//
// Created by Vera-Firefly on 02.08.2024.
//

#ifndef POJAVLAUNCHER_SPARE_BRIDGE_H
#define POJAVLAUNCHER_SPARE_BRIDGE_H

#include <ctxbridges/common.h>
// #include <ctxbridges/spare_gl_bridge.h>
#include <ctxbridges/spare_osm_bridge.h>

typedef basic_render_window_t* (*spare_init_context_t)(basic_render_window_t* share);
typedef void (*spare_make_current_t)(basic_render_window_t* bundle);
typedef basic_render_window_t* (*spare_get_current_t)();

bool (*spare_init)() = NULL;
spare_init_context_t spare_init_context = NULL;
spare_make_current_t spare_make_current = NULL;
spare_get_current_t spare_get_current = NULL;
void (*spare_swap_buffers)() = NULL;
void (*spare_setup_window)() = NULL;
void (*spare_swap_interval)(int swapInterval) = NULL;


void spare_osm_bridge() {
    spare_init = spare_osm_init;
    spare_init_context = (spare_init_context_t) spare_osm_init_context;
    spare_make_current = (spare_make_current_t) spare_osm_make_current;
    spare_get_current = (spare_get_current_t) spare_osm_get_current;
    spare_swap_buffers = spare_osm_swap_buffers;
    spare_setup_window = spare_osm_setup_window;
    spare_swap_interval = spare_osm_swap_interval;
}

/*
void spare_gl_bridge() {
    spare_init = gl_init;
    spare_init_context = (spare_init_context_t) gl_init_context;
    spare_make_current = (spare_make_current_t) gl_make_current;
    spare_get_current = (spare_get_current_t) gl_get_current;
    spare_swap_buffers = gl_swap_buffers;
    spare_setup_window = gl_setup_window;
    spare_swap_interval = gl_swap_interval;
}
*/

#endif //POJAVLAUNCHER_SPARE_BRIDGE_H
