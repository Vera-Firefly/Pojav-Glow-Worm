//
// Created by Vera-Firefly on 12.03.2025.
//

#include <android/native_window.h>
#include <malloc.h>
#include "osmesa_loader.h"

#define OSM_CTX
#define INITIAL_FRAME_BUFFER
#include "renderer_config.h"

void osm_make_current_l(OSMesaContext context, void *buffer, int width, int height) {
    OSMesaMakeCurrent_p(context, buffer, GL_UNSIGNED_BYTE, width, height);
}

void osm_make_current_ll(OSMesaContext context, int width, int height) {
    if (InitialFrameBuffer())
    {
        gbuffer = malloc(width * height * 4);
        OSMesaMakeCurrent_p(context, gbuffer, GL_UNSIGNED_BYTE, width, height);
    }
    else
    {
        OSMesaMakeCurrent_p(context, setbuffer, GL_UNSIGNED_BYTE, width, height);
    }
}

void osm_screen_o() {
    OSMesaPixelStore_p(OSMESA_Y_UP, 0);
}

void osm_pixel_store(int32_t stride) {
    OSMesaPixelStore_p(OSMESA_ROW_LENGTH, stride);
}

void osm_clean_color() {
    glClear_p(GL_COLOR_BUFFER_BIT);
    glClearColor_p(0.4f, 0.4f, 0.4f, 1.0f);
}
