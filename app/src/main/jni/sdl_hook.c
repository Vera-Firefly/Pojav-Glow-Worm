// Reference AAMC: https://github.com/AngelAuraMC/Amethyst-Android/blob/360d708262ff703d9b52782d20cd348410a33df5/app_pojavlauncher/src/main/jni/native_hooks/sdl_hook.c

#include <stdbool.h>
#include <stdint.h>

#include "environ/environ.h"
#include "pojav/utils.h"
#include "pojav/log.h"

#include <bytehook.h>
#include <dlfcn.h>
#include <jni.h>
#include <stdlib.h>
#include <time.h>
#include <string.h>

// Map launcher log macros
#define LOG_TO_E(...) LOGE(__VA_ARGS__)
#define LOG_TO_W(...) LOGW(__VA_ARGS__)
#define LOG_TO_I(...) LOGI(__VA_ARGS__)
#define LOG_TO_D(...) LOGD(__VA_ARGS__)

// --- Minimal SDL3 declarations needed by the hooks ---
typedef uint32_t SDL_InitFlags;
typedef struct SDL_Window SDL_Window;
typedef struct SDL_Rect { int x, y, w, h; } SDL_Rect;

bool SDL_InitSubSystem(SDL_InitFlags flags);
bool SDL_SetHint(const char *name, const char *value);
bool SDL_SetTextInputArea(SDL_Window *window, const SDL_Rect *rect, int cursor);
bool SDL_SetWindowRelativeMouseMode(SDL_Window *window, bool enabled);
bool SDL_SetWindowMouseGrab(SDL_Window *window, bool grabbed);
void SDL_SetError(const char *fmt, ...);
const char *SDL_GetError(void);
SDL_Window *SDL_GetWindowFromEvent(const void *event);
SDL_Window *SDL_GetWindowFromID(uint32_t id);
bool SDL_GL_SetAttribute(int attr, int value);
void *SDL_LoadObject(const char *path);
void SDL_UnloadObject(void *handle);
void *SDL_LoadFunction(void *handle, const char *name);
SDL_Window *SDL_CreateWindow(const char *title, int w, int h, uint32_t flags);
SDL_Window *SDL_CreateWindowWithProperties(uint32_t props);
void SDL_DestroyWindow(SDL_Window *window);
void *SDL_EGL_GetProcAddress(const char *proc);

// Provided by egl_bridge.c (libpojavexec.so)

DECL_DLSYM(SDL_InitSubSystem)
DECL_DLSYM(SDL_SetHint);
DECL_DLSYM(SDL_SetTextInputArea);
DECL_DLSYM(SDL_SetWindowRelativeMouseMode);
DECL_DLSYM(SDL_SetWindowMouseGrab);
DECL_DLSYM(SDL_SetError);
DECL_DLSYM(SDL_GetError);
DECL_DLSYM(SDL_GetWindowFromEvent)
DECL_DLSYM(SDL_GetWindowFromID)
DECL_DLSYM(SDL_GL_SetAttribute)
DECL_DLSYM(SDL_LoadObject)
DECL_DLSYM(SDL_UnloadObject)
DECL_DLSYM(SDL_LoadFunction)
DECL_DLSYM(SDL_CreateWindow)
DECL_DLSYM(SDL_CreateWindowWithProperties)
DECL_DLSYM(SDL_DestroyWindow)
DECL_DLSYM(SDL_EGL_GetProcAddress)

typedef void *EGLDisplay;
typedef void *EGLConfig;
typedef int EGLint;
typedef int EGLBoolean;
typedef EGLBoolean (*eglChooseConfig_t)(EGLDisplay dpy, const EGLint *attrib_list, EGLConfig *configs,
                                        EGLint config_size, EGLint *num_config);
typedef void *(*eglCreateContext_t)(EGLDisplay dpy, EGLConfig config, void *share, const EGLint *attrib_list);
typedef EGLBoolean (*eglSwapBuffers_t)(EGLDisplay dpy, void *surface);

// EGL constants (EGL/egl.h)
#define EGL_NONE              0x3038
#define EGL_RENDERABLE_TYPE   0x3040
#define EGL_OPENGL_BIT        0x0008
#define EGL_OPENGL_ES2_BIT    0x0004
#define EGL_OPENGL_ES3_BIT    0x0040
#define EGL_CONTEXT_CLIENT_VERSION    0x3098
#define EGL_CONTEXT_MAJOR_VERSION_KHR 0x30FB
#define EGL_CONTEXT_MINOR_VERSION_KHR 0x30FC



