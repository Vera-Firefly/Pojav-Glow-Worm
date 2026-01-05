//
// Modifiled by Vera-Firefly on 02.08.2024.
//
#include <jni.h>
#include <assert.h>
#include <dlfcn.h>
#include <limits.h>

#include <string.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <unistd.h>

#include <EGL/egl.h>
#include <GL/osmesa.h>
#include "ctxbridges/egl_loader.h"
#include "ctxbridges/osmesa_loader.h"

#ifdef GLES_TEST
#include <GLES2/gl2.h>
#endif

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/rect.h>
#include <string.h>
#include <environ/environ.h>
#include <android/dlext.h>
#include "utils.h"
#include "ctxbridges/gl_bridge.h"
#include "ctxbridges/bridge_tbl.h"
#include "ctxbridges/osm_bridge.h"
#include "ctxbridges/osm_bridge_xxx1.h"
#include "ctxbridges/osm_bridge_xxx2.h"
#include "ctxbridges/osm_bridge_xxx3.h"
#include "ctxbridges/virgl_bridge.h"
#include "driver_helper/nsbypass.h"

#define POTATOBRIDGE
#define INITIAL_FRAME_BUFFER
#include "ctxbridges/renderer_config.h"

#define GLFW_CLIENT_API 0x22001
/* Consider GLFW_NO_API as Vulkan API */
#define GLFW_NO_API 0
#define GLFW_OPENGL_API 0x30001
// region OSMESA internals

// This means that the function is an external API and that it will be used
#define EXTERNAL_API __attribute__((used))
// This means that you are forced to have this function/variable for ABI compatibility
#define ABI_COMPAT __attribute__((unused))

static int currentFps = 0;

void bigcore_set_affinity();

void* loadTurnipVulkan();

