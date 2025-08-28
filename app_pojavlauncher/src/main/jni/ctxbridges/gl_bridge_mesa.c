//
// Created by Vera-Firefly on 2025/8/28.
//

#define _GNU_SOURCE
#include <stdlib.h>
#include <stdio.h>
#include <string.h>


#include <GL/gl.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <android/native_window_jni.h>
#include <android/hardware_buffer.h>

#include "egl_loader.h"
#include "gl_bridge_mesa.h"

#define LOGI(fmt,...) fprintf(stdout, "[MESA_BRIDGE] " fmt "\n", ##__VA_ARGS__)
#define LOGE(fmt,...) fprintf(stderr, "[MESA_BRIDGE] " fmt "\n", ##__VA_ARGS__)
#define LOGW(fmt,...) fprintf(stderr, "[MESA_BRIDGE] " fmt "\n", ##__VA_ARGS__)

static EGLDisplay g_display = EGL_NO_DISPLAY;

static PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC p_eglGetNativeClientBufferANDROID = NULL;
static PFNEGLCREATEIMAGEKHRPROC p_eglCreateImageKHR = NULL;
static PFNEGLDESTROYIMAGEKHRPROC p_eglDestroyImageKHR = NULL;
static PFNGLEGLIMAGETARGETTEXTURE2DOESPROC p_glEGLImageTargetTexture2DOES = NULL;

static void setup_ext_procs_once(void)
{
    if (p_eglGetNativeClientBufferANDROID)
    {
        LOGI("setup_ext_procs_once OK");
        return;
    }
    p_eglGetNativeClientBufferANDROID = (PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC)eglGetProcAddress_p("eglGetNativeClientBufferANDROID");
    p_eglCreateImageKHR = (PFNEGLCREATEIMAGEKHRPROC)eglGetProcAddress_p("eglCreateImageKHR");
    p_eglDestroyImageKHR = (PFNEGLDESTROYIMAGEKHRPROC)eglGetProcAddress_p("eglDestroyImageKHR");
    p_glEGLImageTargetTexture2DOES = (PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)eglGetProcAddress_p("glEGLImageTargetTexture2DOES");
}

/* get display prefer surfaceless fallback default */
static EGLDisplay get_display_fallback(void)
{
    PFNEGLGETPLATFORMDISPLAYEXTPROC getPlat = (PFNEGLGETPLATFORMDISPLAYEXTPROC)eglGetProcAddress_p("eglGetPlatformDisplayEXT");
#ifdef EGL_PLATFORM_SURFACELESS_MESA
    if (getPlat) {
        EGLDisplay d = getPlat(EGL_PLATFORM_SURFACELESS_MESA, EGL_DEFAULT_DISPLAY, NULL);
        if (d != EGL_NO_DISPLAY) return d;
    }
#endif
    return eglGetDisplay_p(EGL_DEFAULT_DISPLAY);
}

bool mesa_gl_init()
{
    if (g_display != EGL_NO_DISPLAY) return true;

    g_display = get_display_fallback();
    if (g_display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return false;
    }
    if (!eglInitialize_p(g_display, NULL, NULL)) {
        LOGE("eglInitialize failed: 0x%04x", eglGetError_p());
        return false;
    }

    if (!eglBindAPI_p(EGL_OPENGL_ES_API)) {
        LOGI("eglBindAPI failed (non-fatal): 0x%04x", eglGetError_p());
    }

    setup_ext_procs_once();
    LOGI("mesa_gl_init OK");
    return true;
}

void mesa_gl_deinit(void)
{
    if (g_display != EGL_NO_DISPLAY) {
        eglTerminate_p(g_display);
        g_display = EGL_NO_DISPLAY;
    }
}

/* choose config helper */
static int choose_config(EGLConfig *out_cfg, EGLint *out_native_vis)
{
    if (g_display == EGL_NO_DISPLAY) return -1;
    EGLConfig cfg;
    EGLint num = 0;
    const EGLint attrs[] = {
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 24,
            EGL_NONE
    };
    if (!eglChooseConfig_p(g_display, attrs, &cfg, 1, &num) || num == 0) {
        LOGW("No exact match for RGBA8+DEPTH24, trying fallback");

        /* 兼容低版本设备，降低要求 */
        const EGLint fallback_attrs[] = {
                EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                EGL_NONE
        };
        if (!eglChooseConfig_p(g_display, fallback_attrs, &cfg, 1, &num) || num == 0) {
            LOGE("mesa_gl_choose_config failed: no configs available");
            return -1;
        }
    }
    if (out_cfg) *out_cfg = cfg;
    if (out_native_vis) *out_native_vis = eglGetConfigAttrib_p(g_display, cfg, EGL_NATIVE_VISUAL_ID, out_native_vis);

    {
        EGLBoolean bindResult;
        bindResult = eglBindAPI_p(EGL_OPENGL_ES_API);
        if (!bindResult) printf("EGLBridge: bind failed: %p\n", eglGetError_p());
    }

    return 0;
}

