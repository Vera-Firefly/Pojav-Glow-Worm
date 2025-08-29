//
// Created by Vera-Firefly on 2025/8/28.
//

#ifndef POJAV_GLOW_WORM_GL_BRIDGE_MESA_H
#define POJAV_GLOW_WORM_GL_BRIDGE_MESA_H

#include <EGL/egl.h>
#include <android/native_window.h>
#include <android/hardware_buffer.h>
#include <GLES2/gl2.h>
#include <stddef.h>
#include <EGL/eglext.h>

#ifdef __cplusplus
extern "C" {
#endif

/* 初始化 / 反初始化（调用前请先 dlsym_EGL()） */
bool mesa_gl_init();   /* return 0 on success */
void mesa_gl_deinit(void);

/* Context 管理 */
EGLContext mesa_gl_create_context(EGLContext share_ctx, int gles_version, EGLConfig *out_cfg, EGLint *out_vis);
void mesa_gl_destroy_context(EGLContext ctx);

/* make current 无 window（优先 surfaceless，否则 pbuffer 回退） */
int mesa_gl_make_current_nosurface(EGLContext ctx, EGLConfig cfg, EGLSurface *out_tmp_pbuffer);
void mesa_gl_release_tmp_pbuffer(EGLSurface tmp);

/* AHB-FBO 封装 */
typedef struct mesa_ahb_fbo {
    AHardwareBuffer *ahb;    /* AHB（可能由 bridge 分配或外部传入） */
    EGLImageKHR image;       /* EGLImage（若由 bridge 创建） */
    GLuint tex;              /* GL texture id（若由 bridge 创建） */
    GLuint fbo;              /* GL FBO id（若由 bridge 创建） */
    int width, height;
    int own_ahb;             /* 是否由此对象负责 release AHB */
} mesa_ahb_fbo;

/* 创建 AHB->EGLImage->GL texture->FBO（当前 context 必须已 current） */
mesa_ahb_fbo* mesa_gl_create_ahb_fbo(EGLContext ctx, int width, int height);

/* 如果你已有 AHardwareBuffer（比如 Mesa 分配的），将其包装为 mesa_ahb_fbo（不会重新 allocate） */
mesa_ahb_fbo* mesa_gl_wrap_ahb_as_fbo(EGLContext ctx , AHardwareBuffer *ahb, int width, int height);

/* 销毁 */
void mesa_gl_destroy_ahb_fbo(mesa_ahb_fbo *obj);

/* 把 AHB 内容 post 到 ANativeWindow（简单 memcpy 路径）；
   - 要求 GPU 渲染完成（调用方负责 glFinish 或使用 sync）
   - 返回 0 成功 */
int mesa_gl_post_ahb_to_window(AHardwareBuffer *ahb, int width, int height, ANativeWindow *win);

/* 直接把 mesa_ahb_fbo post 到 window（会调用上面的函数） */
int mesa_gl_post_fbo_to_window(mesa_ahb_fbo *obj, ANativeWindow *win);

/* 读回 AHB 像素到 out（使用 AHardwareBuffer_lock） */
int mesa_gl_readback_ahb(AHardwareBuffer *ahb, int width, int height, void *out, size_t out_size);

#ifdef __cplusplus
}
#endif

#endif //POJAV_GLOW_WORM_GL_BRIDGE_MESA_H
