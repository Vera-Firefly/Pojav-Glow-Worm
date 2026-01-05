//
// Created by Vera-Firefly on 21.12.2024.
//

#ifndef OSM_BRIDGE_XXX3_H
#define OSM_BRIDGE_XXX3_H

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include "osmesa_loader.h"

struct xxx3_osm_render_window_t {
    struct ANativeWindow *nativeSurface;
    ANativeWindow_Buffer buffer;
    OSMesaContext context;
    int32_t last_stride;
    void* window;
};

bool xxx3OsmloadSymbols();
int xxx3OsmInit();
void* xxx3OsmCreateContext(void* contextSrc);
void* xxx3OsmGetCurrentContext();
void xxx3OsmMakeCurrent();
void xxx3OsmSwapBuffers();
void xxx3OsmSwapInterval(int interval);

#endif //OSM_BRIDGE_XXX3_H
