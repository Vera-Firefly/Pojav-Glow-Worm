//
// Modifiled by Vera-Firefly on 02.08.2024.
//
#include <jni.h>
#include <assert.h>
#include <dlfcn.h>

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
#include "ctxbridges/spare_bridge.h"
#include "ctxbridges/spare_osm_bridge.h"
#include "ctxbridges/spare_renderer_config.h"
#include "ctxbridges/renderer_config.h"
#include "ctxbridges/virgl_bridge.h"
#include "driver_helper/nsbypass.h"

#define GLFW_CLIENT_API 0x22001
/* Consider GLFW_NO_API as Vulkan API */
#define GLFW_NO_API 0
#define GLFW_OPENGL_API 0x30001
// region OSMESA internals

// This means that the function is an external API and that it will be used
#define EXTERNAL_API __attribute__((used))
// This means that you are forced to have this function/variable for ABI compatibility
#define ABI_COMPAT __attribute__((unused))

EGLConfig config;
struct PotatoBridge potatoBridge;

void bigcore_set_affinity();

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
        case RENDERER_VK_ZINK_PREF:
            break;
    }
}

int SpareBridge() {
    if (getenv("POJAV_SPARE_BRIDGE") != NULL) return 1;
    return 0;
}

int SpareBuffer() {
    if (getenv("POJAV_SPARE_FRAME_BUFFER") != NULL) return 1;
    return 0;
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeWindow(JNIEnv* env, ABI_COMPAT jclass clazz, jobject surface) {
    pojav_environ->pojavWindow = ANativeWindow_fromSurface(env, surface);

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK
     || pojav_environ->config_renderer == RENDERER_GL4ES)
    {
        if (SpareBridge() && pojav_environ->config_renderer == RENDERER_GL4ES)
            gl_setup_window();
        else if (br_setup_window != NULL) br_setup_window();
    }

    // pojav_environ->config_renderer == RENDERER_VK_ZINK_PREF
    if (spare_setup_window != NULL) spare_setup_window();

}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_releaseBridgeWindow(ABI_COMPAT JNIEnv *env, ABI_COMPAT jclass clazz) {
    ANativeWindow_release(pojav_environ->pojavWindow);
}

/*If you don't want your renderer for
the Mesa class to crash in your launcher
don't touch the code here
*/
EXTERNAL_API void* pojavGetCurrentContext() {

    if (SpareBridge() && pojav_environ->config_renderer == RENDERER_GL4ES)
        return (void *)eglGetCurrentContext_p();

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_PREF)
        return spare_get_current();

    if (pojav_environ->config_renderer == RENDERER_VIRGL)
        return virglGetCurrentContext();

    return br_get_current();
}

static void set_vulkan_ptr(void* ptr) {
    char envval[64];
    sprintf(envval, "%"PRIxPTR, (uintptr_t)ptr);
    setenv("VULKAN_PTR", envval, 1);
}

void load_vulkan() {
    if(getenv("POJAV_ZINK_PREFER_SYSTEM_DRIVER") == NULL && android_get_device_api_level() >= 28) {
    // the loader does not support below that
#ifdef ADRENO_POSSIBLE
        void* result = load_turnip_vulkan();
        if(result != NULL) {
            printf("AdrenoSupp: Loaded Turnip, loader address: %p\n", result);
            set_vulkan_ptr(result);
            return;
        }
#endif
    }
    printf("OSMDroid: loading vulkan regularly...\n");
    void* vulkan_ptr = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
    printf("OSMDroid: loaded vulkan, ptr=%p\n", vulkan_ptr);
    set_vulkan_ptr(vulkan_ptr);
}

void renderer_load_config() {
    if(!SpareBridge()) {
        pojav_environ->config_renderer = RENDERER_VK_ZINK;
        set_osm_bridge_tbl();
    } else {
        pojav_environ->config_renderer = RENDERER_VK_ZINK_PREF;
        spare_osm_bridge();
    }
}

int pojavInitOpenGL() {
    // Only affects GL4ES as of now
    const char *forceVsync = getenv("FORCE_VSYNC");
    if (!strcmp(forceVsync, "true"))
        pojav_environ->force_vsync = true;

    // NOTE: Override for now.
    const char *renderer = getenv("POJAV_BETA_RENDERER");
    const char *ldrivermodel = getenv("LOCAL_DRIVER_MODEL");
    const char *mldo = getenv("LOCAL_LOADER_OVERRIDE");

    if (mldo) printf("OSMDroid: MESA_LOADER_DRIVER_OVERRIDE = %s\n", mldo);

    if (!strncmp("opengles", renderer, 8))
    {
        pojav_environ->config_renderer = RENDERER_GL4ES;
        setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
        if (!SpareBridge()) set_gl_bridge_tbl();
    } else if (!strcmp(renderer, "mesa_3d")) {

        if (!strcmp(ldrivermodel, "driver_zink"))
        {
            setenv("GALLIUM_DRIVER", "zink", 1);
            renderer_load_config();
            load_vulkan();
        }

        if (!strcmp(ldrivermodel, "driver_virgl"))
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

        if (!strcmp(ldrivermodel, "driver_panfrost"))
        {
            setenv("GALLIUM_DRIVER", "panfrost", 1);
            renderer_load_config();
        }

        if (!strcmp(ldrivermodel, "driver_freedreno"))
        {
            setenv("GALLIUM_DRIVER", "freedreno", 1);
            if (mldo) setenv("MESA_LOADER_DRIVER_OVERRIDE", mldo, 1);
            else setenv("MESA_LOADER_DRIVER_OVERRIDE", "kgsl", 1);
            renderer_load_config();
        }

        if (!strcmp(ldrivermodel, "driver_softpipe"))
        {
            setenv("GALLIUM_DRIVER", "softpipe", 1);
            setenv("LIBGL_ALWAYS_SOFTWARE", "1", 1);
            renderer_load_config();
        }

        if (!strcmp(ldrivermodel, "driver_llvmpipe"))
        {
            setenv("GALLIUM_DRIVER", "llvmpipe", 1);
            setenv("LIBGL_ALWAYS_SOFTWARE", "1", 1);
            renderer_load_config();
        }
    }

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK
     || pojav_environ->config_renderer == RENDERER_GL4ES)
    {
        if (gl_init() && SpareBridge() && pojav_environ->config_renderer == RENDERER_GL4ES)
            gl_setup_window();
        else if (br_init()) br_setup_window();
    }

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_PREF && spare_init())
        spare_setup_window();

    return 0;
}

