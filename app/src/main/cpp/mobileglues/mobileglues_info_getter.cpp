#include "mobileglues_info_getter.h"

static void* mg_handle = nullptr;

static void* try_load_symbol(void* handle, const char* name) {
    void* s = dlsym(handle, name);
    if (!s) {
        fprintf(stderr, "Error: dlsym %s failed: %s", name, dlerror());
    }
    return s;
}

static bool load_mobile_symbols() {
    if (mg_handle) return true;
    const char* mg_directory = getenv("MG_DIR_PATH");
    std::string library_path = mg_directory && *mg_directory
        ? std::string(mg_directory) + "/libmobileglues.so"
        : "libmobileglues.so";
    mg_handle = dlopen(library_path.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!mg_handle) {
        fprintf(stderr, "Error: dlopen %s failed: %s", library_path.c_str(), dlerror());
        return false;
    }

    p_eglGetDisplay = (PFN_eglGetDisplay) try_load_symbol(mg_handle, "eglGetDisplay");
    p_eglInitialize = (PFN_eglInitialize) try_load_symbol(mg_handle, "eglInitialize");
    p_eglChooseConfig = (PFN_eglChooseConfig) try_load_symbol(mg_handle, "eglChooseConfig");
    p_eglCreatePbufferSurface = (PFN_eglCreatePbufferSurface) try_load_symbol(mg_handle, "eglCreatePbufferSurface");
    p_eglCreateContext = (PFN_eglCreateContext) try_load_symbol(mg_handle, "eglCreateContext");
    p_eglMakeCurrent = (PFN_eglMakeCurrent) try_load_symbol(mg_handle, "eglMakeCurrent");
    p_eglDestroySurface = (PFN_eglDestroySurface) try_load_symbol(mg_handle, "eglDestroySurface");
    p_eglDestroyContext = (PFN_eglDestroyContext) try_load_symbol(mg_handle, "eglDestroyContext");
    p_eglTerminate = (PFN_eglTerminate) try_load_symbol(mg_handle, "eglTerminate");

    p_glGetIntegerv = (PFN_glGetIntegerv) try_load_symbol(mg_handle, "glGetIntegerv");
    p_glGetString = (PFN_glGetString) try_load_symbol(mg_handle, "glGetString");
    p_glGetStringi = (PFN_glGetStringi) try_load_symbol(mg_handle, "glGetStringi");

    if (!p_glGetStringi) {
        typedef void* (*PFN_eglGetProcAddress)(const char*);
        auto p_eglGetProcAddress = (PFN_eglGetProcAddress) try_load_symbol(mg_handle, "eglGetProcAddress");
        if (p_eglGetProcAddress) {
            p_glGetStringi = (PFN_glGetStringi) p_eglGetProcAddress("glGetStringi");
        }
    }

    if (!p_eglGetDisplay || !p_eglInitialize || !p_eglChooseConfig || !p_eglCreatePbufferSurface || !p_eglCreateContext || !p_eglMakeCurrent || !p_glGetString || !p_glGetIntegerv) {
        fprintf(stderr, "Error: some required symbols are missing");
        return false;
    }

    return true;
}

