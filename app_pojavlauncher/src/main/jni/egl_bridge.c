//
// Modifile by Vera-Firefly on 17.09.2023.
//
#include <jni.h>
#include <assert.h>
#include <dlfcn.h>

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <unistd.h>

#include <EGL/egl.h>
#include <GL/osmesa.h>
#include "ctxbridges/osmesa_loader.h"
#include "driver_helper/nsbypass.h"

#ifdef GLES_TEST
#include <GLES2/gl2.h>
#endif

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/rect.h>
#include <string.h>
#include <environ/environ.h>
#include <android/dlext.h>
#include "utils.h"
#include "ctxbridges/gl_bridge.h"

#define GLFW_CLIENT_API 0x22001
/* Consider GLFW_NO_API as Vulkan API */
#define GLFW_NO_API 0
#define GLFW_OPENGL_API 0x30001
// region OSMESA internals

// This means that the function is an external API and that it will be used
#define EXTERNAL_API __attribute__((used))
// This means that you are forced to have this function/variable for ABI compatibility
#define ABI_COMPAT __attribute__((unused))

struct pipe_screen;

//only get what we need to access/modify
struct st_manager
{
    struct pipe_screen *screen;
};
struct st_context_iface
{
    void *st_context_private;
};
struct zink_device_info
{
    bool have_EXT_conditional_rendering;
    bool have_EXT_transform_feedback;
};
struct zink_screen
{
    struct zink_device_info info;
};

enum st_attachment_type {
    ST_ATTACHMENT_FRONT_LEFT,
    ST_ATTACHMENT_BACK_LEFT,
    ST_ATTACHMENT_FRONT_RIGHT,
    ST_ATTACHMENT_BACK_RIGHT,
    ST_ATTACHMENT_DEPTH_STENCIL,
    ST_ATTACHMENT_ACCUM,
    ST_ATTACHMENT_SAMPLE,

    ST_ATTACHMENT_COUNT,
    ST_ATTACHMENT_INVALID = -1
};
enum pipe_format {
    PIPE_FORMAT_NONE,
    PIPE_FORMAT_B8G8R8A8_UNORM,
    PIPE_FORMAT_B8G8R8X8_UNORM,
    PIPE_FORMAT_A8R8G8B8_UNORM,
    PIPE_FORMAT_X8R8G8B8_UNORM,
    PIPE_FORMAT_B5G5R5A1_UNORM,
    PIPE_FORMAT_R4G4B4A4_UNORM,
    PIPE_FORMAT_B4G4R4A4_UNORM,
    PIPE_FORMAT_R5G6B5_UNORM,
    PIPE_FORMAT_B5G6R5_UNORM,
    PIPE_FORMAT_R10G10B10A2_UNORM,
    PIPE_FORMAT_L8_UNORM,    /**< ubyte luminance */
    PIPE_FORMAT_A8_UNORM,    /**< ubyte alpha */
    PIPE_FORMAT_I8_UNORM,    /**< ubyte intensity */
    PIPE_FORMAT_L8A8_UNORM,  /**< ubyte alpha, luminance */
    PIPE_FORMAT_L16_UNORM,   /**< ushort luminance */
    PIPE_FORMAT_UYVY,
    PIPE_FORMAT_YUYV,
    PIPE_FORMAT_Z16_UNORM,
    PIPE_FORMAT_Z16_UNORM_S8_UINT,
    PIPE_FORMAT_Z32_UNORM,
    PIPE_FORMAT_Z32_FLOAT,
    PIPE_FORMAT_Z24_UNORM_S8_UINT,
    PIPE_FORMAT_S8_UINT_Z24_UNORM,
    PIPE_FORMAT_Z24X8_UNORM,
    PIPE_FORMAT_X8Z24_UNORM,
    PIPE_FORMAT_S8_UINT,     /**< ubyte stencil */
    PIPE_FORMAT_R64_FLOAT,
    PIPE_FORMAT_R64G64_FLOAT,
    PIPE_FORMAT_R64G64B64_FLOAT,
    PIPE_FORMAT_R64G64B64A64_FLOAT,
    PIPE_FORMAT_R32_FLOAT,
    PIPE_FORMAT_R32G32_FLOAT,
    PIPE_FORMAT_R32G32B32_FLOAT,
    PIPE_FORMAT_R32G32B32A32_FLOAT,
    PIPE_FORMAT_R32_UNORM,
    PIPE_FORMAT_R32G32_UNORM,
    PIPE_FORMAT_R32G32B32_UNORM,
    PIPE_FORMAT_R32G32B32A32_UNORM,
    PIPE_FORMAT_R32_USCALED,
    PIPE_FORMAT_R32G32_USCALED,
    PIPE_FORMAT_R32G32B32_USCALED,
    PIPE_FORMAT_R32G32B32A32_USCALED,
    PIPE_FORMAT_R32_SNORM,
    PIPE_FORMAT_R32G32_SNORM,
    PIPE_FORMAT_R32G32B32_SNORM,
    PIPE_FORMAT_R32G32B32A32_SNORM,
    PIPE_FORMAT_R32_SSCALED,
    PIPE_FORMAT_R32G32_SSCALED,
    PIPE_FORMAT_R32G32B32_SSCALED,
    PIPE_FORMAT_R32G32B32A32_SSCALED,
    PIPE_FORMAT_R16_UNORM,
    PIPE_FORMAT_R16G16_UNORM,
    PIPE_FORMAT_R16G16B16_UNORM,
    PIPE_FORMAT_R16G16B16A16_UNORM,
    PIPE_FORMAT_R16_USCALED,
    PIPE_FORMAT_R16G16_USCALED,
    PIPE_FORMAT_R16G16B16_USCALED,
    PIPE_FORMAT_R16G16B16A16_USCALED,
    PIPE_FORMAT_R16_SNORM,
    PIPE_FORMAT_R16G16_SNORM,
    PIPE_FORMAT_R16G16B16_SNORM,
    PIPE_FORMAT_R16G16B16A16_SNORM,
    PIPE_FORMAT_R16_SSCALED,
    PIPE_FORMAT_R16G16_SSCALED,
    PIPE_FORMAT_R16G16B16_SSCALED,
    PIPE_FORMAT_R16G16B16A16_SSCALED,
    PIPE_FORMAT_R8_UNORM,
    PIPE_FORMAT_R8G8_UNORM,
    PIPE_FORMAT_R8G8B8_UNORM,
    PIPE_FORMAT_B8G8R8_UNORM,
    PIPE_FORMAT_R8G8B8A8_UNORM,
    PIPE_FORMAT_X8B8G8R8_UNORM,
    PIPE_FORMAT_R8_USCALED,
    PIPE_FORMAT_R8G8_USCALED,
    PIPE_FORMAT_R8G8B8_USCALED,
    PIPE_FORMAT_B8G8R8_USCALED,
    PIPE_FORMAT_R8G8B8A8_USCALED,
    PIPE_FORMAT_B8G8R8A8_USCALED,
    PIPE_FORMAT_A8B8G8R8_USCALED,
    PIPE_FORMAT_R8_SNORM,
    PIPE_FORMAT_R8G8_SNORM,
    PIPE_FORMAT_R8G8B8_SNORM,
    PIPE_FORMAT_B8G8R8_SNORM,
    PIPE_FORMAT_R8G8B8A8_SNORM,
    PIPE_FORMAT_B8G8R8A8_SNORM,
    PIPE_FORMAT_R8_SSCALED,
    PIPE_FORMAT_R8G8_SSCALED,
    PIPE_FORMAT_R8G8B8_SSCALED,
    PIPE_FORMAT_B8G8R8_SSCALED,
    PIPE_FORMAT_R8G8B8A8_SSCALED,
    PIPE_FORMAT_B8G8R8A8_SSCALED,
    PIPE_FORMAT_A8B8G8R8_SSCALED,
    PIPE_FORMAT_R32_FIXED,
    PIPE_FORMAT_R32G32_FIXED,
    PIPE_FORMAT_R32G32B32_FIXED,
    PIPE_FORMAT_R32G32B32A32_FIXED,
    PIPE_FORMAT_R16_FLOAT,
    PIPE_FORMAT_R16G16_FLOAT,
    PIPE_FORMAT_R16G16B16_FLOAT,
    PIPE_FORMAT_R16G16B16A16_FLOAT,