EXTERNAL_API int pojavInit() {
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
    if (pojav_environ->config_renderer == RENDERER_VK_ZINK
     || pojav_environ->config_renderer == RENDERER_GL4ES)
    {
        if (SpareBridge() && pojav_environ->config_renderer == RENDERER_GL4ES)
            gl_swap_buffers();
        else br_swap_buffers();
    }

    if (pojav_environ->config_renderer == RENDERER_VIRGL)
        virglSwapBuffers();

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_PREF)
        spare_swap_buffers();
}

EXTERNAL_API void pojavMakeCurrent(void* window) {
    if (getenv("POJAV_BIG_CORE_AFFINITY") != NULL) bigcore_set_affinity();

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK
     || pojav_environ->config_renderer == RENDERER_GL4ES)
    {
        if(SpareBridge() && pojav_environ->config_renderer == RENDERER_GL4ES)
            gl_make_current((gl_render_window_t*)window);
        else br_make_current((basic_render_window_t*)window);
    }

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_PREF)
        spare_make_current((basic_render_window_t*)window);

    if (pojav_environ->config_renderer == RENDERER_VIRGL)
        virglMakeCurrent(window);

}

EXTERNAL_API void* pojavCreateContext(void* contextSrc) {
    if (pojav_environ->config_renderer == RENDERER_VULKAN)
        return (void *) pojav_environ->pojavWindow;

    if (SpareBridge() && pojav_environ->config_renderer == RENDERER_GL4ES)
        return gl_init_context(contextSrc);

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_PREF)
        return spare_init_context((basic_render_window_t*)contextSrc);

    if (pojav_environ->config_renderer == RENDERER_VIRGL)
        return virglCreateContext(contextSrc);

    return br_init_context((basic_render_window_t*)contextSrc);
}

EXTERNAL_API JNIEXPORT jlong JNICALL
Java_org_lwjgl_vulkan_VK_getVulkanDriverHandle(ABI_COMPAT JNIEnv *env, ABI_COMPAT jclass thiz) {
    printf("EGLBridge: LWJGL-side Vulkan loader requested the Vulkan handle\n");
    // The code below still uses the env var because
    // 1. it's easier to do that
    // 2. it won't break if something will try to load vulkan and osmesa simultaneously
    if(getenv("VULKAN_PTR") == NULL) load_vulkan();
    return strtoul(getenv("VULKAN_PTR"), NULL, 0x10);
}

#ifdef FRAME_BUFFER_SUPPOST
EXTERNAL_API JNIEXPORT void JNICALL
Java_org_lwjgl_opengl_GL_nativeRegalMakeCurrent(JNIEnv *env, jclass clazz) {
    if (SpareBuffer() && (pojav_environ->config_renderer == RENDERER_VK_ZINK_PREF
     || pojav_environ->config_renderer == RENDERER_VIRGL))
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
    if (SpareBuffer() && pojav_environ->config_renderer == RENDERER_VIRGL)
        return &gbuffer;
    else if (SpareBuffer() && pojav_environ->config_renderer == RENDERER_VK_ZINK_PREF)
        return &mbuffer;
}

EXTERNAL_API JNIEXPORT jintArray JNICALL
Java_org_lwjgl_opengl_GL_getNativeWidthHeight(JNIEnv *env, jobject thiz) {
    if (SpareBuffer() && (pojav_environ->config_renderer == RENDERER_VK_ZINK_PREF
     || pojav_environ->config_renderer == RENDERER_VIRGL))
    {
        jintArray ret = (*env)->NewIntArray(env,2);
        jint arr[] = {pojav_environ->savedWidth, pojav_environ->savedHeight};
        (*env)->SetIntArrayRegion(env,ret,0,2,arr);
        return ret;
    }
}
#endif

EXTERNAL_API void pojavSwapInterval(int interval) {
    if(pojav_environ->config_renderer == RENDERER_VK_ZINK
     || pojav_environ->config_renderer == RENDERER_GL4ES)
    {
        if(SpareBridge() && pojav_environ->config_renderer == RENDERER_GL4ES)
            gl_swap_interval(interval);
        else br_swap_interval(interval);
    }

    if (pojav_environ->config_renderer == RENDERER_VIRGL)
        virglSwapInterval(interval);

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK_PREF)
    {
        spare_swap_interval(interval);
        printf("eglSwapInterval: NOT IMPLEMENTED YET!\n");
        // Nothing to do here
    }
}