// --- Event window resolution fallback ---

static SDL_Window *sdlLastEventWindow = NULL;

static SDL_Window *custom_SDL_GetWindowFromEvent_Func(const void *event) {
    SDL_Window *window = BYTEHOOK_CALL_PREV(custom_SDL_GetWindowFromEvent_Func, SDL_GetWindowFromEvent_t, event);
    if (window != NULL) {
        sdlLastEventWindow = window;
    } else if (sdlLastEventWindow != NULL) {
        window = sdlLastEventWindow;
    }
    BYTEHOOK_POP_STACK();
    return window;
}

static SDL_Window *custom_SDL_GetWindowFromID_Func(uint32_t id) {
    SDL_Window *window = BYTEHOOK_CALL_PREV(custom_SDL_GetWindowFromID_Func, SDL_GetWindowFromID_t, id);
    if (window != NULL) {
        sdlLastEventWindow = window;
    } else if (sdlLastEventWindow != NULL) {
        window = sdlLastEventWindow;
    }
    BYTEHOOK_POP_STACK();
    return window;
}

// --- EGL attribute normalization for GLES hosts ---
static EGLBoolean normalizeEglChooseConfigList(const EGLint *attrib_list, EGLint *fixed, int cap) {
    if (attrib_list == NULL) return 0;
    int n = 0;
    for (int i = 0; n < cap - 2; i += 2) {
        EGLint attr = attrib_list[i];
        EGLint val = attrib_list[i + 1];
        if (attr == EGL_NONE) {
            fixed[n] = EGL_NONE;
            fixed[n + 1] = 0;
            n += 2;
            break;
        }
        if (attr == EGL_RENDERABLE_TYPE) {
            if ((val & (EGL_OPENGL_ES3_BIT | EGL_OPENGL_BIT)) != 0 && (val & EGL_OPENGL_ES2_BIT) == 0) {
                val = (val & ~(EGL_OPENGL_ES3_BIT | EGL_OPENGL_BIT)) | EGL_OPENGL_ES2_BIT;
            }
        }
        fixed[n] = attr;
        fixed[n + 1] = val;
        n += 2;
    }
    return n > 0;
}

// Strip KHR version attributes into a fallback attribute table; returns the requested major version
static int normalizeEglContextAttribs(const EGLint *attrib_list, EGLint *fixed, int cap, bool esSemantics) {
    int version = 0;
    bool hasClientVersion = false;
    if (attrib_list == NULL) return 0;
    int n = 0;
    for (int i = 0; n < cap - 2; i += 2) {
        EGLint attr = attrib_list[i];
        EGLint val = attrib_list[i + 1];
        if (attr == EGL_NONE) break;
        if (attr == EGL_CONTEXT_MAJOR_VERSION_KHR) {
            if (version == 0) version = val;
            continue;
        }
        if (attr == EGL_CONTEXT_MINOR_VERSION_KHR) continue;
        if (attr == EGL_CONTEXT_CLIENT_VERSION) {
            hasClientVersion = true;
            if (version == 0) version = val;
        }
        if (n >= cap - 2) return 0;
        fixed[n++] = attr;
        fixed[n++] = val;
    }
    // Add CLIENT_VERSION for ES semantics when absent
    if (esSemantics && version > 0 && !hasClientVersion) {
        if (n >= cap - 2) return 0;
        fixed[n++] = EGL_CONTEXT_CLIENT_VERSION;
        fixed[n++] = version;
    }
    if (n >= cap - 2) return 0;
    fixed[n++] = EGL_NONE;
    fixed[n++] = 0;
    return version;
}

static bool isMobileGluesEgl(void) {
    const char *egl = getenv("POJAVEXEC_EGL");
    if (egl == NULL) return false;
    const char *base = strrchr(egl, '/');
    return strcmp(base != NULL ? base + 1 : egl, "libmobileglues.so") == 0;
}

// GLES compat layer: applies to mobile ES renderers only
static bool sdlGlesCompatEnabled(void) {
    const char *renderer = getenv("POJAV_RENDERER");
    if (renderer == NULL) return isMobileGluesEgl();
    if (strstr(renderer, "desktopgl") != NULL) return false;
    if (strncmp(renderer, "gallium_", 8) == 0) return false; // OSMesa
    if (strcmp(renderer, "custom_gallium") == 0 || strcmp(renderer, "vulkan_zink") == 0) return false;
    if (strncmp(renderer, "opengles", 8) == 0) return true; // built-in GL4ES/NGGL4ES
    return isMobileGluesEgl(); // MobileGlues
}