    /* sRGB formats */
    PIPE_FORMAT_L8_SRGB,
    PIPE_FORMAT_R8_SRGB,
    PIPE_FORMAT_L8A8_SRGB,
    PIPE_FORMAT_R8G8_SRGB,
    PIPE_FORMAT_R8G8B8_SRGB,
    PIPE_FORMAT_B8G8R8_SRGB,
    PIPE_FORMAT_A8B8G8R8_SRGB,
    PIPE_FORMAT_X8B8G8R8_SRGB,
    PIPE_FORMAT_B8G8R8A8_SRGB,
    PIPE_FORMAT_B8G8R8X8_SRGB,
    PIPE_FORMAT_A8R8G8B8_SRGB,
    PIPE_FORMAT_X8R8G8B8_SRGB,
    PIPE_FORMAT_R8G8B8A8_SRGB,

    /* compressed formats */
    PIPE_FORMAT_DXT1_RGB,
    PIPE_FORMAT_DXT1_RGBA,
    PIPE_FORMAT_DXT3_RGBA,
    PIPE_FORMAT_DXT5_RGBA,

    /* sRGB, compressed */
    PIPE_FORMAT_DXT1_SRGB,
    PIPE_FORMAT_DXT1_SRGBA,
    PIPE_FORMAT_DXT3_SRGBA,
    PIPE_FORMAT_DXT5_SRGBA,

    /* rgtc compressed */
    PIPE_FORMAT_RGTC1_UNORM,
    PIPE_FORMAT_RGTC1_SNORM,
    PIPE_FORMAT_RGTC2_UNORM,
    PIPE_FORMAT_RGTC2_SNORM,

    PIPE_FORMAT_R8G8_B8G8_UNORM,
    PIPE_FORMAT_G8R8_G8B8_UNORM,

    /* mixed formats */
    PIPE_FORMAT_R8SG8SB8UX8U_NORM,
    PIPE_FORMAT_R5SG5SB6U_NORM,

    /* TODO: re-order these */
    PIPE_FORMAT_A8B8G8R8_UNORM,
    PIPE_FORMAT_B5G5R5X1_UNORM,
    PIPE_FORMAT_R10G10B10A2_USCALED,
    PIPE_FORMAT_R11G11B10_FLOAT,
    PIPE_FORMAT_R9G9B9E5_FLOAT,
    PIPE_FORMAT_Z32_FLOAT_S8X24_UINT,
    PIPE_FORMAT_R1_UNORM,
    PIPE_FORMAT_R10G10B10X2_USCALED,
    PIPE_FORMAT_R10G10B10X2_SNORM,
    PIPE_FORMAT_L4A4_UNORM,
    PIPE_FORMAT_A2R10G10B10_UNORM,
    PIPE_FORMAT_A2B10G10R10_UNORM,
    PIPE_FORMAT_B10G10R10A2_UNORM,
    PIPE_FORMAT_R10SG10SB10SA2U_NORM,
    PIPE_FORMAT_R8G8Bx_SNORM,
    PIPE_FORMAT_R8G8B8X8_UNORM,
    PIPE_FORMAT_B4G4R4X4_UNORM,

    /* some stencil samplers formats */
    PIPE_FORMAT_X24S8_UINT,
    PIPE_FORMAT_S8X24_UINT,
    PIPE_FORMAT_X32_S8X24_UINT,

    PIPE_FORMAT_R3G3B2_UNORM,
    PIPE_FORMAT_B2G3R3_UNORM,
    PIPE_FORMAT_L16A16_UNORM,
    PIPE_FORMAT_A16_UNORM,
    PIPE_FORMAT_I16_UNORM,

    PIPE_FORMAT_LATC1_UNORM,
    PIPE_FORMAT_LATC1_SNORM,
    PIPE_FORMAT_LATC2_UNORM,
    PIPE_FORMAT_LATC2_SNORM,

