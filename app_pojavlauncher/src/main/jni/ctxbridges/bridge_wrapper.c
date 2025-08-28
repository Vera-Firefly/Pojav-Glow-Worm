//
// Created by Vera-Firefly on 2025/8/28.
//

#define _GNU_SOURCE
#include "bridge_wrapper.h"
#include "gl_bridge_mesa.h"
#include "egl_loader.h"
#include "environ/environ.h"

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <GL/gl.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>

#define LOGI(fmt, ...) fprintf(stdout, "[BRIDGE_WRAPPER] " fmt "\n", ##__VA_ARGS__)
#define LOGE(fmt, ...) fprintf(stderr, "[BRIDGE_WRAPPER] " fmt "\n", ##__VA_ARGS__)

/* 当前 bundle */
static bridge_wrapper_t *g_current_bundle = NULL;

/* 保存来自 Java 层的 ANativeWindow（bridge_setup_window() 会把它绑定到 main bundle） */
static ANativeWindow *g_pending_native_window = NULL;

/* ============ 外部辅助函数 ============ */

void bridge_set_native_window(ANativeWindow *win)
{
    /* ANativeWindow_acquire should be called when assigned to a bundle */
    g_pending_native_window = win;
}

/* ============ bridge_tbl ============ */

/* 初始化 mesa_gl */
bool bridge_init()
{
    dlsym_EGL();
    if (!mesa_gl_init()) {
        LOGE("mesa_gl_init failed");
        return false;
    }
    LOGI("bridge_init OK");
    return true;
}

/* 创建 window，返回 p */
bridge_wrapper_t* bridge_init_context(bridge_wrapper_t * share)
{
    EGLContext share_ctx = EGL_NO_CONTEXT;
    EGLConfig cfg = EGL_NO_CONFIG_KHR;
    EGLint native_vis = 0;
    if (share) share_ctx = share->ctx;

    EGLContext ctx = mesa_gl_create_context(share_ctx, 3, &cfg, &native_vis);
    if (ctx == EGL_NO_CONTEXT) {
        LOGE("mesa_gl_create_context failed");
        return NULL;
    }

    bridge_wrapper_t* bundle = malloc(sizeof(bridge_wrapper_t));
    memset(bundle, 0, sizeof(bridge_wrapper_t));
    if (!bundle) {
        LOGE("calloc failed");
        mesa_gl_destroy_context(ctx);
        return NULL;
    }

    bundle->ctx = ctx;
    bundle->cfg = cfg;
    bundle->format = native_vis;
    bundle->tmp_pbuffer = EGL_NO_SURFACE;

    LOGI("bridge_init_context created bundle %p (ctx %p)", (void*)bundle, (void*)ctx);
    return bundle;
}

void bridge_swap_surface(bridge_wrapper_t* bundle)
{
    if (bundle->nativeSurface != NULL)
        ANativeWindow_release(bundle->nativeSurface);

    if (bundle->tmp_pbuffer != EGL_NO_SURFACE)
        eglDestroySurface_p(bundle->ctx, bundle->tmp_pbuffer);

    if (bundle->newNativeSurface != NULL)
    {
        LOGI("Switching to new native surface");
        bundle->nativeSurface = bundle->newNativeSurface;
        bundle->newNativeSurface = NULL;
        ANativeWindow_acquire(bundle->nativeSurface);
        ANativeWindow_setBuffersGeometry(bundle->nativeSurface, 0, 0, bundle->format);
        // bundle->tmp_pbuffer = eglCreateWindowSurface_p(bundle->ctx, bundle->cfg, bundle->window, NULL);
    } else {
        LOGI("No new native surface, switching to 1x1 pbuffer");
        bundle->nativeSurface = NULL;
        const EGLint pbuffer_attrs[] = {EGL_WIDTH, 1 , EGL_HEIGHT, 1, EGL_NONE};
        bundle->tmp_pbuffer = eglCreatePbufferSurface_p(bundle->ctx, bundle->cfg, pbuffer_attrs);
    }
}