/* create context (no surface) */
EGLContext mesa_gl_create_context(EGLContext share_ctx, int gles_version, EGLConfig *out_cfg, EGLint *out_vis)
{
    if (g_display == EGL_NO_DISPLAY) return EGL_NO_CONTEXT;
    EGLConfig cfg;
    if (choose_config(&cfg, &out_vis) != 0) {
        LOGE("choose_config failed");
        return EGL_NO_CONTEXT;
    }
    if (out_cfg) *out_cfg = cfg;
    EGLint ctx_attribs[] = { EGL_CONTEXT_CLIENT_VERSION, (gles_version >= 3) ? 3 : 2, EGL_NONE };
    EGLContext ctx = eglCreateContext_p(g_display, cfg, share_ctx ? share_ctx : EGL_NO_CONTEXT, ctx_attribs);
    if (ctx == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed: 0x%04x", eglGetError_p());
    }
    return ctx;
}

void mesa_gl_destroy_context(EGLContext ctx)
{
    if (g_display == EGL_NO_DISPLAY || ctx == EGL_NO_CONTEXT) return;
    eglDestroyContext_p(g_display, ctx);
}

/* try make current without surface; fallback to 1x1 pbuffer */
int mesa_gl_make_current_nosurface(EGLContext ctx, EGLConfig cfg, EGLSurface *out_tmp_pbuffer)
{
    if (eglMakeCurrent_p(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, ctx)) {
        if (out_tmp_pbuffer) *out_tmp_pbuffer = EGL_NO_SURFACE;
        return 0;
    }
    const EGLint pbattrs[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
    EGLSurface pb = eglCreatePbufferSurface_p(g_display, cfg, pbattrs);
    if (pb == EGL_NO_SURFACE) {
        LOGE("eglCreatePbufferSurface failed: 0x%04x", eglGetError_p());
        return -1;
    }
    if (!eglMakeCurrent_p(g_display, pb, pb, ctx)) {
        LOGE("eglMakeCurrent(pbuffer) failed: 0x%04x", eglGetError_p());
        eglDestroySurface_p(g_display, pb);
        return -1;
    }
    if (out_tmp_pbuffer) *out_tmp_pbuffer = pb;
    return 0;
}

void mesa_gl_release_tmp_pbuffer(EGLSurface tmp_pb)
{
    if (tmp_pb != EGL_NO_SURFACE && g_display != EGL_NO_DISPLAY) {
        eglMakeCurrent_p(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface_p(g_display, tmp_pb);
    }
}

/* ---------- AHB-FBO helpers (context must be current) ---------- */

mesa_ahb_fbo* mesa_gl_create_ahb_fbo(int width, int height)
{
    if (g_display == EGL_NO_DISPLAY) { LOGE("display not init"); return NULL; }
    if (!p_eglGetNativeClientBufferANDROID || !p_eglCreateImageKHR || !p_glEGLImageTargetTexture2DOES) {
        LOGE("required EGL/GL extensions missing");
        return NULL;
    }

    mesa_ahb_fbo* o = (mesa_ahb_fbo*) calloc(1, sizeof(mesa_ahb_fbo));
    if (!o) return NULL;
    o->width = width; o->height = height; o->own_ahb = 1;

    AHardwareBuffer_Desc desc = {0};
    desc.width = width; desc.height = height; desc.layers = 1;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_FRAMEBUFFER | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE | AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN;

    if (AHardwareBuffer_allocate(&desc, &o->ahb) != 0) {
        LOGE("AHardwareBuffer_allocate failed");
        free(o); return NULL;
    }

    EGLClientBuffer client = p_eglGetNativeClientBufferANDROID(o->ahb);
    if (!client) { LOGE("eglGetNativeClientBufferANDROID returned NULL"); AHardwareBuffer_release(o->ahb); free(o); return NULL; }

    o->image = p_eglCreateImageKHR(g_display, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, client, NULL);
    if (o->image == EGL_NO_IMAGE_KHR) { LOGE("eglCreateImageKHR failed: 0x%04x", eglGetError_p()); AHardwareBuffer_release(o->ahb); free(o); return NULL; }

    glGenTextures(1, &o->tex);
    glBindTexture(GL_TEXTURE_2D, o->tex);
    p_glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, (GLeglImageOES)o->image);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

    glGenFramebuffers(1, &o->fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, o->fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, o->tex, 0);

    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        LOGE("glCheckFramebufferStatus failed: 0x%x", status);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDeleteFramebuffers(1, &o->fbo);
        glDeleteTextures(1, &o->tex);
        p_eglDestroyImageKHR(g_display, o->image);
        AHardwareBuffer_release(o->ahb);
        free(o); return NULL;
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);

    return o;
}