    PIPE_FORMAT_A8_SNORM,
    PIPE_FORMAT_L8_SNORM,
    PIPE_FORMAT_L8A8_SNORM,
    PIPE_FORMAT_I8_SNORM,
    PIPE_FORMAT_A16_SNORM,
    PIPE_FORMAT_L16_SNORM,
    PIPE_FORMAT_L16A16_SNORM,
    PIPE_FORMAT_I16_SNORM,

    PIPE_FORMAT_A16_FLOAT,
    PIPE_FORMAT_L16_FLOAT,
    PIPE_FORMAT_L16A16_FLOAT,
    PIPE_FORMAT_I16_FLOAT,
    PIPE_FORMAT_A32_FLOAT,
    PIPE_FORMAT_L32_FLOAT,
    PIPE_FORMAT_L32A32_FLOAT,
    PIPE_FORMAT_I32_FLOAT,

    PIPE_FORMAT_YV12,
    PIPE_FORMAT_YV16,
    PIPE_FORMAT_IYUV,  /**< aka I420 */
    PIPE_FORMAT_NV12,
    PIPE_FORMAT_NV21,

    /* PIPE_FORMAT_Y8_U8_V8_420_UNORM = IYUV */
    /* PIPE_FORMAT_Y8_U8V8_420_UNORM = NV12 */
    PIPE_FORMAT_Y8_U8_V8_422_UNORM,
    PIPE_FORMAT_Y8_U8V8_422_UNORM,
    PIPE_FORMAT_Y8_U8_V8_444_UNORM,

    PIPE_FORMAT_Y16_U16_V16_420_UNORM,
    /* PIPE_FORMAT_Y16_U16V16_420_UNORM */
    PIPE_FORMAT_Y16_U16_V16_422_UNORM,
    PIPE_FORMAT_Y16_U16V16_422_UNORM,
    PIPE_FORMAT_Y16_U16_V16_444_UNORM,

    PIPE_FORMAT_A4R4_UNORM,
    PIPE_FORMAT_R4A4_UNORM,
    PIPE_FORMAT_R8A8_UNORM,
    PIPE_FORMAT_A8R8_UNORM,

    PIPE_FORMAT_R10G10B10A2_SSCALED,
    PIPE_FORMAT_R10G10B10A2_SNORM,

    PIPE_FORMAT_B10G10R10A2_USCALED,
    PIPE_FORMAT_B10G10R10A2_SSCALED,
    PIPE_FORMAT_B10G10R10A2_SNORM,

    PIPE_FORMAT_R8_UINT,
    PIPE_FORMAT_R8G8_UINT,
    PIPE_FORMAT_R8G8B8_UINT,
    PIPE_FORMAT_R8G8B8A8_UINT,

    PIPE_FORMAT_R8_SINT,
    PIPE_FORMAT_R8G8_SINT,
    PIPE_FORMAT_R8G8B8_SINT,
    PIPE_FORMAT_R8G8B8A8_SINT,

    PIPE_FORMAT_R16_UINT,
    PIPE_FORMAT_R16G16_UINT,
    PIPE_FORMAT_R16G16B16_UINT,
    PIPE_FORMAT_R16G16B16A16_UINT,

    PIPE_FORMAT_R16_SINT,
    PIPE_FORMAT_R16G16_SINT,
    PIPE_FORMAT_R16G16B16_SINT,
    PIPE_FORMAT_R16G16B16A16_SINT,

    PIPE_FORMAT_R32_UINT,
    PIPE_FORMAT_R32G32_UINT,
    PIPE_FORMAT_R32G32B32_UINT,
    PIPE_FORMAT_R32G32B32A32_UINT,

    PIPE_FORMAT_R32_SINT,
    PIPE_FORMAT_R32G32_SINT,
    PIPE_FORMAT_R32G32B32_SINT,
    PIPE_FORMAT_R32G32B32A32_SINT,

    PIPE_FORMAT_R64_UINT,
    PIPE_FORMAT_R64_SINT,

    PIPE_FORMAT_A8_UINT,
    PIPE_FORMAT_I8_UINT,
    PIPE_FORMAT_L8_UINT,
    PIPE_FORMAT_L8A8_UINT,

    PIPE_FORMAT_A8_SINT,
    PIPE_FORMAT_I8_SINT,
    PIPE_FORMAT_L8_SINT,
    PIPE_FORMAT_L8A8_SINT,

    PIPE_FORMAT_A16_UINT,
    PIPE_FORMAT_I16_UINT,
    PIPE_FORMAT_L16_UINT,
    PIPE_FORMAT_L16A16_UINT,

    PIPE_FORMAT_A16_SINT,
    PIPE_FORMAT_I16_SINT,
    PIPE_FORMAT_L16_SINT,
    PIPE_FORMAT_L16A16_SINT,

    PIPE_FORMAT_A32_UINT,
    PIPE_FORMAT_I32_UINT,
    PIPE_FORMAT_L32_UINT,
    PIPE_FORMAT_L32A32_UINT,

    PIPE_FORMAT_A32_SINT,
    PIPE_FORMAT_I32_SINT,
    PIPE_FORMAT_L32_SINT,
    PIPE_FORMAT_L32A32_SINT,

    PIPE_FORMAT_B8G8R8_UINT,
    PIPE_FORMAT_B8G8R8A8_UINT,

    PIPE_FORMAT_B8G8R8_SINT,
    PIPE_FORMAT_B8G8R8A8_SINT,

    PIPE_FORMAT_A8R8G8B8_UINT,
    PIPE_FORMAT_A8B8G8R8_UINT,
    PIPE_FORMAT_A2R10G10B10_UINT,
    PIPE_FORMAT_A2B10G10R10_UINT,
    PIPE_FORMAT_B10G10R10A2_UINT,
    PIPE_FORMAT_B10G10R10A2_SINT,
    PIPE_FORMAT_R5G6B5_UINT,
    PIPE_FORMAT_B5G6R5_UINT,
    PIPE_FORMAT_R5G5B5A1_UINT,
    PIPE_FORMAT_B5G5R5A1_UINT,
    PIPE_FORMAT_A1R5G5B5_UINT,
    PIPE_FORMAT_A1B5G5R5_UINT,
    PIPE_FORMAT_R4G4B4A4_UINT,
    PIPE_FORMAT_B4G4R4A4_UINT,
    PIPE_FORMAT_A4R4G4B4_UINT,
    PIPE_FORMAT_A4B4G4R4_UINT,
    PIPE_FORMAT_R3G3B2_UINT,
    PIPE_FORMAT_B2G3R3_UINT,