static bool sForcedEsProfile = false;

static bool shouldReusePrimaryWindow(void) {
    const char *value = getenv("POJAV_SDL_REUSE_WINDOW");
    if (value != NULL) return strcmp(value, "1") == 0;
    return true;
}

// --- SDL EGL function resolution takeover ---
static eglChooseConfig_t sOrigEglChooseConfig = NULL;
static eglCreateContext_t sOrigEglCreateContext = NULL;
static eglSwapBuffers_t sOrigEglSwapBuffers = NULL;

static void *proxyEglCreateContext(EGLDisplay dpy, EGLConfig config, void *share, const EGLint *attrib_list) {
    if (sOrigEglCreateContext == NULL) {
        LOG_TO_E("SDL_Hook: eglCreateContext was not resolved");
        return NULL;
    }

    void *ctx = sOrigEglCreateContext(dpy, config, share, attrib_list);
    if (ctx != NULL || !sdlGlesCompatEnabled()) return ctx;

    bool esSemantics = sForcedEsProfile;
    EGLint fixed[64];
    int version = normalizeEglContextAttribs(attrib_list, fixed, 64, esSemantics);
    if (version == 0) return ctx;

    LOG_TO_W("SDL_Hook: retrying eglCreateContext without KHR version attrs (CV=%d)", version);
    ctx = sOrigEglCreateContext(dpy, config, share, fixed);
    if (ctx != NULL || !esSemantics || version <= 2) return ctx; // CV=2 is the last mobile fallback

    LOG_TO_W("SDL_Hook: retrying eglCreateContext with CV=2 after CV=%d failed", version);
    EGLint es2[3] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    return sOrigEglCreateContext(dpy, config, share, es2);
}

static EGLBoolean proxyEglChooseConfig(EGLDisplay dpy, const EGLint *attrib_list, EGLConfig *configs,
                                       EGLint config_size, EGLint *num_config) {
    if (sOrigEglChooseConfig == NULL) {
        LOG_TO_E("SDL_Hook: eglChooseConfig was not resolved");
        return 0;
    }

    EGLBoolean result = sOrigEglChooseConfig(dpy, attrib_list, configs, config_size, num_config);
    if (result && num_config != NULL && *num_config > 0) return result;
    if (!sdlGlesCompatEnabled()) return result;

    EGLint fixed[64];
    if (!normalizeEglChooseConfigList(attrib_list, fixed, 64)) return result;
    EGLint fallbackCount = 0;
    EGLBoolean fallbackResult = sOrigEglChooseConfig(dpy, fixed, configs, config_size, &fallbackCount);
    if (fallbackResult && num_config != NULL) *num_config = fallbackCount;
    LOG_TO_W("SDL_Hook: eglChooseConfig compatibility fallback result=%d count=%d", fallbackResult, fallbackCount);
    return fallbackResult;
}

// Frame counter bridging into the launcher FPS statistics
void pojav_record_frame_for_sdl(void);
void calculateFPS(void) {
    pojav_record_frame_for_sdl();
}

static EGLBoolean proxyEglSwapBuffers(EGLDisplay dpy, void *surface) {
    if (sOrigEglSwapBuffers == NULL) {
        LOG_TO_E("SDL_Hook: eglSwapBuffers was not resolved");
        return 0;
    }
    calculateFPS();
    return sOrigEglSwapBuffers(dpy, surface);
}

// Inject proxies when SDL resolves EGL functions via SDL_LoadFunction
static void *custom_SDL_LoadFunction_Func(void *handle, const char *name) {
    void *r = BYTEHOOK_CALL_PREV(custom_SDL_LoadFunction_Func, SDL_LoadFunction_t, handle, name);
    BYTEHOOK_POP_STACK();
    if (name != NULL) {
        if (strcmp(name, "eglChooseConfig") == 0) {
            if (r != NULL && sOrigEglChooseConfig == NULL) sOrigEglChooseConfig = (eglChooseConfig_t) r;
            if (sOrigEglChooseConfig != NULL && r != (void *) proxyEglChooseConfig) r = (void *) proxyEglChooseConfig;
        } else if (strcmp(name, "eglCreateContext") == 0) {
            if (r != NULL && sOrigEglCreateContext == NULL) sOrigEglCreateContext = (eglCreateContext_t) r;
            if (sOrigEglCreateContext != NULL && r != (void *) proxyEglCreateContext) r = (void *) proxyEglCreateContext;
        } else if (strcmp(name, "eglSwapBuffers") == 0) {
            if (r != NULL && sOrigEglSwapBuffers == NULL) sOrigEglSwapBuffers = (eglSwapBuffers_t) r;
            if (sOrigEglSwapBuffers != NULL && r != (void *) proxyEglSwapBuffers) r = (void *) proxyEglSwapBuffers;
        }
    }
    return r;
}

