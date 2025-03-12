//
// Created by Vera-Firefly on 20.11.2024.
//

#include <android/native_window.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
#include <dlfcn.h>
#include <assert.h>
#include <malloc.h>
#include <stdlib.h>
#include "environ/environ.h"
#include "osm_bridge_xxx2.h"
#include "osmesa_loader.h"

#define OSM_CTX
#include "renderer_config.h"

static struct xxx2_osm_render_window_t *xxx2_osm;
static bool hasCleaned = false;
static bool hasSetNoRendererBuffer = false;
static char xxx2_no_render_buffer[4];

void setNativeWindowSwapInterval(struct ANativeWindow* nativeWindow, int swapInterval);

void xxx2_osm_set_no_render_buffer(ANativeWindow_Buffer* buf) {
    buf->bits = &xxx2_no_render_buffer;
    buf->width = pojav_environ->savedWidth;
    buf->height = pojav_environ->savedHeight;
    buf->stride = 0;
}

void *xxx2OsmGetCurrentContext() {
    return (void *)OSMesaGetCurrentContext_p();
}

void xxx2OsmloadSymbols() {
    dlsym_OSMesa();
}

void xxx2_osm_apply_current_l(ANativeWindow_Buffer* buf) {
    OSMesaContext ctx = OSMesaGetCurrentContext_p();
    if (ctx == NULL)
        printf("Zink: attempted to swap buffers without context!");

    osm_make_current_l(ctx, buf->bits, buf->width, buf->height);
    if (buf->stride != xxx2_osm->last_stride)
        osm_pixel_store(buf->stride);
    xxx2_osm->last_stride = buf->stride;
}

void xxx2_osm_apply_current_ll(ANativeWindow_Buffer* buf) {
    osm_make_current_ll(xxx2_osm->window, buf->width, buf->height);
    if (buf->stride != xxx2_osm->last_stride)
        osm_pixel_store(buf->stride);
    xxx2_osm->last_stride = buf->stride;
}

void xxx2OsmSwapBuffers() {
    ANativeWindow_lock(xxx2_osm->nativeSurface, &xxx2_osm->buffer, NULL);
    xxx2_osm_apply_current_l(&xxx2_osm->buffer);
    glFinish_p();
    ANativeWindow_unlockAndPost(xxx2_osm->nativeSurface);
}

void xxx2OsmMakeCurrent(void *window) {
    if (!hasCleaned)
    {
        printf("OSMDroid: making current\n");
        xxx2_osm->nativeSurface = pojav_environ->pojavWindow;
        ANativeWindow_acquire(xxx2_osm->nativeSurface);
        ANativeWindow_setBuffersGeometry(xxx2_osm->nativeSurface, 0, 0, WINDOW_FORMAT_RGBX_8888);
        ANativeWindow_lock(xxx2_osm->nativeSurface, &xxx2_osm->buffer, NULL);
    }

    if (!hasSetNoRendererBuffer)
    {
        hasSetNoRendererBuffer = true;
        xxx2_osm_set_no_render_buffer(&xxx2_osm->buffer);
    }

    xxx2_osm->window = window;
    xxx2_osm_apply_current_ll(&xxx2_osm->buffer);
    osm_screen_o();

    if (!hasCleaned)
    {
        hasCleaned = true;
        printf("OSMDroid: vendor: %s\n", glGetString_p(GL_VENDOR));
        printf("OSMDroid: renderer: %s\n", glGetString_p(GL_RENDERER));
        osm_clean_color();
        ANativeWindow_unlockAndPost(xxx2_osm->nativeSurface);
    }
}

void *xxx2OsmCreateContext(void *contextSrc) {
    printf("OSMDroid: generating context\n");
    void *ctx = OSMesaCreateContext_p(OSMESA_RGBA, contextSrc);
    printf("OSMDroid: context=%p\n", ctx);
    return ctx;
}

void xxx2OsmSwapInterval(int interval) {
    if (xxx2_osm->nativeSurface != NULL)
        setNativeWindowSwapInterval(xxx2_osm->nativeSurface, interval);
}

int xxx2OsmInit() {
    if (pojav_environ->bridge_config != BRIDGE_TBL_XXX2)
        return 0;

    xxx2_osm = malloc(sizeof(struct xxx2_osm_render_window_t));
    if (!xxx2_osm) {
        fprintf(stderr, "Failed to allocate memory for xxx2_osm\n");
        return -1;
    }
    memset(xxx2_osm, 0, sizeof(struct xxx2_osm_render_window_t));

    return 0;
}