EXTERNAL_API void pojavTerminate() {
    printf("EGLBridge: Terminating\n");

    switch (pojav_environ->config_renderer) {
        case RENDERER_GL4ES: {
            eglMakeCurrent_p(potatoBridge.eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            eglDestroySurface_p(potatoBridge.eglDisplay, potatoBridge.eglSurface);
            eglDestroyContext_p(potatoBridge.eglDisplay, potatoBridge.eglContext);
            eglTerminate_p(potatoBridge.eglDisplay);
            eglReleaseThread_p();

            potatoBridge.eglContext = EGL_NO_CONTEXT;
            potatoBridge.eglDisplay = EGL_NO_DISPLAY;
            potatoBridge.eglSurface = EGL_NO_SURFACE;
        } break;
        case RENDERER_VK_ZINK:
        case RENDERER_VK_ZINK_XXX1:
        case RENDERER_VK_ZINK_XXX2:
        case RENDERER_VK_ZINK_XXX3:
            // Nothing to do here
            break;
    }
}

void ConfigBridgeTbl() {
    const char* bridge_tbl = getenv("BRIDGE_CONFIG");
    if (bridge_tbl == NULL)
    {
        pojav_environ->bridge_config = BRIDGE_TBL_DEFAULT;
        return;
    }
    
    struct {
        const char* key;
        int value;
    } bridge_map[] = {
        {"xxx1", BRIDGE_TBL_XXX1},
        {"xxx2", BRIDGE_TBL_XXX2},
        {"xxx3", BRIDGE_TBL_XXX3},
        {"xxx4", BRIDGE_TBL_XXX4},
    };
    
    int hasSelected = 0;
    for (int i = 0; i < sizeof(bridge_map) / sizeof(bridge_map[0]); ++i)
    {
        if (!strcmp(bridge_tbl, bridge_map[i].key))
        {
            pojav_environ->bridge_config = bridge_map[i].value;
            hasSelected = 1;
            break;
        }
    }
    if (hasSelected == 0)
    {
        printf("Config Bridge: Config not found, using default config\n");
        pojav_environ->bridge_config = BRIDGE_TBL_DEFAULT;
    }
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_firefly_utils_JREUtils_initRendererTag(JNIEnv *env, jclass clazz, jstring tag) {
    const char *RTag = (*env)->GetStringUTFChars(env, tag, 0);
    pojav_environ->rendererTag = strdup(RTag);
    printf("Renderer Tag: %s\n", RTag);
    (*env)->ReleaseStringUTFChars(env, tag, RTag);
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_firefly_utils_JREUtils_setupBridgeWindow(JNIEnv* env, ABI_COMPAT jclass clazz, jobject surface) {
    pojav_environ->pojavWindow = ANativeWindow_fromSurface(env, surface);

    if (pojav_environ->bridge_config != 0 && pojav_environ->config_renderer == RENDERER_GL4ES)
        gl_setup_window();

    if (br_setup_window) br_setup_window();

}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_firefly_utils_JREUtils_releaseBridgeWindow(ABI_COMPAT JNIEnv *env, ABI_COMPAT jclass clazz) {
    ANativeWindow_release(pojav_environ->pojavWindow);
}

/*If you don't want your renderer for
the Mesa class to crash in your launcher
don't touch the code here
*/
EXTERNAL_API void* pojavGetCurrentContext() {

    if (pojav_environ->bridge_config != 0 && pojav_environ->config_renderer == RENDERER_GL4ES)
        return (void *)eglGetCurrentContext_p();

    if (pojav_environ->config_renderer == RENDERER_VIRGL)
        return virglGetCurrentContext();

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX2)
        return xxx2OsmGetCurrentContext();

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX3)
        return xxx3OsmGetCurrentContext();

    return br_get_current();
}

static void set_vulkan_ptr(void* ptr) {
    char envval[64];
    sprintf(envval, "%"PRIxPTR, (uintptr_t)ptr);
    setenv("VULKAN_PTR", envval, 1);
}

void load_vulkan() {
    const char* zinkPreferSystemDriver = getenv("POJAV_ZINK_PREFER_SYSTEM_DRIVER");
    int deviceApiLevel = android_get_device_api_level();
    if (zinkPreferSystemDriver == NULL && deviceApiLevel >= 28) {
#ifdef ADRENO_POSSIBLE
        void* result = loadTurnipVulkan();
        if (result != NULL)
        {
            printf("AdrenoSupp: Loaded Turnip, loader address: %p\n", result);
            set_vulkan_ptr(result);
            return;
        }
#endif
    }

    printf("OSMDroid: Loading Vulkan regularly...\n");
    void* vulkanPtr = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
    printf("OSMDroid: Loaded Vulkan, ptr=%p\n", vulkanPtr);
    set_vulkan_ptr(vulkanPtr);
}

void renderer_load_config() {
    ConfigBridgeTbl();
    if (pojav_environ->bridge_config == 0)
    {
        pojav_environ->config_renderer = RENDERER_VK_ZINK;
        set_osm_bridge_tbl();
        return;
    }
    printf("Config Bridge: Config = %p\n", pojav_environ->bridge_config);
    switch (pojav_environ->bridge_config) {
        case BRIDGE_TBL_XXX1: {
            pojav_environ->config_renderer = RENDERER_VK_ZINK_XXX1;
            osm_bridge_xxx1();
        } break;
        case BRIDGE_TBL_XXX2: {
            pojav_environ->config_renderer = RENDERER_VK_ZINK_XXX2;
            xxx2OsmInit();
            xxx2OsmloadSymbols();
        } break;
        case BRIDGE_TBL_XXX3: {
            pojav_environ->config_renderer = RENDERER_VK_ZINK_XXX3;
            xxx3OsmloadSymbols();
        } break;
        case BRIDGE_TBL_XXX4:
            // Nothing to do here
            break;
        default: {
            pojav_environ->config_renderer = RENDERER_VK_ZINK;
            set_osm_bridge_tbl();
        } break;
    }
}

int pojavInitOpenGL() {
    // Only affects GL4ES as of now
    const char *forceVsync = getenv("FORCE_VSYNC");
    if (!strcmp(forceVsync, "true"))
        pojav_environ->force_vsync = true;

    // NOTE: Override for now.
    const char *renderer = pojav_environ->rendererTag;
    const char *ldrivermodel = getenv("LOCAL_DRIVER_MODEL");
    const char *mldo = getenv("LOCAL_LOADER_OVERRIDE");
    load_vulkan();

    if (mldo != NULL) printf("OSMDroid: MESA_LOADER_DRIVER_OVERRIDE = %s\n", mldo);

    if (!strncmp("opengles", renderer, 8))
    {
        ConfigBridgeTbl();
        pojav_environ->config_renderer = RENDERER_GL4ES;
        if (pojav_environ->bridge_config == 0) set_gl_bridge_tbl();
    }

    if (!strcmp(renderer, "custom_gallium"))
    {
        renderer_load_config();
    }

    if (!strcmp(renderer, "mesa_3d"))
    {

        if (!strcmp(ldrivermodel, "gallium_zink") || !strcmp(ldrivermodel, "vulkan_zink"))
        {
            setenv("GALLIUM_DRIVER", "zink", 1);
            setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
            setenv("mesa_glthread", "true", 1);
            renderer_load_config();
        }

        if (!strcmp(ldrivermodel, "gallium_virgl"))
        {
            pojav_environ->config_renderer = RENDERER_VIRGL;
            setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
            setenv("GALLIUM_DRIVER", "virpipe", 1);
            setenv("OSMESA_NO_FLUSH_FRONTBUFFER", "1", false);
            if (!strcmp(getenv("OSMESA_NO_FLUSH_FRONTBUFFER"), "1"))
                printf("VirGL: OSMesa buffer flush is DISABLED!\n");
            loadSymbolsVirGL();
            virglInit();
            return 0;
        }

        if (!strcmp(ldrivermodel, "gallium_panfrost"))
        {
            setenv("GALLIUM_DRIVER", "panfrost", 1);
            renderer_load_config();
        }

        if (!strcmp(ldrivermodel, "gallium_freedreno"))
        {
            setenv("GALLIUM_DRIVER", "freedreno", 1);
            if (mldo != NULL) setenv("MESA_LOADER_DRIVER_OVERRIDE", mldo, 1);
            else setenv("MESA_LOADER_DRIVER_OVERRIDE", "kgsl", 1);
            renderer_load_config();
        }

        if (!strcmp(ldrivermodel, "gallium_softpipe"))
        {
            setenv("GALLIUM_DRIVER", "softpipe", 1);
            setenv("LIBGL_ALWAYS_SOFTWARE", "1", 1);
            renderer_load_config();
        }

        if (!strcmp(ldrivermodel, "gallium_llvmpipe"))
        {
            setenv("GALLIUM_DRIVER", "llvmpipe", 1);
            setenv("LIBGL_ALWAYS_SOFTWARE", "1", 1);
            renderer_load_config();
        }
    }

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK)
        if (br_init()) br_setup_window();

    if (pojav_environ->config_renderer == RENDERER_GL4ES)
    {
        if (pojav_environ->bridge_config != 0)
        {
            printf("Config Bridge: Config = %p\n", pojav_environ->bridge_config);
            if (gl_init()) gl_setup_window();
        } else {
            if (br_init()) br_setup_window();
        }
    }

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX1)
        if (br_init()) br_setup_window();

    return 0;
}

