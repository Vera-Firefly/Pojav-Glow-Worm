//
// Created by Vera-Firefly on 27.01.2025.
//
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <EGL/egl.h>
#include "ctxbridges/renderer_config.h"

void *abuffer;
void *gbuffer;
void *mbuffer;

EGLConfig config;
struct PotatoBridge potatoBridge;

int InitialFrameBuffer() {
    if (getenv("OSM_INITIAL_FRAMEBUFFER") != NULL) return 1;
    return 0;
}