    PIPE_FORMAT_ETC1_RGB8,

    PIPE_FORMAT_R8G8_R8B8_UNORM,
    PIPE_FORMAT_G8R8_B8R8_UNORM,

    PIPE_FORMAT_R8G8B8X8_SNORM,
    PIPE_FORMAT_R8G8B8X8_SRGB,
    PIPE_FORMAT_R8G8B8X8_UINT,
    PIPE_FORMAT_R8G8B8X8_SINT,
    PIPE_FORMAT_B10G10R10X2_UNORM,
    PIPE_FORMAT_R16G16B16X16_UNORM,
    PIPE_FORMAT_R16G16B16X16_SNORM,
    PIPE_FORMAT_R16G16B16X16_FLOAT,
    PIPE_FORMAT_R16G16B16X16_UINT,
    PIPE_FORMAT_R16G16B16X16_SINT,
    PIPE_FORMAT_R32G32B32X32_FLOAT,
    PIPE_FORMAT_R32G32B32X32_UINT,
    PIPE_FORMAT_R32G32B32X32_SINT,

    PIPE_FORMAT_R8A8_SNORM,
    PIPE_FORMAT_R16A16_UNORM,
    PIPE_FORMAT_R16A16_SNORM,
    PIPE_FORMAT_R16A16_FLOAT,
    PIPE_FORMAT_R32A32_FLOAT,
    PIPE_FORMAT_R8A8_UINT,
    PIPE_FORMAT_R8A8_SINT,
    PIPE_FORMAT_R16A16_UINT,
    PIPE_FORMAT_R16A16_SINT,
    PIPE_FORMAT_R32A32_UINT,
    PIPE_FORMAT_R32A32_SINT,
    PIPE_FORMAT_R10G10B10A2_UINT,
    PIPE_FORMAT_R10G10B10A2_SINT,

    PIPE_FORMAT_B5G6R5_SRGB,

    PIPE_FORMAT_BPTC_RGBA_UNORM,
    PIPE_FORMAT_BPTC_SRGBA,
    PIPE_FORMAT_BPTC_RGB_FLOAT,
    PIPE_FORMAT_BPTC_RGB_UFLOAT,

    PIPE_FORMAT_G8R8_UNORM,
    PIPE_FORMAT_G8R8_SNORM,
    PIPE_FORMAT_G16R16_UNORM,
    PIPE_FORMAT_G16R16_SNORM,

    PIPE_FORMAT_A8B8G8R8_SNORM,
    PIPE_FORMAT_X8B8G8R8_SNORM,

    PIPE_FORMAT_ETC2_RGB8,
    PIPE_FORMAT_ETC2_SRGB8,
    PIPE_FORMAT_ETC2_RGB8A1,
    PIPE_FORMAT_ETC2_SRGB8A1,
    PIPE_FORMAT_ETC2_RGBA8,
    PIPE_FORMAT_ETC2_SRGBA8,
    PIPE_FORMAT_ETC2_R11_UNORM,
    PIPE_FORMAT_ETC2_R11_SNORM,
    PIPE_FORMAT_ETC2_RG11_UNORM,
    PIPE_FORMAT_ETC2_RG11_SNORM,

    PIPE_FORMAT_ASTC_4x4,
    PIPE_FORMAT_ASTC_5x4,
    PIPE_FORMAT_ASTC_5x5,
    PIPE_FORMAT_ASTC_6x5,
    PIPE_FORMAT_ASTC_6x6,
    PIPE_FORMAT_ASTC_8x5,
    PIPE_FORMAT_ASTC_8x6,
    PIPE_FORMAT_ASTC_8x8,
    PIPE_FORMAT_ASTC_10x5,
    PIPE_FORMAT_ASTC_10x6,
    PIPE_FORMAT_ASTC_10x8,
    PIPE_FORMAT_ASTC_10x10,
    PIPE_FORMAT_ASTC_12x10,
    PIPE_FORMAT_ASTC_12x12,

    PIPE_FORMAT_ASTC_4x4_SRGB,
    PIPE_FORMAT_ASTC_5x4_SRGB,
    PIPE_FORMAT_ASTC_5x5_SRGB,
    PIPE_FORMAT_ASTC_6x5_SRGB,
    PIPE_FORMAT_ASTC_6x6_SRGB,
    PIPE_FORMAT_ASTC_8x5_SRGB,
    PIPE_FORMAT_ASTC_8x6_SRGB,
    PIPE_FORMAT_ASTC_8x8_SRGB,
    PIPE_FORMAT_ASTC_10x5_SRGB,
    PIPE_FORMAT_ASTC_10x6_SRGB,
    PIPE_FORMAT_ASTC_10x8_SRGB,
    PIPE_FORMAT_ASTC_10x10_SRGB,
    PIPE_FORMAT_ASTC_12x10_SRGB,
    PIPE_FORMAT_ASTC_12x12_SRGB,

    PIPE_FORMAT_ASTC_3x3x3,
    PIPE_FORMAT_ASTC_4x3x3,
    PIPE_FORMAT_ASTC_4x4x3,
    PIPE_FORMAT_ASTC_4x4x4,
    PIPE_FORMAT_ASTC_5x4x4,
    PIPE_FORMAT_ASTC_5x5x4,
    PIPE_FORMAT_ASTC_5x5x5,
    PIPE_FORMAT_ASTC_6x5x5,
    PIPE_FORMAT_ASTC_6x6x5,
    PIPE_FORMAT_ASTC_6x6x6,

    PIPE_FORMAT_ASTC_3x3x3_SRGB,
    PIPE_FORMAT_ASTC_4x3x3_SRGB,
    PIPE_FORMAT_ASTC_4x4x3_SRGB,
    PIPE_FORMAT_ASTC_4x4x4_SRGB,
    PIPE_FORMAT_ASTC_5x4x4_SRGB,
    PIPE_FORMAT_ASTC_5x5x4_SRGB,
    PIPE_FORMAT_ASTC_5x5x5_SRGB,
    PIPE_FORMAT_ASTC_6x5x5_SRGB,
    PIPE_FORMAT_ASTC_6x6x5_SRGB,
    PIPE_FORMAT_ASTC_6x6x6_SRGB,

