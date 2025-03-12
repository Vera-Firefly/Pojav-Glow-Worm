//
// Created by Vera-Firefly on 02.08.2024.
//

#include <malloc.h>
#include <stdio.h>
#include <string.h>
#include <environ/environ.h>
#include "osm_bridge_xxx1.h"

#define OSM_CTX
#include "renderer_config.h"

static const char* osm_LogTag = "OSMBridge";
static __thread xxx1_osm_render_window_t* currentBundle;
static char xxx1_no_render_buffer[4];
static bool hasSetNoRendererBuffer = false;


void setNativeWindowSwapInterval(struct ANativeWindow* nativeWindow, int swapInterval);

bool xxx1_osm_init() {
    dlsym_OSMesa();
    return true;
}

xxx1_osm_render_window_t* xxx1_osm_get_current() {
    return currentBundle;
}

xxx1_osm_render_window_t* xxx1_osm_init_context(xxx1_osm_render_window_t* share) {

    xxx1_osm_render_window_t* render_window = malloc(sizeof(xxx1_osm_render_window_t));
    if (render_window == NULL) return NULL;

    printf("%s: generating context\n", osm_LogTag);
    memset(render_window, 0, sizeof(xxx1_osm_render_window_t));
    OSMesaContext osmesa_share = NULL;
    if (share != NULL) osmesa_share = share->context;
    OSMesaContext context = OSMesaCreateContext_p(OSMESA_RGBA, osmesa_share);

    if (context == NULL)
    {
        free(render_window);
        return NULL;
    }

    printf("%s: context=%p\n", osm_LogTag, context);
    render_window->context = context;
    return render_window;

}

void xxx1_osm_set_no_render_buffer(ANativeWindow_Buffer* buffer) {
    buffer->bits = &xxx1_no_render_buffer;
    buffer->width = 1;
    buffer->height = 1;
    buffer->stride = 0;
}

void xxx1_osm_swap_surfaces(xxx1_osm_render_window_t* bundle) {

    if (bundle->nativeSurface != NULL && bundle->newNativeSurface != bundle->nativeSurface)
    {
        if (!bundle->disable_rendering)
        {
            printf("%s: Unlocking for cleanup...\n", osm_LogTag);
            ANativeWindow_unlockAndPost(bundle->nativeSurface);
        }
        ANativeWindow_release(bundle->nativeSurface);
    }

    if (bundle->newNativeSurface != NULL)
    {
        printf("%s: Switching to new native surface\n", osm_LogTag);
        bundle->nativeSurface = bundle->newNativeSurface;
        bundle->newNativeSurface = NULL;
        ANativeWindow_acquire(bundle->nativeSurface);
        ANativeWindow_setBuffersGeometry(bundle->nativeSurface, 0, 0, WINDOW_FORMAT_RGBX_8888);
        bundle->disable_rendering = false;
        return;
    } else {
        printf("%s:No new native surface, switching to dummy framebuffer\n", osm_LogTag);
        bundle->nativeSurface = NULL;
        xxx1_osm_set_no_render_buffer(&bundle->buffer);
        bundle->disable_rendering = true;
    }

}

void xxx1_osm_release_window() {
    currentBundle->newNativeSurface = NULL;
    xxx1_osm_swap_surfaces(currentBundle);
}

void xxx1_osm_apply_current_l(ANativeWindow_Buffer* buffer) {
    osm_make_current_l(currentBundle->context, buffer->bits, buffer->width, buffer->height);
    if (buffer->stride != currentBundle->last_stride)
        osm_pixel_store(buffer->stride);
    currentBundle->last_stride = buffer->stride;
}

void xxx1_osm_apply_current_ll(ANativeWindow_Buffer* buffer) {
    osm_make_current_ll(currentBundle->context, buffer->width, buffer->height);
    if (buffer->stride != currentBundle->last_stride)
        osm_pixel_store(buffer->stride);
    currentBundle->last_stride = buffer->stride;
}

void xxx1_osm_make_current(xxx1_osm_render_window_t* bundle) {

    if (bundle == NULL)
    {
        OSMesaMakeCurrent_p(NULL, NULL, 0, 0, 0);
        currentBundle = NULL;
        return;
    }

    bool hasSetMainWindow = false;
    currentBundle = bundle;

    if (pojav_environ->mainWindowBundle == NULL)
    {
        printf("%s: making current\n", osm_LogTag);
        pojav_environ->mainWindowBundle = (basic_render_window_t*) bundle;
        printf("%s: Main window bundle is now %p\n", osm_LogTag, pojav_environ->mainWindowBundle);
        pojav_environ->mainWindowBundle->newNativeSurface = pojav_environ->pojavWindow;
        hasSetMainWindow = true;
    }

    if (bundle->nativeSurface == NULL)
    {
        xxx1_osm_swap_surfaces(bundle);
        if (hasSetMainWindow) pojav_environ->mainWindowBundle->state = STATE_RENDERER_ALIVE;
    }

    if (!hasSetNoRendererBuffer)
    {
        xxx1_osm_set_no_render_buffer(&bundle->buffer);
        printf("%s: Has set no renderer buffer!\n", osm_LogTag);
        printf("%s: bundle buffer = %d\n", osm_LogTag, bundle->buffer);

        printf("OSMDroid: vendor: %s\n", glGetString_p(GL_VENDOR));
        printf("OSMDroid: renderer: %s\n", glGetString_p(GL_RENDERER));
        hasSetNoRendererBuffer = true;
    }

    xxx1_osm_apply_current_ll(&currentBundle->buffer);
    osm_screen_o();
}

void xxx1_osm_swap_buffers() {

    if (currentBundle->state == STATE_RENDERER_NEW_WINDOW)
    {
        xxx1_osm_swap_surfaces(currentBundle);
        currentBundle->state = STATE_RENDERER_ALIVE;
    }

    if (currentBundle->nativeSurface != NULL && !currentBundle->disable_rendering)
        if (ANativeWindow_lock(currentBundle->nativeSurface, &currentBundle->buffer, NULL) != 0)
            xxx1_osm_release_window();

    xxx1_osm_apply_current_l(&currentBundle->buffer);
    glFinish_p();

    if (currentBundle->nativeSurface != NULL && !currentBundle->disable_rendering)
        if (ANativeWindow_unlockAndPost(currentBundle->nativeSurface) != 0)
            xxx1_osm_release_window();

}

void xxx1_osm_setup_window() {

    if (pojav_environ->mainWindowBundle != NULL)
    {
        printf("%s: Main window bundle is not NULL, changing state\n", osm_LogTag);
        pojav_environ->mainWindowBundle->state = STATE_RENDERER_NEW_WINDOW;
        pojav_environ->mainWindowBundle->newNativeSurface = pojav_environ->pojavWindow;
    }

}

void xxx1_osm_swap_interval(int swapInterval) {

    if (pojav_environ->mainWindowBundle != NULL && pojav_environ->mainWindowBundle->nativeSurface != NULL)
        setNativeWindowSwapInterval(pojav_environ->mainWindowBundle->nativeSurface, swapInterval);

}