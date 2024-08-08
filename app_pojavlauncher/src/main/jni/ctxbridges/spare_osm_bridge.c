//
// Created by Vera-Firefly on 02.08.2024.
//

#include <malloc.h>
#include <stdio.h>
#include <string.h>
#include <environ/environ.h>
#include "spare_osm_bridge.h"
#include "spare_renderer_config.h"

#ifdef FRAME_BUFFER_SUPPOST

void* mbuffer;

#endif

static const char* osm_LogTag = "OSMBridge";
static __thread spare_osm_render_window_t* currentBundle;
static char spare_no_render_buffer[4];


void setNativeWindowSwapInterval(struct ANativeWindow* nativeWindow, int swapInterval);

bool spare_osm_init() {
    dlsym_OSMesa();
    return true;
}

spare_osm_render_window_t* spare_osm_get_current() {
    return currentBundle;
}

spare_osm_render_window_t* spare_osm_init_context(spare_osm_render_window_t* share) {

    spare_osm_render_window_t* render_window = malloc(sizeof(spare_osm_render_window_t));
    if(render_window == NULL) return NULL;

    printf("%s: generating context\n", osm_LogTag);
    memset(render_window, 0, sizeof(spare_osm_render_window_t));
    OSMesaContext osmesa_share = NULL;
    if (share != NULL) osmesa_share = share->context;
    OSMesaContext context = OSMesaCreateContext_p(OSMESA_RGBA, osmesa_share);

    if(context == NULL)
    {
        free(render_window);
        return NULL;
    }

    printf("%s: context=%p\n", osm_LogTag, context);
    render_window->context = context;
    return render_window;

}

void spare_osm_set_no_render_buffer(ANativeWindow_Buffer* buffer) {
    buffer->bits = &spare_no_render_buffer;
    buffer->width = 1;
    buffer->height = 1;
    buffer->stride = 0;
}

void spare_osm_swap_surfaces(spare_osm_render_window_t* bundle) {

    if(bundle->nativeSurface != NULL && bundle->newNativeSurface != bundle->nativeSurface) {
        if(!bundle->disable_rendering) {
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
        spare_osm_set_no_render_buffer(&bundle->buffer);
        bundle->disable_rendering = true;
    }

}

void spare_osm_release_window() {
    currentBundle->newNativeSurface = NULL;
    spare_osm_swap_surfaces(currentBundle);
}

void spare_osm_apply_current_l(ANativeWindow_Buffer* buffer) {
    /**
    *printf("%s: context= %d, setbuffer: %d, width= %d, height= %d, stride= %d \n",
    *      osm_LogTag,
    *      currentBundle->context,
    *      buffer->bits,
    *      buffer->width,
    *      buffer->height,
    *      buffer->stride);
    */
    OSMesaMakeCurrent_p(currentBundle->context,
                        buffer->bits,
                        GL_UNSIGNED_BYTE,
                        buffer->width,
                        buffer->height);
    if (buffer->stride != currentBundle->last_stride)
        OSMesaPixelStore_p(OSMESA_ROW_LENGTH, buffer->stride);
    currentBundle->last_stride = buffer->stride;
}

void spare_osm_apply_current_ll(ANativeWindow_Buffer* buffer) {
    /**
    *printf("%s: context= %d, setbuffer: %d, width= %d, height= %d, stride= %d \n",
    *      osm_LogTag,
    *      currentBundle->context,
    *      buffer->bits,
    *      buffer->width,
    *      buffer->height,
    *      buffer->stride);
    */

    if (SpareBuffer())
    {
#ifdef FRAME_BUFFER_SUPPOST
        mbuffer = malloc(buffer->width * buffer->height * 4);
        printf("%s: reserving %d bytes for frame buffer\n", osm_LogTag, mbuffer);
        OSMesaMakeCurrent_p(currentBundle->context,
                               mbuffer,
                               GL_UNSIGNED_BYTE,
                               buffer->width,
                               buffer->height);
#endif
    } else {
        OSMesaMakeCurrent_p(currentBundle->context,
                               setbuffer,
                               GL_UNSIGNED_BYTE,
                               buffer->width,
                               buffer->height);
    }
    if (buffer->stride != currentBundle->last_stride)
        OSMesaPixelStore_p(OSMESA_ROW_LENGTH, buffer->stride);
    currentBundle->last_stride = buffer->stride;
}

void spare_osm_make_current(spare_osm_render_window_t* bundle) {

    if (bundle == NULL)
    {
        OSMesaMakeCurrent_p(NULL, NULL, 0, 0, 0);
        currentBundle = NULL;
        return;
    }

    static bool hasSetNoRendererBuffer = false;
    bool hasSetMainWindow = false;
    currentBundle = bundle;

    if (pojav_environ->mainWindowBundle == NULL)
    {
        pojav_environ->mainWindowBundle = (basic_render_window_t*) bundle;
        printf("%s: Main window bundle is now %p\n", osm_LogTag, pojav_environ->mainWindowBundle);
        pojav_environ->mainWindowBundle->newNativeSurface = pojav_environ->pojavWindow;
        hasSetMainWindow = true;
    }

    if (bundle->nativeSurface == NULL)
    {
        spare_osm_swap_surfaces(bundle);
        if(hasSetMainWindow) pojav_environ->mainWindowBundle->state = STATE_RENDERER_ALIVE;
    }

    if (!hasSetNoRendererBuffer)
    {
        spare_osm_set_no_render_buffer(&bundle->buffer);
        printf("%s: Has set no renderer buffer!\n", osm_LogTag);
        hasSetNoRendererBuffer = true;
    }

    printf("%s: making current\n", osm_LogTag);
    printf("%s: bundle buffer = %d\n", osm_LogTag, bundle->buffer);

    spare_osm_apply_current_ll(&currentBundle->buffer);
    OSMesaPixelStore_p(OSMESA_Y_UP, 0);

}

void spare_osm_swap_buffers() {

    if (currentBundle->state == STATE_RENDERER_NEW_WINDOW)
    {
        spare_osm_swap_surfaces(currentBundle);
        currentBundle->state = STATE_RENDERER_ALIVE;
    }

    if (currentBundle->nativeSurface != NULL && !currentBundle->disable_rendering)
        if (ANativeWindow_lock(currentBundle->nativeSurface, &currentBundle->buffer, NULL) != 0)
            spare_osm_release_window();

    // printf("%s: swap buffers: %d\n", osm_LogTag, currentBundle->buffer);
    spare_osm_apply_current_l(&currentBundle->buffer);
    glFinish_p();

    if (currentBundle->nativeSurface != NULL && !currentBundle->disable_rendering)
        if (ANativeWindow_unlockAndPost(currentBundle->nativeSurface) != 0)
            spare_osm_release_window();

}

void spare_osm_setup_window() {

    if (pojav_environ->mainWindowBundle != NULL)
    {
        printf("%s: Main window bundle is not NULL, changing state\n", osm_LogTag);
        pojav_environ->mainWindowBundle->state = STATE_RENDERER_NEW_WINDOW;
        pojav_environ->mainWindowBundle->newNativeSurface = pojav_environ->pojavWindow;
    }

}

void spare_osm_swap_interval(int swapInterval) {

    if (pojav_environ->mainWindowBundle != NULL && pojav_environ->mainWindowBundle->nativeSurface != NULL)
        setNativeWindowSwapInterval(pojav_environ->mainWindowBundle->nativeSurface, swapInterval);

}