    PIPE_FORMAT_FXT1_RGB,
    PIPE_FORMAT_FXT1_RGBA,

    PIPE_FORMAT_P010,
    PIPE_FORMAT_P012,
    PIPE_FORMAT_P016,

    PIPE_FORMAT_R10G10B10X2_UNORM,
    PIPE_FORMAT_A1R5G5B5_UNORM,
    PIPE_FORMAT_A1B5G5R5_UNORM,
    PIPE_FORMAT_X1B5G5R5_UNORM,
    PIPE_FORMAT_R5G5B5A1_UNORM,
    PIPE_FORMAT_A4R4G4B4_UNORM,
    PIPE_FORMAT_A4B4G4R4_UNORM,

    PIPE_FORMAT_G8R8_SINT,
    PIPE_FORMAT_A8B8G8R8_SINT,
    PIPE_FORMAT_X8B8G8R8_SINT,

    PIPE_FORMAT_ATC_RGB,
    PIPE_FORMAT_ATC_RGBA_EXPLICIT,
    PIPE_FORMAT_ATC_RGBA_INTERPOLATED,

    PIPE_FORMAT_Z24_UNORM_S8_UINT_AS_R8G8B8A8,

    PIPE_FORMAT_AYUV,
    PIPE_FORMAT_XYUV,

    PIPE_FORMAT_R8_G8B8_420_UNORM,

    PIPE_FORMAT_COUNT
};

struct st_visual
{
    bool no_config;

    /**
     * Available buffers.  Bitfield of ST_ATTACHMENT_*_MASK bits.
     */
    unsigned buffer_mask;

    /**
     * Buffer formats.  The formats are always set even when the buffer is
     * not available.
     */
    enum pipe_format color_format;
    enum pipe_format depth_stencil_format;
    enum pipe_format accum_format;
    unsigned samples;

    /**
     * Desired render buffer.
     */
    enum st_attachment_type render_buffer;
};
typedef struct osmesa_buffer
{
    struct st_framebuffer_iface *stfb;
    struct st_visual visual;
    unsigned width, height;

    struct pipe_resource *textures[ST_ATTACHMENT_COUNT];

    void *map;

    struct osmesa_buffer *next;  /**< next in linked list */
};


typedef struct osmesa_context
{
    struct st_context_iface *stctx;

    bool ever_used;     /*< Has this context ever been current? */

    struct osmesa_buffer *current_buffer;

    /* Storage for depth/stencil, if the user has requested access.  The backing
     * driver always has its own storage for the actual depth/stencil, which we
     * have to transfer in and out.
     */
    void *zs;
    unsigned zs_stride;

    enum pipe_format depth_stencil_format, accum_format;

    GLenum format;         /*< User-specified context format */
    GLenum type;           /*< Buffer's data type */
    GLint user_row_length; /*< user-specified number of pixels per row */
    GLboolean y_up;        /*< TRUE  -> Y increases upward */
    /*< FALSE -> Y increases downward */

    /** Which postprocessing filters are enabled. */
    //safe to remove
};
// endregion OSMESA internals
struct PotatoBridge {

    /* EGLContext */ void* eglContext;
    /* EGLDisplay */ void* eglDisplay;
    /* EGLSurface */ void* eglSurface;
/*
    void* eglSurfaceRead;
    void* eglSurfaceDraw;
*/
};
EGLConfig config;
struct PotatoBridge potatoBridge;

#include "ctxbridges/egl_loader.h"
#include "ctxbridges/osmesa_loader.h"
int (*vtest_main_p) (int argc, char** argv);
void (*vtest_swap_buffers_p) (void);
void bigcore_set_affinity();

#define RENDERER_GL4ES 1
#define RENDERER_VK_ZINK 2
#define RENDERER_VIRGL 3
#define RENDERER_VULKAN 4

void* egl_make_current(void* window);