/* 指定 bundle 的 context */
void bridge_make_current(bridge_wrapper_t* bundle)
{
    if (!bundle) {
        /* 解绑 */
        if (g_current_bundle) {
            mesa_gl_release_tmp_pbuffer(g_current_bundle->tmp_pbuffer);
            g_current_bundle->tmp_pbuffer = EGL_NO_SURFACE;
        }
        if (eglMakeCurrent_p && g_current_bundle) {
            eglMakeCurrent_p(mesa_gl_create_context(EGL_NO_CONTEXT, 3, NULL, &bundle->format) /* noop? */, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        }
        g_current_bundle = NULL;
        LOGI("bridge_make_current: unset current");
        return;
    }

    if (pojav_environ->mainWindowBundle == NULL)
    {
        pojav_environ->mainWindowBundle = (basic_render_window_t *)bundle;
        LOGI("Main window bundle is now %p", pojav_environ->mainWindowBundle);
        pojav_environ->mainWindowBundle->newNativeSurface = pojav_environ->pojavWindow;
    }

    if (bundle->tmp_pbuffer == EGL_NO_SURFACE)
        bridge_swap_surface(bundle);

    if (g_current_bundle && g_current_bundle != bundle) {
        mesa_gl_release_tmp_pbuffer(g_current_bundle->tmp_pbuffer);
        g_current_bundle->tmp_pbuffer = EGL_NO_SURFACE;
    }

    /* 让 mesa_gl 尝试在当前线程上 make current（优先 EGL_NO_SURFACE; 若回退则创建 pbuffer） */
    EGLSurface tmp_pb = EGL_NO_SURFACE;
    int ok = mesa_gl_make_current_nosurface(bundle->ctx, bundle->cfg, &tmp_pb);
    if (ok != 0) {
        LOGE("mesa_gl_make_current_nosurface failed for ctx %p", (void*)bundle->ctx);
        g_current_bundle = NULL;
        return;
    }
    g_current_bundle = bundle;
    bundle->tmp_pbuffer = tmp_pb;
    LOGI("bridge_make_current: bundle %p is now current (tmp_pb %p)", (void*)bundle, (void*)bundle->tmp_pbuffer);
}

/* br_get_current returns current bundle */
bridge_wrapper_t* bridge_get_current(void)
{
    return g_current_bundle;
}

/* 将全局 pending native window 绑定到 bundle */
void bridge_setup_window(void)
{
    if (!g_current_bundle) {
        LOGI("bridge_setup_window: no current bundle, nothing to setup");
        return;
    }

    if (!g_pending_native_window) {
        LOGI("bridge_setup_window: no pending native window provided");
        return;
    }

    /* 如果当前 bundle 已经有 window 且不同则 release 原 window */
    if (g_current_bundle->nativeSurface && g_current_bundle->nativeSurface != g_pending_native_window) {
        ANativeWindow_release(g_current_bundle->nativeSurface);
        g_current_bundle->nativeSurface = NULL;
    }

    /* Acquire and assign */
    ANativeWindow_acquire(g_pending_native_window);
    g_current_bundle->nativeSurface = g_pending_native_window;

    /* optionally set buffers geometry based on cfg */
    EGLint native_format = 0;
    if (!g_current_bundle->ctx) {
        g_current_bundle->ctx = mesa_gl_create_context(EGL_NO_CONTEXT, 3, NULL, &native_format);
    }

    eglGetConfigAttrib_p(
            g_current_bundle->ctx,
            g_current_bundle->cfg,
            EGL_NATIVE_VISUAL_ID,
            &native_format
    );

    /* ANativeWindow_setBuffersGeometry expects format as pixel format, but native_format from EGL_NATIVE_VISUAL_ID should be compatible */
    ANativeWindow_setBuffersGeometry(g_current_bundle->nativeSurface, 0, 0, native_format);

    LOGI("bridge_setup_window: bound ANativeWindow %p to bundle %p", (void*)g_pending_native_window, (void*)g_current_bundle);
    /* clear pending */
    g_pending_native_window = NULL;
}

/* br_swap_buffers:
 * - 如果 bundle 有 attached AHB-FBO 并有 window，则 post_fbo_to_window
 * - 否则如果 bundle->tmp_pbuffer == EGL_NO_SURFACE 和 bundle 存在 window-surface, 尝试 eglSwapBuffers
 * - 否则 noop
 */
void bridge_swap_buffers(void)
{
    if (!g_current_bundle) {
        LOGI("bridge_swap_buffers: no current bundle");
        return;
    }

    bridge_wrapper_t *b = g_current_bundle;

    if (b->ahb_fbo && b->nativeSurface) {
        /* ensure rendering finished (caller may call glFinish or we call here) */
        glFinish();
        if (mesa_gl_post_fbo_to_window(b->ahb_fbo, b->nativeSurface) != 0) {
            LOGE("bridge_swap_buffers: post_fbo_to_window failed");
        }
        return;
    }

    /* fallback: if we have a real window-surface (not typical in EGL_NO_SURFACE flow), try eglSwapBuffers */
    if (b->tmp_pbuffer == EGL_NO_SURFACE && b->nativeSurface) {
        /* try eglGetCurrentSurface to see if have valid draw surface */
        EGLSurface current = eglGetCurrentSurface_p(EGL_DRAW);
        if (current != EGL_NO_SURFACE) {
            if (!eglSwapBuffers_p(b->ctx, current)) {
                LOGE("bridge_swap_buffers: eglSwapBuffers failed");
            }
        }
    }
    /* otherwise nothing to do (rendering stays in AHB-FBO) */
}

/* br_swap_interval */
void bridge_swap_interval(int interval)
{
    /* If we have display, call eglSwapInterval on it; otherwise noop.
       We need to call mesa_gl_init to ensure g_display exists. */
    /* We wrap this by calling eglSwapInterval_p against display from mesa_gl internals. */
    /* There's no exported getter for display; simply call eglSwapInterval_p with EGL_NO_DISPLAY is invalid.
       For simplicity, call eglSwapInterval_p on current display via eglGetCurrentDisplay if available. */
    EGLDisplay disp = eglGetCurrentDisplay();
    if (disp != EGL_NO_DISPLAY) {
        eglSwapInterval_p(disp, interval);
    }
}

/* destroy context and free bundle */
void bridge_destroy_context(bridge_wrapper_t **bundle)
{
    if (!bundle || !*bundle) return;
    bridge_wrapper_t *b = *bundle;

    /* if current, unbind */
    if (g_current_bundle == b) {
        bridge_make_current(NULL);
    }

    /* destroy any attached ahb_fbo */
    if (b->ahb_fbo) {
        mesa_gl_destroy_ahb_fbo(b->ahb_fbo);
        b->ahb_fbo = NULL;
    }

    /* release window if held */
    if (b->nativeSurface) {
        ANativeWindow_release(b->nativeSurface);
        b->nativeSurface = NULL;
    }

    /* destroy EGL context */
    mesa_gl_destroy_context(b->ctx);

    free(b);
    *bundle = NULL;
    LOGI("bridge_destroy_context: bundle destroyed");
}