static std::string create_context_and_query() {
    if (!load_mobile_symbols()) return "Error: failed to load libmobileglues symbols";

    EGLDisplay display = p_eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) return "Error: eglGetDisplay returned EGL_NO_DISPLAY";

    EGLint major = 0, minor = 0;
    if (!p_eglInitialize(display, &major, &minor)) {
        return "Error: eglInitialize failed";
    }

    EGLint configAttribs[] = {
            EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL_NONE
    };
    EGLConfig config;
    EGLint numConfigs = 0;
    if (!p_eglChooseConfig(display, configAttribs, &config, 1, &numConfigs) || numConfigs <= 0) {
        p_eglTerminate(display);
        return "Error: eglChooseConfig failed";
    }

    EGLint pbufAttribs[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
    EGLSurface surface = p_eglCreatePbufferSurface(display, config, pbufAttribs);
    if (surface == EGL_NO_SURFACE) {
        p_eglTerminate(display);
        return "Error: eglCreatePbufferSurface failed";
    }

    EGLContext context = EGL_NO_CONTEXT;
    EGLint ctxAttribs3[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    EGLint ctxAttribs2[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };

    context = p_eglCreateContext(display, config, EGL_NO_CONTEXT, ctxAttribs3);
    if (context == EGL_NO_CONTEXT) {
        fprintf(stderr, "Error: ES3 context failed, try ES2");
        context = p_eglCreateContext(display, config, EGL_NO_CONTEXT, ctxAttribs2);
    }

    if (context == EGL_NO_CONTEXT) {
        p_eglDestroySurface(display, surface);
        p_eglTerminate(display);
        return "Error: eglCreateContext failed";
    }

    if (!p_eglMakeCurrent(display, surface, surface, context)) {
        p_eglDestroyContext(display, context);
        p_eglDestroySurface(display, surface);
        p_eglTerminate(display);
        return "Error: eglMakeCurrent failed";
    }

    std::ostringstream out;

    std::ostringstream out_exts;

    GLint numExt = 0;
    p_glGetIntegerv(GL_NUM_EXTENSIONS, &numExt);
    if (numExt > 0 && p_glGetStringi) {
        out_exts << "Extensions (" << numExt << "):\n";
        for (GLint i = 0; i < numExt; ++i) {
            const GLubyte* ext = p_glGetStringi(GL_EXTENSIONS, (GLuint)i);
            if (ext) {
                const char* extStr = reinterpret_cast<const char*>(ext);
                if (strcmp(extStr, "GL_MG_mobileglues") == 0) g_MGQueryCapability.HasMobileGluesExt = true;
                if (strcmp(extStr, "GL_MG_backend_string_getter_access") == 0) g_MGQueryCapability.BackendStringGetterAccess = true;
                if (strcmp(extStr, "GL_MG_settings_string_dump") == 0) g_MGQueryCapability.SettingsStringDump = true;
                out_exts << "  " << reinterpret_cast<const char*>(ext) << "\n";
            }
        }
    } else {
        const GLubyte* exts = p_glGetString(GL_EXTENSIONS);
        out_exts << "Extensions: " << (exts ? reinterpret_cast<const char*>(exts) : "NULL") << "\n";
        if (exts) {
            const char* extStr = reinterpret_cast<const char*>(exts);
            if (strstr(extStr, " GL_MG_mobileglues ")) g_MGQueryCapability.HasMobileGluesExt = true;
            if (strstr(extStr, " GL_MG_backend_string_getter_access ")) g_MGQueryCapability.BackendStringGetterAccess = true;
            if (strstr(extStr, " GL_MG_settings_string_dump ")) g_MGQueryCapability.SettingsStringDump = true;
        }
    }

    out << "Is MobileGlues (>=1.3.3): " << (g_MGQueryCapability.HasMobileGluesExt ? "Yes\n" : "No\n");

    // Which driver actually answered. The loader is the only honest source: the
    // renderer string cannot be trusted (a system driver may itself be ANGLE),
    // and the caller's own "did I pass a directory" is an intention, not a fact.
    // Old renderers lack the symbol; the line is simply absent then, and the
    // Kotlin side treats that as "unknown".
    typedef int (*PFN_mg_angle_in_use)();
    auto p_angle_in_use = (PFN_mg_angle_in_use) dlsym(mg_handle, "mg_angle_in_use");
    if (p_angle_in_use) {
        out << "ANGLE in use: " << (p_angle_in_use() ? "yes" : "no") << "\n";
    }

    const GLubyte* renderer = p_glGetString(GL_RENDERER);
    const GLubyte* version = p_glGetString(GL_VERSION);
    const GLubyte* vendor = p_glGetString(GL_VENDOR);
    const GLubyte* shading = p_glGetString(GL_SHADING_LANGUAGE_VERSION);

    out << "\nOpenGL Frontend\n====\n";
    out << "Renderer: " << (renderer ? reinterpret_cast<const char*>(renderer) : "NULL") << "\n";
    out << "Version: " << (version ? reinterpret_cast<const char*>(version) : "NULL") << "\n";
    out << "Vendor: " << (vendor ? reinterpret_cast<const char*>(vendor) : "NULL") << "\n";
    out << "Shading language version: " << (shading ? reinterpret_cast<const char*>(shading) : "NULL") << "\n";
    out << out_exts.str();

    out << "\nOpenGL ES Backend\n====\n";
    if (g_MGQueryCapability.BackendStringGetterAccess) {
        const GLubyte* backendRenderer = p_glGetString(GL_RENDERER + GL_BACKEND_GETTER_MG);
        const GLubyte* backendVersion = p_glGetString(GL_VERSION + GL_BACKEND_GETTER_MG);
        const GLubyte* backendVendor = p_glGetString(GL_VENDOR + GL_BACKEND_GETTER_MG);
        const GLubyte* backendShading = p_glGetString(GL_SHADING_LANGUAGE_VERSION + GL_BACKEND_GETTER_MG);
        out << "Renderer: " << (backendRenderer ? reinterpret_cast<const char*>(backendRenderer) : "NULL") << "\n";
        out << "Version: " << (backendVersion ? reinterpret_cast<const char*>(backendVersion) : "NULL") << "\n";
        out << "Vendor: " << (backendVendor ? reinterpret_cast<const char*>(backendVendor) : "NULL") << "\n";
        out << "Shading language version: " << (backendShading ? reinterpret_cast<const char*>(backendShading) : "NULL") << "\n";

        GLint backendNumExt = 0;
        p_glGetIntegerv(GL_NUM_EXTENSIONS + GL_BACKEND_GETTER_MG, &backendNumExt);
        if (backendNumExt > 0 && p_glGetStringi) {
            out << "Extensions (" << backendNumExt << "):\n";
            for (GLint i = 0; i < backendNumExt; ++i) {
                const GLubyte* ext = p_glGetStringi(GL_EXTENSIONS + GL_BACKEND_GETTER_MG, (GLuint)i);
                out << "  " << reinterpret_cast<const char*>(ext) << "\n";
            }
        } else {
            const GLubyte* exts = p_glGetString(GL_EXTENSIONS + GL_BACKEND_GETTER_MG);
            out << "Extensions: " << (exts ? reinterpret_cast<const char*>(exts) : "NULL") << "\n";
        }
    } else {
        out << "Not Compatible. GL_MG_backend_string_getter_access is not supported in current context.\n";
    }

    out << "\nSettings\n====\n";
    if (g_MGQueryCapability.SettingsStringDump) {
        const GLubyte* settingsStr = p_glGetString(GL_SETTINGS_MG);
        out << reinterpret_cast<const char*>(settingsStr) << "\n";
    } else {
        out << "Not Compatible. GL_MG_settings_string_dump is not supported in current context.\n";
    }

    p_eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    p_eglDestroySurface(display, surface);
    p_eglDestroyContext(display, context);
    p_eglTerminate(display);

    dlclose(mg_handle);
    mg_handle = nullptr;

    return out.str();
}

extern "C"
JNIEXPORT jstring JNICALL
Java_net_kdt_pojavlaunch_firefly_mobileglues_MGInfoGetter_getMobileGluesGLInfo(JNIEnv *env,
                                                                          jobject thiz) {
    std::string res = create_context_and_query();
    printf("MobileGlues GL Info: \n%s", res.c_str());
    return env->NewStringUTF(res.c_str());
}

// Runs the MultiDraw micro benchmark that lives inside libmobileglues
// (gl/multidraw_bench.cpp) and returns its JSON verbatim. Same shape as the GL
// info query: dlopen the renderer, make a context current through its own EGL
// layer, call one symbol, tear everything down.
// Published while the benchmark runs so the UI thread can poll how far along it
// is. The pointer belongs to a dlopen'd library, so it is only valid between
// the dlsym below and the dlclose at the end of the run -- hence the lock.
typedef int (*PFN_mg_multidraw_bench_progress)();
static std::mutex g_bench_progress_mutex;
static PFN_mg_multidraw_bench_progress p_bench_progress = nullptr;

static void set_bench_progress_fn(PFN_mg_multidraw_bench_progress fn) {
    std::lock_guard<std::mutex> lock(g_bench_progress_mutex);
    p_bench_progress = fn;
}

static std::string create_context_and_bench(int start_sections, int max_sections) {
    if (!load_mobile_symbols()) return R"({"error":"failed to load libmobileglues symbols"})";

    typedef const char* (*PFN_mg_multidraw_bench_run)(int, int);
    auto p_bench = (PFN_mg_multidraw_bench_run) dlsym(mg_handle, "mg_multidraw_bench_run");
    if (!p_bench) {
        return R"({"error":"mg_multidraw_bench_run is missing; the renderer is too old"})";
    }
    // Optional: an older renderer has the benchmark but not the progress
    // counter, and the caller falls back to an indeterminate spinner.
    auto p_progress = (PFN_mg_multidraw_bench_progress) dlsym(mg_handle, "mg_multidraw_bench_progress");

    EGLDisplay display = p_eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) return R"({"error":"eglGetDisplay returned EGL_NO_DISPLAY"})";

    EGLint major = 0, minor = 0;
    if (!p_eglInitialize(display, &major, &minor)) {
        return R"({"error":"eglInitialize failed"})";
    }

    EGLint configAttribs[] = {
            EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL_NONE
    };
    EGLConfig config;
    EGLint numConfigs = 0;
    if (!p_eglChooseConfig(display, configAttribs, &config, 1, &numConfigs) || numConfigs <= 0) {
        p_eglTerminate(display);
        return R"({"error":"eglChooseConfig failed"})";
    }

    EGLint pbufAttribs[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
    EGLSurface surface = p_eglCreatePbufferSurface(display, config, pbufAttribs);
    if (surface == EGL_NO_SURFACE) {
        p_eglTerminate(display);
        return R"({"error":"eglCreatePbufferSurface failed"})";
    }

    EGLint ctxAttribs3[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    EGLContext context = p_eglCreateContext(display, config, EGL_NO_CONTEXT, ctxAttribs3);
    if (context == EGL_NO_CONTEXT) {
        p_eglDestroySurface(display, surface);
        p_eglTerminate(display);
        return R"({"error":"eglCreateContext failed"})";
    }

    if (!p_eglMakeCurrent(display, surface, surface, context)) {
        p_eglDestroyContext(display, context);
        p_eglDestroySurface(display, surface);
        p_eglTerminate(display);
        return R"({"error":"eglMakeCurrent failed"})";
    }

    set_bench_progress_fn(p_progress);
    const char* raw = p_bench(start_sections, max_sections);
    // Copy before teardown: the string lives inside libmobileglues.
    std::string res = raw ? raw : R"({"error":"benchmark returned nothing"})";
    set_bench_progress_fn(nullptr);

    p_eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    p_eglDestroySurface(display, surface);
    p_eglDestroyContext(display, context);
    p_eglTerminate(display);

    dlclose(mg_handle);
    mg_handle = nullptr;

    return res;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_net_kdt_pojavlaunch_firefly_mobileglues_MGBench_runMultidrawBench(JNIEnv *env, jobject thiz,
                                                          jint startSections, jint maxSections) {
    std::string res = create_context_and_bench(startSections, maxSections);
    printf("MobileGlues MultiDraw bench: \n%s", res.c_str());
    __android_log_print(ANDROID_LOG_INFO, "MGBench", "%s", res.c_str());
    return env->NewStringUTF(res.c_str());
}

// 0..1000 while a benchmark is running, -1 when none is (or when the renderer
// predates the progress counter). Called from a different thread than the run.
extern "C"
JNIEXPORT jint JNICALL
Java_net_kdt_pojavlaunch_firefly_mobileglues_MGBench_benchProgress(JNIEnv *env, jobject thiz) {
    (void)env;
    (void)thiz;
    std::lock_guard<std::mutex> lock(g_bench_progress_mutex);
    return p_bench_progress ? p_bench_progress() : -1;
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)vm;
    (void)reserved;
    printf("JNI_OnLoad called");
    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT jint JNICALL
Java_net_kdt_pojavlaunch_firefly_mobileglues_MGInfoGetter_setenv(JNIEnv *env, jobject thiz, jstring key,
                                                             jstring value, jint overwrite) {
    const char* k = env->GetStringUTFChars(key, nullptr);
    const char* v = env->GetStringUTFChars(value, nullptr);
    int ret = setenv(k, v, overwrite);
    env->ReleaseStringUTFChars(key, k);
    env->ReleaseStringUTFChars(value, v);
    return ret;
}