// Same proxies for the public SDL_EGL_GetProcAddress entry point
static void *custom_SDL_EGLGetProcAddress_Func(const char *proc) {
    void *r = BYTEHOOK_CALL_PREV(custom_SDL_EGLGetProcAddress_Func, SDL_EGL_GetProcAddress_t, proc);
    BYTEHOOK_POP_STACK();
    if (proc == NULL || r == NULL) return r;
    if (strcmp(proc, "eglChooseConfig") == 0) {
        if (sOrigEglChooseConfig == NULL) sOrigEglChooseConfig = (eglChooseConfig_t) r;
        if (r != (void *) proxyEglChooseConfig) r = (void *) proxyEglChooseConfig;
    } else if (strcmp(proc, "eglCreateContext") == 0) {
        if (sOrigEglCreateContext == NULL) sOrigEglCreateContext = (eglCreateContext_t) r;
        if (r != (void *) proxyEglCreateContext) r = (void *) proxyEglCreateContext;
    } else if (strcmp(proc, "eglSwapBuffers") == 0) {
        if (sOrigEglSwapBuffers == NULL) sOrigEglSwapBuffers = (eglSwapBuffers_t) r;
        if (r != (void *) proxyEglSwapBuffers) r = (void *) proxyEglSwapBuffers;
    }
    return r;
}

// --- Vulkan loader consistency: reuse the launcher-held loader handle ---
static void *custom_SDL_LoadObject_Func(const char *path) {
    if (path != NULL && strstr(path, "libvulkan") != NULL) {
        const char *vkptr = getenv("VULKAN_PTR");
        if (vkptr != NULL && vkptr[0] != '\0') {
            void *handle = (void *) (uintptr_t) strtoull(vkptr, NULL, 16);
            if (handle != NULL) return handle;
        }
    }
    void *r = BYTEHOOK_CALL_PREV(custom_SDL_LoadObject_Func, SDL_LoadObject_t, path);
    BYTEHOOK_POP_STACK();
    return r;
}

static void custom_SDL_UnloadObject_Func(void *handle) {
    const char *vkptr = getenv("VULKAN_PTR");
    if (vkptr != NULL && vkptr[0] != '\0') {
        void *vulkan_handle = (void *) (uintptr_t) strtoull(vkptr, NULL, 16);
        if (handle == vulkan_handle) return;
    }
    BYTEHOOK_CALL_PREV(custom_SDL_UnloadObject_Func, SDL_UnloadObject_t, handle);
    BYTEHOOK_POP_STACK();
}

// First successfully created SDL window; later creations are redirected to it
static SDL_Window *sPrimaryWindow = NULL;
static unsigned int sPrimaryWindowRefs = 0;

static void custom_SDL_DestroyWindow_Func(SDL_Window *window) {
    if (window == sPrimaryWindow && sPrimaryWindowRefs > 0) {
        sPrimaryWindowRefs--;
        LOG_TO_I("SDL_Hook: releasing logical window %p, refs=%u", window, sPrimaryWindowRefs);
        if (sPrimaryWindowRefs > 0) {
            if (window == sdlLastEventWindow) sdlLastEventWindow = NULL;
            return;
        }
        sPrimaryWindow = NULL;
        if (window == sdlLastEventWindow) sdlLastEventWindow = NULL;
    } else if (window == sdlLastEventWindow) {
        sdlLastEventWindow = NULL;
    }
    BYTEHOOK_CALL_PREV(custom_SDL_DestroyWindow_Func, SDL_DestroyWindow_t, window);
    BYTEHOOK_POP_STACK();
}