EXTERNAL_API int pojavInit() {
    pojav_environ->glfwThreadVmEnv = get_attached_env(pojav_environ->runtimeJavaVMPtr);
    if(pojav_environ->glfwThreadVmEnv == NULL) {
        printf("Failed to attach Java-side JNIEnv to GLFW thread\n");
        return 0;
    }
    ANativeWindow_acquire(pojav_environ->pojavWindow);
    pojav_environ->savedWidth = ANativeWindow_getWidth(pojav_environ->pojavWindow);
    pojav_environ->savedHeight = ANativeWindow_getHeight(pojav_environ->pojavWindow);
    ANativeWindow_setBuffersGeometry(pojav_environ->pojavWindow,pojav_environ->savedWidth,pojav_environ->savedHeight,AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM);
    pojavInitOpenGL();
    return 1;
}

EXTERNAL_API void pojavSetWindowHint(int hint, int value) {
    if (hint != GLFW_CLIENT_API) return;
    switch (value) {
        case GLFW_NO_API:
            pojav_environ->config_renderer = RENDERER_VULKAN;
            /* Nothing to do: initialization is handled in Java-side */
            // pojavInitVulkan();
            break;
        case GLFW_OPENGL_API:
            /* Nothing to do: initialization is called in pojavCreateContext */
            // pojavInitOpenGL();
            break;
        default:
            printf("GLFW: Unimplemented API 0x%x\n", value);
            abort();
    }
}

EXTERNAL_API void pojavSwapBuffers() {
    if (currentFps >= INT_MAX - 1)
        currentFps = 0;
    currentFps++;

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK
     || pojav_environ->config_renderer == RENDERER_GL4ES)
    {
        if (pojav_environ->bridge_config != 0 && pojav_environ->config_renderer == RENDERER_GL4ES)
            gl_swap_buffers();
        else br_swap_buffers();
    }

    if (pojav_environ->config_renderer == RENDERER_VIRGL)
        virglSwapBuffers();

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX2)
        xxx2OsmSwapBuffers();

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX3)
        xxx3OsmSwapBuffers();

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX1)
        br_swap_buffers();
}