EXTERNAL_API void pojavTerminate() {
    printf("EGLBridge: Terminating\n");

    switch (pojav_environ->config_renderer) {
        case RENDERER_GL4ES: {
            eglMakeCurrent_p(potatoBridge.eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            eglDestroySurface_p(potatoBridge.eglDisplay, potatoBridge.eglSurface);
            eglDestroyContext_p(potatoBridge.eglDisplay, potatoBridge.eglContext);
            eglTerminate_p(potatoBridge.eglDisplay);
            eglReleaseThread_p();

            potatoBridge.eglContext = EGL_NO_CONTEXT;
            potatoBridge.eglDisplay = EGL_NO_DISPLAY;
            potatoBridge.eglSurface = EGL_NO_SURFACE;
        } break;

            //case RENDERER_VIRGL:
        case RENDERER_VK_ZINK: {
            // Nothing to do here
        } break;
    }
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeWindow(JNIEnv* env, ABI_COMPAT jclass clazz, jobject surface) {
    pojav_environ->pojavWindow = ANativeWindow_fromSurface(env, surface);
    if(pojav_environ->config_renderer == RENDERER_GL4ES) {
        gl_setup_window();
    }
}


JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_releaseBridgeWindow(ABI_COMPAT JNIEnv *env, ABI_COMPAT jclass clazz) {
    ANativeWindow_release(pojav_environ->pojavWindow);
}

EXTERNAL_API void* pojavGetCurrentContext() {
    switch (pojav_environ->config_renderer) {
        case RENDERER_GL4ES:
            return (void *)eglGetCurrentContext_p();
        case RENDERER_VIRGL:
        case RENDERER_VK_ZINK:
            return (void *)OSMesaGetCurrentContext_p();

        default: return NULL;
    }
}

void loadSymbols() {
    switch (pojav_environ->config_renderer) {
        case RENDERER_VIRGL:
            dlsym_OSMesa_1();
            dlsym_EGL();
            break;
        case RENDERER_VK_ZINK:
            dlsym_OSMesa();
            break;
        case RENDERER_GL4ES:
            //inside glbridge
            break;
    }
}

//#define ADRENO_POSSIBLE
#ifdef ADRENO_POSSIBLE
//Checks if your graphics are Adreno. Returns true if your graphics are Adreno, false otherwise or if there was an error
bool checkAdrenoGraphics() {
    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if(eglDisplay == EGL_NO_DISPLAY || eglInitialize(eglDisplay, NULL, NULL) != EGL_TRUE) return false;
    EGLint egl_attributes[] = { EGL_BLUE_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_RED_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_DEPTH_SIZE, 24, EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_NONE };
    EGLint num_configs = 0;
    if(eglChooseConfig(eglDisplay, egl_attributes, NULL, 0, &num_configs) != EGL_TRUE || num_configs == 0) {
        eglTerminate(eglDisplay);
        return false;
    }
    EGLConfig eglConfig;
    eglChooseConfig(eglDisplay, egl_attributes, &eglConfig, 1, &num_configs);
    const EGLint egl_context_attributes[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    EGLContext context = eglCreateContext(eglDisplay, eglConfig, EGL_NO_CONTEXT, egl_context_attributes);
    if(context == EGL_NO_CONTEXT) {
        eglTerminate(eglDisplay);
        return false;
    }
    if(eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, context) != EGL_TRUE) {
        eglDestroyContext(eglDisplay, context);
        eglTerminate(eglDisplay);
    }
    const char* vendor = glGetString(GL_VENDOR);
    const char* renderer = glGetString(GL_RENDERER);
    bool is_adreno = false;
    if(strcmp(vendor, "Qualcomm") == 0 && strstr(renderer, "Adreno") != NULL) {
        is_adreno = true; // TODO: check for Turnip support
    }
    eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(eglDisplay, context);
    eglTerminate(eglDisplay);
    return is_adreno;
}
void* load_turnip_vulkan() {
    if(!checkAdrenoGraphics()) return NULL;
    const char* native_dir = getenv("POJAV_NATIVEDIR");
    const char* cache_dir = getenv("TMPDIR");
    if(!linker_ns_load(native_dir)) return NULL;
    void* linkerhook = linker_ns_dlopen("liblinkerhook.so", RTLD_LOCAL | RTLD_NOW);
    if(linkerhook == NULL) return NULL;
    void* turnip_driver_handle = linker_ns_dlopen("libvulkan_freedreno.so", RTLD_LOCAL | RTLD_NOW);
    if(turnip_driver_handle == NULL) {
        printf("AdrenoSupp: Failed to load Turnip!\n%s\n", dlerror());
        dlclose(linkerhook);
        return NULL;
    }
    void* dl_android = linker_ns_dlopen("libdl_android.so", RTLD_LOCAL | RTLD_LAZY);
    if(dl_android == NULL) {
        dlclose(linkerhook);
        dlclose(turnip_driver_handle);
        return NULL;
    }
    void* android_get_exported_namespace = dlsym(dl_android, "android_get_exported_namespace");
    void (*linkerhook_pass_handles)(void*, void*, void*) = dlsym(linkerhook, "app__pojav_linkerhook_pass_handles");
    if(linkerhook_pass_handles == NULL || android_get_exported_namespace == NULL) {
        dlclose(dl_android);
        dlclose(linkerhook);
        dlclose(turnip_driver_handle);
        return NULL;
    }
    linkerhook_pass_handles(turnip_driver_handle, android_dlopen_ext, android_get_exported_namespace);
    void* libvulkan = linker_ns_dlopen_unique(cache_dir, "libvulkan.so", RTLD_LOCAL | RTLD_NOW);
    return libvulkan;
}
#endif

static void set_vulkan_ptr(void* ptr) {
    char envval[64];
    sprintf(envval, "%"PRIxPTR, (uintptr_t)ptr);
    setenv("VULKAN_PTR", envval, 1);
}

void load_vulkan() {
    if(getenv("POJAV_ZINK_PREFER_SYSTEM_DRIVER") == NULL && android_get_device_api_level() >= 28) {
    // the loader does not support below that
#ifdef ADRENO_POSSIBLE
        void* result = load_turnip_vulkan();
        if(result != NULL) {
            printf("AdrenoSupp: Loaded Turnip, loader address: %p\n", result);
            set_vulkan_ptr(result);
            return;
        }
#endif
    }
    printf("OSMDroid: loading vulkan regularly...\n");
    void* vulkan_ptr = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
    printf("OSMDroid: loaded vulkan, ptr=%p\n", vulkan_ptr);
    set_vulkan_ptr(vulkan_ptr);
}

bool loadSymbolsVirGL() {
    pojav_environ->config_renderer = RENDERER_VIRGL;
    loadSymbols();

    char* fileName = calloc(1, 1024);

    sprintf(fileName, "%s/libvirgl_test_server.so", getenv("POJAV_NATIVEDIR"));
    void *handle = dlopen(fileName, RTLD_LAZY);
    printf("VirGL: libvirgl_test_server = %p\n", handle);
    if (!handle) {
        printf("VirGL: %s\n", dlerror());
    }
    vtest_main_p = dlsym(handle, "vtest_main");
    vtest_swap_buffers_p = dlsym(handle, "vtest_swap_buffers");

    free(fileName);
}

EXTERNAL_API int pojavInit() {
    ANativeWindow_acquire(pojav_environ->pojavWindow);
    pojav_environ->savedWidth = ANativeWindow_getWidth(pojav_environ->pojavWindow);
    pojav_environ->savedHeight = ANativeWindow_getHeight(pojav_environ->pojavWindow);
    ANativeWindow_setBuffersGeometry(pojav_environ->pojavWindow,pojav_environ->savedWidth,pojav_environ->savedHeight,AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM);
    return 1;
}

int pojavInitOpenGL() {
    // Only affects GL4ES as of now
    const char *forceVsync = getenv("FORCE_VSYNC");
    if (strcmp(forceVsync, "true") == 0)
        pojav_environ->force_vsync = true;

    // NOTE: Override for now.
    const char *renderer = getenv("POJAV_RENDERER");
    if (strncmp("opengles3_virgl", renderer, 15) == 0) {
        pojav_environ->config_renderer = RENDERER_VIRGL;
        setenv("GALLIUM_DRIVER","virpipe",1);
        setenv("OSMESA_NO_FLUSH_FRONTBUFFER","1",false);
        if(strcmp(getenv("OSMESA_NO_FLUSH_FRONTBUFFER"),"1") == 0) {
            printf("VirGL: OSMesa buffer flush is DISABLED!\n");
        }
        loadSymbolsVirGL();
    } else if (strncmp("opengles", renderer, 8) == 0) {
        pojav_environ->config_renderer = RENDERER_GL4ES;
        //loadSymbols();
    } else if (strcmp(renderer, "vulkan_zink") == 0) {
        pojav_environ->config_renderer = RENDERER_VK_ZINK;
        load_vulkan();
        setenv("GALLIUM_DRIVER","zink",1);
        loadSymbols();
    }
    if(pojav_environ->config_renderer == RENDERER_GL4ES) {
        if(gl_init()) {
            gl_setup_window();
            return 1;
        }
        return 0;
    }
    if (pojav_environ->config_renderer == RENDERER_VIRGL) {
        if (potatoBridge.eglDisplay == NULL || potatoBridge.eglDisplay == EGL_NO_DISPLAY) {
            potatoBridge.eglDisplay = eglGetDisplay_p(EGL_DEFAULT_DISPLAY);
            if (potatoBridge.eglDisplay == EGL_NO_DISPLAY) {
                printf("EGLBridge: Error eglGetDefaultDisplay() failed: %p\n", eglGetError_p());
                return 0;
            }
        }

        printf("EGLBridge: Initializing\n");
        // printf("EGLBridge: ANativeWindow pointer = %p\n", pojav_environ->pojavWindow);
        //(*env)->ThrowNew(env,(*env)->FindClass(env,"java/lang/Exception"),"Trace exception");
        if (!eglInitialize_p(potatoBridge.eglDisplay, NULL, NULL)) {
            printf("EGLBridge: Error eglInitialize() failed: %s\n", eglGetError_p());
            return 0;
        }

        static const EGLint attribs[] = {
                EGL_RED_SIZE, 8,
                EGL_GREEN_SIZE, 8,
                EGL_BLUE_SIZE, 8,
                EGL_ALPHA_SIZE, 8,
                // Minecraft required on initial 24
                EGL_DEPTH_SIZE, 24,
                EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL_NONE
        };

        EGLint num_configs;
        EGLint vid;

        if (!eglChooseConfig_p(potatoBridge.eglDisplay, attribs, &config, 1, &num_configs)) {
            printf("EGLBridge: Error couldn't get an EGL visual config: %s\n", eglGetError_p());
            return 0;
        }

        assert(config);
        assert(num_configs > 0);

        if (!eglGetConfigAttrib_p(potatoBridge.eglDisplay, config, EGL_NATIVE_VISUAL_ID, &vid)) {
            printf("EGLBridge: Error eglGetConfigAttrib() failed: %s\n", eglGetError_p());
            return 0;
        }

        ANativeWindow_setBuffersGeometry(pojav_environ->pojavWindow, 0, 0, vid);

        eglBindAPI_p(EGL_OPENGL_ES_API);

        potatoBridge.eglSurface = eglCreateWindowSurface_p(potatoBridge.eglDisplay, config, pojav_environ->pojavWindow, NULL);

        if (!potatoBridge.eglSurface) {
            printf("EGLBridge: Error eglCreateWindowSurface failed: %p\n", eglGetError_p());
            //(*env)->ThrowNew(env,(*env)->FindClass(env,"java/lang/Exception"),"Trace exception");
            return 0;
        }

        // sanity checks
        {
            EGLint val;
            assert(eglGetConfigAttrib_p(potatoBridge.eglDisplay, config, EGL_SURFACE_TYPE, &val));
            assert(val & EGL_WINDOW_BIT);
        }

        printf("EGLBridge: Initialized!\n");
        printf("EGLBridge: ThreadID=%d\n", gettid());
        printf("EGLBridge: EGLDisplay=%p, EGLSurface=%p\n",
/* window==0 ? EGL_NO_CONTEXT : */
               potatoBridge.eglDisplay,
               potatoBridge.eglSurface
        );
        if (pojav_environ->config_renderer != RENDERER_VIRGL) {
            return 1;
        }
    }

    if (pojav_environ->config_renderer == RENDERER_VIRGL) {
        // Init EGL context and vtest server
        const EGLint ctx_attribs[] = {
                EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL_NONE
        };
        EGLContext* ctx = eglCreateContext_p(potatoBridge.eglDisplay, config, NULL, ctx_attribs);
        printf("VirGL: created EGL context %p\n", ctx);

        pthread_t t;
        pthread_create(&t, NULL, egl_make_current, (void *)ctx);
        usleep(100*1000); // need enough time for the server to init
    }

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK || pojav_environ->config_renderer == RENDERER_VIRGL) {
        if(OSMesaCreateContext_p == NULL) {
            printf("OSMDroid: %s\n",dlerror());
            return 0;
        }
    }

    return 0;
}

EXTERNAL_API void pojavSetWindowHint(int hint, int value) {
    if (hint != GLFW_CLIENT_API) return;
    switch (value) {
        case GLFW_NO_API:
            pojav_environ->config_renderer = RENDERER_VULKAN;
            /* Nothing to do: initialization is handled in Java-side */
            // pojavInitVulkan();
            break;
        case GLFW_OPENGL_API:
            /* Nothing to do: initialization is called in pojavCreateContext */
            // pojavInitOpenGL();
            break;
        default:
            printf("GLFW: Unimplemented API 0x%x\n", value);
            abort();
    }
}

ANativeWindow_Buffer buf;
int32_t stride;
bool stopSwapBuffers;
void pojavSwapBuffers() {
    if (stopSwapBuffers) {
        return;
    }
    switch (pojav_environ->config_renderer) {
        case RENDERER_GL4ES: {
            gl_swap_buffers();
        } break;

        case RENDERER_VIRGL: {
            glFinish_p();
            vtest_swap_buffers_p();
        } break;

        case RENDERER_VK_ZINK: {
            OSMesaContext ctx = OSMesaGetCurrentContext_p();
            if(ctx == NULL) {
                printf("Zink: attempted to swap buffers without context!");
                break;
            }
            OSMesaMakeCurrent_p(ctx,buf.bits,GL_UNSIGNED_BYTE,pojav_environ->savedWidth,pojav_environ->savedHeight);
            glFinish_p();
            ANativeWindow_unlockAndPost(pojav_environ->pojavWindow);
            ANativeWindow_lock(pojav_environ->pojavWindow,&buf,NULL);
        } break;
    }
}

void* egl_make_current(void* window) {
    EGLBoolean success = eglMakeCurrent_p(
            potatoBridge.eglDisplay,
            window==0 ? (EGLSurface *) 0 : potatoBridge.eglSurface,
            window==0 ? (EGLSurface *) 0 : potatoBridge.eglSurface,
            /* window==0 ? EGL_NO_CONTEXT : */ (EGLContext *) window
    );

    if (success == EGL_FALSE) {
        printf("EGLBridge: Error: eglMakeCurrent() failed: %p\n", eglGetError_p());
    } else {
        printf("EGLBridge: eglMakeCurrent() succeed!\n");
    }

    if (pojav_environ->config_renderer == RENDERER_VIRGL) {
        printf("VirGL: vtest_main = %p\n", vtest_main_p);
        printf("VirGL: Calling VTest server's main function\n");
        vtest_main_p(3, (const char*[]){"vtest", "--no-loop-or-fork", "--use-gles", NULL, NULL});
    }
}

EXTERNAL_API void pojavMakeCurrent(void* window) {
    if(getenv("POJAV_BIG_CORE_AFFINITY") != NULL) bigcore_set_affinity();
    if(pojav_environ->config_renderer == RENDERER_GL4ES) {
        gl_make_current((render_window_t*)window);
    }
    if (pojav_environ->config_renderer == RENDERER_VIRGL) {
        printf("OSMDroid: making current\n");
        OSMesaMakeCurrent_p((OSMesaContext)window,setbuffer,GL_UNSIGNED_BYTE,pojav_environ->savedWidth,pojav_environ->savedHeight);


        printf("OSMDroid: vendor: %s\n",glGetString_p(GL_VENDOR));
        printf("OSMDroid: renderer: %s\n",glGetString_p(GL_RENDERER));
        glClear_p(GL_COLOR_BUFFER_BIT);
        glClearColor_p(0.4f, 0.4f, 0.4f, 1.0f);

        // Trigger a texture creation, which then set VIRGL_TEXTURE_ID
        int pixelsArr[4];
        glReadPixels_p(0, 0, 1, 1, GL_RGB, GL_INT, &pixelsArr);

        pojavSwapBuffers();
        return;
    }
    if (pojav_environ->config_renderer == RENDERER_VK_ZINK) {
        printf("OSMDroid: making current %p\n", pojav_environ->pojavWindow);
        ANativeWindow_lock(pojav_environ->pojavWindow,&buf,NULL);
        OSMesaMakeCurrent_p((OSMesaContext)window,buf.bits,GL_UNSIGNED_BYTE,pojav_environ->savedWidth,pojav_environ->savedHeight);
        OSMesaPixelStore_p(OSMESA_ROW_LENGTH,buf.stride);
        OSMesaPixelStore_p(OSMESA_Y_UP,0);


        printf("OSMDroid: vendor: %s\n",glGetString_p(GL_VENDOR));
        printf("OSMDroid: renderer: %s\n",glGetString_p(GL_RENDERER));
        glClearColor_p(0.4f, 0.4f, 0.4f, 1.0f);
        glClear_p(GL_COLOR_BUFFER_BIT);

        pojavSwapBuffers();
    }
}

EXTERNAL_API void* pojavCreateContext(void* contextSrc) {
    if (pojav_environ->config_renderer == RENDERER_VULKAN) {
        return (void *)pojav_environ->pojavWindow;
    }

    pojavInitOpenGL();

    if (pojav_environ->config_renderer == RENDERER_GL4ES) {
        return gl_init_context(contextSrc);
    }

    if (pojav_environ->config_renderer == RENDERER_VK_ZINK || pojav_environ->config_renderer == RENDERER_VIRGL) {
        printf("OSMDroid: generating context\n");
        void* ctx = OSMesaCreateContext_p(OSMESA_RGBA,contextSrc);
        printf("OSMDroid: context=%p\n",ctx);
        return ctx;
    }
    printf("Unknown config_renderer value: %i\n", pojav_environ->config_renderer);
    abort();
}

EXTERNAL_API JNIEXPORT jlong JNICALL
Java_org_lwjgl_vulkan_VK_getVulkanDriverHandle(ABI_COMPAT JNIEnv *env, ABI_COMPAT jclass thiz) {
    printf("EGLBridge: LWJGL-side Vulkan loader requested the Vulkan handle\n");
    // The code below still uses the env var because
    // 1. it's easier to do that
    // 2. it won't break if something will try to load vulkan and osmesa simultaneously
    if(getenv("VULKAN_PTR") == NULL) load_vulkan();
    return strtoul(getenv("VULKAN_PTR"), NULL, 0x10);
}

EXTERNAL_API JNIEXPORT jlong JNICALL
Java_org_lwjgl_opengl_GL_getGraphicsBufferAddr(ABI_COMPAT JNIEnv *env, ABI_COMPAT jobject thiz) {
    return (jlong) buf.bits;
}
EXTERNAL_API JNIEXPORT jintArray JNICALL
Java_org_lwjgl_opengl_GL_getNativeWidthHeight(JNIEnv *env, ABI_COMPAT jobject thiz) {
    jintArray ret = (*env)->NewIntArray(env,2);
    jint arr[] = {pojav_environ->savedWidth, pojav_environ->savedHeight};
    (*env)->SetIntArrayRegion(env,ret,0,2,arr);
    return ret;
}
EXTERNAL_API void pojavSwapInterval(int interval) {
    switch (pojav_environ->config_renderer) {
        case RENDERER_GL4ES: {
            gl_swap_interval(interval);
        } break;
        case RENDERER_VIRGL: {
            eglSwapInterval_p(potatoBridge.eglDisplay, interval);
        } break;

        case RENDERER_VK_ZINK: {
            printf("eglSwapInterval: NOT IMPLEMENTED YET!\n");
            // Nothing to do here
        } break;
    }
}

