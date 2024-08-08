//
// Created by Vera-Firefly on 02.08.2024.
//
#include <android/native_window.h>
#include <stdbool.h>
#ifndef POJAVLAUNCHER_SPARE_OSM_BRIDGE_H
#define POJAVLAUNCHER_SPARE_OSM_BRIDGE_H
#include "osmesa_loader.h"


typedef struct {
    char       state;
    struct ANativeWindow *nativeSurface;
    struct ANativeWindow *newNativeSurface;
    ANativeWindow_Buffer buffer;
    int32_t last_stride;
    bool disable_rendering;
    OSMesaContext context;
} spare_osm_render_window_t;

bool spare_osm_init();
spare_osm_render_window_t* spare_osm_get_current();
spare_osm_render_window_t* spare_osm_init_context(spare_osm_render_window_t* share);
void spare_osm_make_current(spare_osm_render_window_t* bundle);
void spare_osm_swap_buffers();
void spare_osm_setup_window();
void spare_osm_swap_interval(int swapInterval);

#endif //POJAVLAUNCHER_SPARE_OSM_BRIDGE_H
