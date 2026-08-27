#pragma once
#include <jni.h>
#include <mutex>
#include <string>
#include <vector>
#include <sstream>
#include <dlfcn.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include "MG/extensions.h"

typedef EGLDisplay (*PFN_eglGetDisplay)(EGLNativeDisplayType);
typedef EGLBoolean (*PFN_eglInitialize)(EGLDisplay, EGLint*, EGLint*);
typedef EGLBoolean (*PFN_eglChooseConfig)(EGLDisplay, const EGLint*, EGLConfig*, EGLint, EGLint*);
typedef EGLSurface (*PFN_eglCreatePbufferSurface)(EGLDisplay, EGLConfig, const EGLint*);
typedef EGLContext (*PFN_eglCreateContext)(EGLDisplay, EGLConfig, EGLContext, const EGLint*);
typedef EGLBoolean (*PFN_eglMakeCurrent)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
typedef EGLBoolean (*PFN_eglDestroySurface)(EGLDisplay, EGLSurface);
typedef EGLBoolean (*PFN_eglDestroyContext)(EGLDisplay, EGLContext);
typedef EGLBoolean (*PFN_eglTerminate)(EGLDisplay);
typedef void (*PFN_glGetIntegerv)(GLenum, GLint*);
typedef const GLubyte* (*PFN_glGetString)(GLenum);
typedef const GLubyte* (*PFN_glGetStringi)(GLenum, GLuint);
typedef void (*PFN_glGetError)();

static PFN_eglGetDisplay p_eglGetDisplay = nullptr;
static PFN_eglInitialize p_eglInitialize = nullptr;
static PFN_eglChooseConfig p_eglChooseConfig = nullptr;
static PFN_eglCreatePbufferSurface p_eglCreatePbufferSurface = nullptr;
static PFN_eglCreateContext p_eglCreateContext = nullptr;
static PFN_eglMakeCurrent p_eglMakeCurrent = nullptr;
static PFN_eglDestroySurface p_eglDestroySurface = nullptr;
static PFN_eglDestroyContext p_eglDestroyContext = nullptr;
static PFN_eglTerminate p_eglTerminate = nullptr;
static PFN_glGetIntegerv p_glGetIntegerv = nullptr;
static PFN_glGetString p_glGetString = nullptr;
static PFN_glGetStringi p_glGetStringi = nullptr;

struct MGQueryCapability {
    bool HasMobileGluesExt = false; // GL_MG_mobileglues
    bool BackendStringGetterAccess = false; // GL_MG_backend_string_getter_access
    bool SettingsStringDump = false; // GL_MG_settings_string_dump
}g_MGQueryCapability;
