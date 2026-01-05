//
// Created by Vera-Firefly on 2.12.2023.
// Definitions specific to the renderer
//


#define RENDERER_GL4ES 1
#define RENDERER_VK_ZINK 2
#define RENDERER_VIRGL 3
#define RENDERER_VULKAN 4
#define RENDERER_MESA_EGL 5
#define RENDERER_VK_ZINK_XXX1 6
#define RENDERER_VK_ZINK_XXX2 7
#define RENDERER_VK_ZINK_XXX3 8
#define RENDERER_VK_ZINK_XXX4 9

#define BRIDGE_TBL_DEFAULT 0
#define BRIDGE_TBL_XXX1 1
#define BRIDGE_TBL_XXX2 2
#define BRIDGE_TBL_XXX3 3
#define BRIDGE_TBL_XXX4 4



#ifdef POTATOBRIDGE
#include <EGL/egl.h>

struct PotatoBridge {
    void* eglContext;    // EGLContext
    void* eglDisplay;    // EGLDisplay
    void* eglSurface;    // EGLSurface
    // void* eglSurfaceRead;
    // void* eglSurfaceDraw;
};

extern struct PotatoBridge potatoBridge;
extern EGLConfig config;

#endif // PotatoBridge


#ifdef INITIAL_FRAME_BUFFER

int InitialFrameBuffer();
extern void *gbuffer;

#endif


#ifdef OSM_CTX
#include <GL/osmesa.h>

void osm_make_current_l(OSMesaContext context, void *buffer, int width, int height);
void osm_make_current_ll(OSMesaContext context, int width, int height);
void osm_screen_o();
void osm_pixel_store(int32_t stride);
void osm_clean_color();

#endif