EXTERNAL_API void pojavMakeCurrent(void* window) {
    if (getenv("POJAV_BIG_CORE_AFFINITY") != NULL) bigcore_set_affinity();

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK
     || pojav_environ->config_renderer == RENDERER_GL4ES)
    {
        if (pojav_environ->bridge_config != 0 && pojav_environ->config_renderer == RENDERER_GL4ES)
            gl_make_current((gl_render_window_t*)window);
        else br_make_current((basic_render_window_t*)window);
    }

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX1)
        br_make_current((basic_render_window_t*)window);

    if (pojav_environ->config_renderer == RENDERER_VIRGL)
        virglMakeCurrent(window);

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX2)
        xxx2OsmMakeCurrent(window);

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX3)
        xxx3OsmMakeCurrent();

}

EXTERNAL_API void* pojavCreateContext(void* contextSrc) {
    if (pojav_environ->config_renderer == RENDERER_VULKAN)
        return (void *) pojav_environ->pojavWindow;

    if (pojav_environ->bridge_config != 0 && pojav_environ->config_renderer == RENDERER_GL4ES)
        return gl_init_context(contextSrc);

    if (pojav_environ->config_renderer == RENDERER_VIRGL)
        return virglCreateContext(contextSrc);

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX2)
        return xxx2OsmCreateContext(contextSrc);

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX3)
        return xxx3OsmCreateContext(contextSrc);

    return br_init_context((basic_render_window_t*)contextSrc);
}

void* maybe_load_vulkan() {
    // We use the env var because
    // 1. it's easier to do that
    // 2. it won't break if something will try to load vulkan and osmesa simultaneously
    if (getenv("VULKAN_PTR") == NULL) load_vulkan();
    return (void*) strtoul(getenv("VULKAN_PTR"), NULL, 0x10);
}

EXTERNAL_API JNIEXPORT jlong JNICALL
Java_org_lwjgl_vulkan_VK_getVulkanDriverHandle(ABI_COMPAT JNIEnv *env, ABI_COMPAT jclass thiz) {
    printf("EGLBridge: LWJGL-side Vulkan loader requested the Vulkan handle\n");
    return (jlong) maybe_load_vulkan();
}

EXTERNAL_API JNIEXPORT jint JNICALL
Java_org_lwjgl_glfw_CallbackBridge_initFps(JNIEnv *env, jclass clazz) {
    int fps = currentFps;
    currentFps = 0;
    return fps;
}

EXTERNAL_API JNIEXPORT void JNICALL
Java_org_lwjgl_opengl_GL_nativeRegalMakeCurrent(JNIEnv *env, jclass clazz) {
    if (InitialFrameBuffer() && (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX1
     || pojav_environ->config_renderer == RENDERER_VIRGL
     || pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX2))
    {
        /*printf("Regal: making current");
    
        RegalMakeCurrent_func *RegalMakeCurrent = (RegalMakeCurrent_func *) dlsym(RTLD_DEFAULT, "RegalMakeCurrent");
        RegalMakeCurrent(potatoBridge.eglContext);*/

        printf("regal removed\n");
        abort();
    }
}

EXTERNAL_API JNIEXPORT jlong JNICALL
Java_org_lwjgl_opengl_GL_getGraphicsBufferAddr(JNIEnv *env, jobject thiz) {
    if (InitialFrameBuffer() &&
       (pojav_environ->config_renderer == RENDERER_VIRGL
     || pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX1
     || pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX2))
    {
        // return &gbuffer;
        return (jlong)gbuffer;
    }
}

EXTERNAL_API JNIEXPORT jintArray JNICALL
Java_org_lwjgl_opengl_GL_getNativeWidthHeight(JNIEnv *env, jobject thiz) {
    if (InitialFrameBuffer() && (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX1
     || pojav_environ->config_renderer == RENDERER_VIRGL
     || pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX2))
    {
        jintArray ret = (*env)->NewIntArray(env, 2);
        jint arr[] = {pojav_environ->savedWidth, pojav_environ->savedHeight};
        (*env)->SetIntArrayRegion(env, ret, 0, 2, arr);
        return ret;
    }
}

EXTERNAL_API void pojavSwapInterval(int interval) {
    if(pojav_environ->config_renderer == RENDERER_VK_ZINK
     || pojav_environ->config_renderer == RENDERER_GL4ES)
    {
        if (pojav_environ->bridge_config != 0 && pojav_environ->config_renderer == RENDERER_GL4ES)
            gl_swap_interval(interval);
        else br_swap_interval(interval);
    }

    if (pojav_environ->config_renderer == RENDERER_VIRGL)
        virglSwapInterval(interval);

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX2)
        xxx2OsmSwapInterval(interval);

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX3)
        xxx3OsmSwapInterval(interval);

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_XXX1)
    {
        br_swap_interval(interval);
        printf("eglSwapInterval: NOT IMPLEMENTED YET!\n");
        // Nothing to do here
    }
}