static bool custom_SDL_InitSubSystem_Func(SDL_InitFlags flags) {
    // Set up the JNI integration on the launcher side via notifyLauncher
    TRY_ATTACH_ENV(dvm_env, pojav_environ->dalvikJavaVMPtr, "SDL_InitSubSystem failed!",
            SET_DLSYM_PTR(dlopen("libSDL3.so", RTLD_NOLOAD), SDL_SetError);
            if (SDL_SetError_p) SDL_SetError_p("Failed to load SDL launcher integration android-side. This is not an SDL bug, please contact the launcher developer.");
            return false;
            );

        jint safeFlags;
    if (flags > INT32_MAX) {
        safeFlags = -1;
    } else safeFlags = (jint)flags;

    notifyLauncher(dvm_env, NOTIF_TYPE_SDL, (int[]){ACTION_INIT_LAUNCHER_INTEGRATION, safeFlags}, 2);

    // This is the normal for the launcher, the default in SDL is false.
    SET_DLSYM_PTR(dlopen("libSDL3.so", RTLD_NOLOAD), SDL_SetHint);
    if (SDL_SetHint_p) SDL_SetHint_p("SDL_RETURN_KEY_HIDES_IME", "true");
    // FIXME: MobileGlues has issues with passing in the proper EGL params
    if (isMobileGluesEgl()) {
        SDL_SetHint_p("SDL_OPENGL_FORCE_SRGB_FRAMEBUFFER", "0");
    }
    // MC disables the platform screen keyboard by default; re-enable it for mobile IME
    if (SDL_SetHint_p) SDL_SetHint_p("SDL_ENABLE_SCREEN_KEYBOARD", "1");

    // Call original func after doing all the needed setup
    bool r = BYTEHOOK_CALL_PREV(custom_SDL_InitSubSystem_Func, SDL_InitSubSystem_t, flags);
    if (!r){
        SET_DLSYM_PTR(dlopen("libSDL3.so", RTLD_NOLOAD), SDL_GetError);
        LOG_TO_E("SDL_Hook: SDL_InitSubsystem Error: %s", SDL_GetError_p());
    }
    BYTEHOOK_POP_STACK();
    return r;
}

// Force the GL profile to ES before window creation (mobile hosts reject desktop profiles)
static void forceEglProfileEs(void) {
    if (!sdlGlesCompatEnabled()) return;
    SET_DLSYM_PTR(dlopen("libSDL3.so", RTLD_NOLOAD), SDL_GL_SetAttribute);
    if (SDL_GL_SetAttribute_p) {
        SDL_GL_SetAttribute_p(20 /* SDL_GL_CONTEXT_PROFILE_MASK */, 4 /* SDL_GL_CONTEXT_PROFILE_ES */);
        sForcedEsProfile = true;
    }
}

// --- Primary window reuse (the Android backend allows one window per process) ---
// Size follows the Android Surface; orientation follows the SDL_ORIENTATIONS hint.
static SDL_Window *reusePrimaryWindow(void) {
    LOG_TO_I("SDL_Hook: reusing primary window %p", sPrimaryWindow);
    return sPrimaryWindow;
}

static SDL_Window *custom_SDL_CreateWindow_Func(const char *title, int w, int h, uint32_t flags) {
    forceEglProfileEs();
    const bool reuse = shouldReusePrimaryWindow();
    if (reuse && sPrimaryWindow != NULL) {
        sPrimaryWindowRefs++;
        SDL_Window *wnd = reusePrimaryWindow();
        return wnd;
    }
    SDL_Window *wnd = BYTEHOOK_CALL_PREV(custom_SDL_CreateWindow_Func, SDL_CreateWindow_t, title, w, h, flags);
    if (reuse && wnd != NULL) {
        sPrimaryWindow = wnd;
        sPrimaryWindowRefs = 1;
    }
    BYTEHOOK_POP_STACK();
    return wnd;
}

static SDL_Window *custom_SDL_CreateWindowWithProperties_Func(uint32_t props) {
    forceEglProfileEs();
    const bool reuse = shouldReusePrimaryWindow();
    if (reuse && sPrimaryWindow != NULL) {
        sPrimaryWindowRefs++;
        SDL_Window *wnd = reusePrimaryWindow();
        return wnd;
    }
    SDL_Window *wnd = BYTEHOOK_CALL_PREV(custom_SDL_CreateWindowWithProperties_Func, SDL_CreateWindowWithProperties_t, props);
    if (reuse && wnd != NULL) {
        sPrimaryWindow = wnd;
        sPrimaryWindowRefs = 1;
    }
    BYTEHOOK_POP_STACK();
    return wnd;
}

static bool custom_SDL_SetWindowRelativeMouseMode_Func(SDL_Window *window, bool enabled) {
    LOG_TO_I("SDL_Hook: MC requested RELATIVE mouse mode = %s", enabled ? "true" : "false");
    BYTEHOOK_CALL_PREV(custom_SDL_SetWindowRelativeMouseMode_Func, SDL_SetWindowRelativeMouseMode_t, window, enabled);
    BYTEHOOK_POP_STACK();
    return enabled;
}