mesa_ahb_fbo* mesa_gl_wrap_ahb_as_fbo(AHardwareBuffer *ahb, int width, int height)
{
    if (!ahb) return NULL;
    if (!p_eglGetNativeClientBufferANDROID || !p_eglCreateImageKHR || !p_glEGLImageTargetTexture2DOES) {
        LOGE("required EGL/GL extensions missing");
        return NULL;
    }
    mesa_ahb_fbo *o = malloc(sizeof(mesa_ahb_fbo));
    memset(o, 0, sizeof(mesa_ahb_fbo));
    if (!o) return NULL;
    o->ahb = ahb;
    o->width = width; o->height = height;
    o->own_ahb = 0;

    EGLClientBuffer client = p_eglGetNativeClientBufferANDROID(ahb);
    if (!client) { LOGE("eglGetNativeClientBufferANDROID returned NULL"); free(o); return NULL; }

    o->image = p_eglCreateImageKHR(g_display, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, client, NULL);
    if (o->image == EGL_NO_IMAGE_KHR) { LOGE("eglCreateImageKHR failed: 0x%04x", eglGetError_p()); free(o); return NULL; }

    glGenTextures(1, &o->tex);
    glBindTexture(GL_TEXTURE_2D, o->tex);
    p_glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, (GLeglImageOES)o->image);

    glGenFramebuffers(1, &o->fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, o->fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, o->tex, 0);

    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        LOGE("glCheckFramebufferStatus failed: 0x%x", status);
        glDeleteFramebuffers(1, &o->fbo);
        glDeleteTextures(1, &o->tex);
        p_eglDestroyImageKHR(g_display, o->image);
        free(o); return NULL;
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
    return o;
}

void mesa_gl_destroy_ahb_fbo(mesa_ahb_fbo *o)
{
    if (!o) return;
    if (o->fbo) glDeleteFramebuffers(1, &o->fbo);
    if (o->tex) glDeleteTextures(1, &o->tex);
    if (o->image) p_eglDestroyImageKHR(g_display, o->image);
    if (o->ahb && o->own_ahb) AHardwareBuffer_release(o->ahb);
    free(o);
}

/* preferred readback: lock AHB and memcpy */
int mesa_gl_readback_ahb(AHardwareBuffer* ahb, int width, int height, void* out, size_t out_size)
{
    if (!ahb || !out) return -1;
    ARect rect = {0,0,width,height};
    void* ptr = NULL;
    int rc = AHardwareBuffer_lock(ahb, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, -1, &rect, &ptr);
    if (rc != 0) { LOGE("AHardwareBuffer_lock failed: %d", rc); return -1; }
    size_t need = (size_t)width * height * 4;
    if (out_size < need) { AHardwareBuffer_unlock(ahb, NULL); return -1; }
    memcpy(out, ptr, need);
    AHardwareBuffer_unlock(ahb, NULL);
    return 0;
}

/* post AHB to ANativeWindow (memcpy path) */
int mesa_gl_post_ahb_to_window(AHardwareBuffer* ahb, int width, int height, ANativeWindow* win)
{
    if (!ahb || !win) return -1;
    /* Caller should ensure synchronization (glFinish() or fences). For safety call glFinish() */
    glFinish();

    void* src = NULL;
    ARect rect = {0,0,width,height};
    int rc = AHardwareBuffer_lock(ahb, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, -1, &rect, &src);
    if (rc != 0) { LOGE("AHardwareBuffer_lock failed: %d", rc); return -1; }

    ANativeWindow_Buffer buf;
    if (ANativeWindow_lock(win, &buf, NULL) != 0) {
        LOGE("ANativeWindow_lock failed");
        AHardwareBuffer_unlock(ahb, NULL);
        return -1;
    }

    int src_stride = width * 4;
    int dst_stride = buf.stride * 4;
    uint8_t* s = (uint8_t*)src;
    uint8_t* d = (uint8_t*)buf.bits;
    int copy_w = width * 4;
    int copy_h = height;
    if (copy_h > buf.height) copy_h = buf.height;
    for (int y = 0; y < copy_h; ++y) {
        memcpy(d + y * dst_stride, s + y * src_stride, copy_w);
    }

    ANativeWindow_unlockAndPost(win);
    AHardwareBuffer_unlock(ahb, NULL);
    return 0;
}

int mesa_gl_post_fbo_to_window(mesa_ahb_fbo* obj, ANativeWindow* win)
{
    if (!obj) return -1;
    /* ensure GPU finished rendering into this FBO */
    glFinish();
    return mesa_gl_post_ahb_to_window(obj->ahb, obj->width, obj->height, win);
}
