//
// Created by Vera-Firefly on 21.12.2024.
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
#include "osm_bridge_xxx3.h"
#include "osmesa_loader.h"

#define OSM_CTX
#include "renderer_config.h"

static struct xxx3_osm_render_window_t *xxx3_osm;
static bool hasCleaned = false;
static bool hasSetNoRendererBuffer = false;
static char xxx3_no_render_buffer[4];
static const char* osm_LogTag = "[ XXX3 OSM Bridge ]";

void setNativeWindowSwapInterval(struct ANativeWindow* nativeWindow, int swapInterval);

void xxx3_osm_set_no_render_buffer(ANativeWindow_Buffer* buf) {
    buf->bits = &xxx3_no_render_buffer;
    buf->width = pojav_environ->savedWidth;
    buf->height = pojav_environ->savedHeight;
    buf->stride = 0;
}

bool xxx3OsmloadSymbols() {
    dlsym_OSMesa();
    return true;
}

void* xxx3OsmGetCurrentContext() {
    return xxx3_osm->context;
}

void* xxx3OsmCreateContext(void* contextSrc) {

    xxx3_osm = malloc(sizeof(struct xxx3_osm_render_window_t));
    if (!xxx3_osm)
    {
        printf("%s Failed to allocate memory for xxx3_osm\n", osm_LogTag);
        return NULL;
    }
    memset(xxx3_osm, 0, sizeof(struct xxx3_osm_render_window_t));

    printf("%s generating context\n", osm_LogTag);

    OSMesaContext osmesa_share = NULL;
    if (contextSrc != NULL) osmesa_share = contextSrc;

    OSMesaContext context = OSMesaCreateContext_p(OSMESA_RGBA, osmesa_share);
    if (context == NULL) {
        printf("%s OSMesaContext is Null!!!\n", osm_LogTag);
        return NULL;
    }

    xxx3_osm->context = context;
    printf("%s context = %p\n", osm_LogTag, context);

    return context;
}


void xxx3_osm_apply_current(ANativeWindow_Buffer* buf) {
    osm_make_current_l(xxx3_osm->context, buf->bits, buf->width, buf->height);
    if (buf->stride != xxx3_osm->last_stride)
        osm_pixel_store(buf->stride);
    xxx3_osm->last_stride = buf->stride;
}

void xxx3OsmMakeCurrent() {
    if (!hasCleaned)
    {
        printf("%s making current\n", osm_LogTag);
        xxx3_osm->nativeSurface = pojav_environ->pojavWindow;
        ANativeWindow_acquire(xxx3_osm->nativeSurface);
        ANativeWindow_setBuffersGeometry(xxx3_osm->nativeSurface, 0, 0, WINDOW_FORMAT_RGBX_8888);
        ANativeWindow_lock(xxx3_osm->nativeSurface, &xxx3_osm->buffer, NULL);
    }

    if (!hasSetNoRendererBuffer)
    {
        hasSetNoRendererBuffer = true;
        xxx3_osm_set_no_render_buffer(&xxx3_osm->buffer);
    }

    xxx3_osm_apply_current(&xxx3_osm->buffer);
    osm_screen_o();

    if (!hasCleaned)
    {
        hasCleaned = true;
        printf("%s vendor: %s\n", osm_LogTag, glGetString_p(GL_VENDOR));
        printf("%s renderer: %s\n", osm_LogTag, glGetString_p(GL_RENDERER));
        osm_clean_color();
        ANativeWindow_unlockAndPost(xxx3_osm->nativeSurface);
    }
}

void xxx3OsmSwapBuffers() {
    ANativeWindow_lock(xxx3_osm->nativeSurface, &xxx3_osm->buffer, NULL);
    xxx3_osm_apply_current(&xxx3_osm->buffer);
    glFinish_p();
    ANativeWindow_unlockAndPost(xxx3_osm->nativeSurface);
}

void xxx3OsmSwapInterval(int interval) {
    if (!getenv("ALLOW_VSYNC_IN_ZINK")) return;

    if (xxx3_osm->nativeSurface != NULL)
        setNativeWindowSwapInterval(xxx3_osm->nativeSurface, interval);
}