static bool custom_SDL_SetWindowMouseGrab_Func(SDL_Window *window, bool grabbed) {
    LOG_TO_I("SDL_Hook: MC requested WINDOW MOUSE GRAB = %s", grabbed ? "true" : "false");
    BYTEHOOK_CALL_PREV(custom_SDL_SetWindowMouseGrab_Func, SDL_SetWindowMouseGrab_t, window, grabbed);
    BYTEHOOK_POP_STACK();
    return grabbed;
}

void create_sdl_hooks(bytehook_stub_t (*bytehook_hook_all_p)(const char *callee_path_name, const char *sym_name, void *new_func,
                                                             bytehook_hooked_t hooked, void *hooked_arg)) {
    // Don't set callee_path_name to anything besides NULL or else it won't be able to find the symbol
    bytehook_stub_t stub_SDL_InitSubSystem = bytehook_hook_all_p(NULL, "SDL_InitSubSystem", &custom_SDL_InitSubSystem_Func, NULL, NULL);
    bytehook_stub_t stub_SDL_GetWindowFromEvent = bytehook_hook_all_p(NULL, "SDL_GetWindowFromEvent", &custom_SDL_GetWindowFromEvent_Func, NULL, NULL);
    bytehook_stub_t stub_SDL_GetWindowFromID = bytehook_hook_all_p(NULL, "SDL_GetWindowFromID", &custom_SDL_GetWindowFromID_Func, NULL, NULL);
    // Force ES profile before window creation (both SDL3 entry points)
    bytehook_stub_t stub_SDL_CreateWindow = bytehook_hook_all_p(NULL, "SDL_CreateWindow", &custom_SDL_CreateWindow_Func, NULL, NULL);
    bytehook_stub_t stub_SDL_CreateWindowWithProperties = bytehook_hook_all_p(NULL, "SDL_CreateWindowWithProperties", &custom_SDL_CreateWindowWithProperties_Func, NULL, NULL);
    // Take over SDL EGL function resolution
    bytehook_stub_t stub_SDL_LoadFunction = bytehook_hook_all_p(NULL, "SDL_LoadFunction", &custom_SDL_LoadFunction_Func, NULL, NULL);
    // Same proxies for the public EGL entry point
    bytehook_stub_t stub_SDL_EGLGetProcAddress = bytehook_hook_all_p(NULL, "SDL_EGL_GetProcAddress", &custom_SDL_EGLGetProcAddress_Func, NULL, NULL);
    // Vulkan loader consistency
    bytehook_stub_t stub_SDL_LoadObject = bytehook_hook_all_p(NULL, "SDL_LoadObject", &custom_SDL_LoadObject_Func, NULL, NULL);
    bytehook_stub_t stub_SDL_UnloadObject = bytehook_hook_all_p(NULL, "SDL_UnloadObject", &custom_SDL_UnloadObject_Func, NULL, NULL);
    // Track primary window destruction for reuse
    bytehook_stub_t stub_SDL_DestroyWindow = bytehook_hook_all_p(NULL, "SDL_DestroyWindow", &custom_SDL_DestroyWindow_Func, NULL, NULL);
    bytehook_stub_t stub_RelMouse = bytehook_hook_all_p(NULL, "SDL_SetWindowRelativeMouseMode", &custom_SDL_SetWindowRelativeMouseMode_Func, NULL, NULL);
    bytehook_stub_t stub_MouseGrab = bytehook_hook_all_p(NULL, "SDL_SetWindowMouseGrab", &custom_SDL_SetWindowMouseGrab_Func, NULL, NULL);
    LOG_TO_I("SDL_Hook: Successfully initialized SDL hooks, stubs: InitSubSystem=%p GetWindowFromEvent=%p GetWindowFromID=%p LoadFunction=%p CreateWindow=%p CreateWindowWithProps=%p LoadObject=%p UnloadObject=%p DestroyWindow=%p", stub_SDL_InitSubSystem, stub_SDL_GetWindowFromEvent, stub_SDL_GetWindowFromID, stub_SDL_LoadFunction, stub_SDL_CreateWindow, stub_SDL_CreateWindowWithProperties, stub_SDL_LoadObject, stub_SDL_UnloadObject, stub_SDL_DestroyWindow);
}
