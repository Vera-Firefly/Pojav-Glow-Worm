//
// Created by Vera-Firefly on 2025/8/28.
//

#ifndef POJAV_GLOW_WORM_BRIDGE_WRAPPER_H
#define POJAV_GLOW_WORM_BRIDGE_WRAPPER_H

#include <EGL/egl.h>
#include <android/native_window.h>
#include <stddef.h>

/* forward of mesa_ahb_fbo defined in mesa_gl_bridge.h */
typedef struct mesa_ahb_fbo mesa_ahb_fbo;

/* 简单的 basic_render_window_t wrapper
 *
 * 这与 bridge_tbl.h 使用的 basic_render_window_t* 兼容（指针类型一致）。
 * 上层通过 br_init_context()/br_make_current()/br_swap_buffers() 等调用。
 */
typedef struct bridge_wrapper_t {
    EGLContext ctx;           /* EGLContext created for this bundle */
    EGLConfig  cfg;           /* EGLConfig selected at creation time */
    EGLint format;
    EGLSurface tmp_pbuffer;   /* 临时 1x1 pbuffer（如果需要回退） */
    mesa_ahb_fbo *ahb_fbo;    /* 可选：如果 bridge 为渲染创建并管理 AHB-FBO，则挂在这里 */
    struct ANativeWindow *nativeSurface;    /* optional ANativeWindow（不持有强引用，需由 caller 管理生命周期） */
    int width;
    int height;
} bridge_wrapper_t;

/* ============ Bridge 外部 API ============ */

/* 上层（Java/NH）在 surfaceCreated / surfaceChanged 时调用，传入 ANativeWindow*；
 * 注意：此函数不会把 window 自动绑定到某个 bundle（我们在 br_setup_window 中处理）。
 * ANativeWindow_fromSurface 返回的 window 应在使用后 ANativeWindow_release。 */
void bridge_set_native_window(ANativeWindow *win);

/* 下面的函数名对应 bridge_tbl.h 里要赋值的全局函数指针 */
bool bridge_init(); /* 0 success */
bridge_wrapper_t* bridge_init_context(bridge_wrapper_t* share);
void bridge_make_current(bridge_wrapper_t * bundle);
bridge_wrapper_t* bridge_get_current(void);
void bridge_swap_buffers(void);
void bridge_setup_window(void);
void bridge_swap_interval(int interval);
void bridge_destroy_context(bridge_wrapper_t **bundle);

#endif //POJAV_GLOW_WORM_BRIDGE_WRAPPER_H
