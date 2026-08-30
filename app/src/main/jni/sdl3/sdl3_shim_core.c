/*
 * SDL3 compatibility layer for Pojav Glow·Worm.
 *
 * Minecraft 26.3+ talks to SDL3 through the stock LWJGL org.lwjgl.sdl
 * bindings, which dlopen("libSDL3.so") and call SDL3 C symbols directly.
 * This library exports the SDL3 C ABI subset the game uses and routes
 * everything through the pojav bridge: the launcher-owned Android Surface
 * (pojav_environ->pojavWindow), the GLFW-semantic input event ring and the
 * ctxbridges renderers.
 */

#define SDL_MAIN_HANDLED

#include <SDL3/SDL.h>
#include <SDL3/SDL_vulkan.h>
#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <android/native_window.h>
#include <pthread.h>
#include <ctype.h>
#include <errno.h>
#include <math.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include "environ/environ.h"
#include "sdl3_keymap.h"

/* Own copy of the shared bridge state, synced through the POJAV_ENVIRON
 * environment variable exactly like environ/environ.c does for the other
 * libraries; keeps libSDL3.so free of linker dependencies on pojavexec */
struct pojav_environ_s *pojav_environ;
__attribute__((constructor)) static void shim_environ_init(void) {
    const char *value = getenv("POJAV_ENVIRON");
    if (value) pojav_environ = (struct pojav_environ_s *) (uintptr_t) strtoul(value, NULL, 0x10);
}

#define POTATOBRIDGE
#define INITIAL_FRAME_BUFFER
#include "ctxbridges/renderer_config.h"

#define SDL3SHIM_LOG(...) do { \
    __android_log_print(ANDROID_LOG_INFO, "SDL3Shim", __VA_ARGS__); \
    printf(__VA_ARGS__); \
    printf("\n"); \
} while (0)

/* ------------------------------------------------------------------ */
/* Fake window / display identities                                    */
/* ------------------------------------------------------------------ */

typedef struct {
    int magic;
} FakeWindow;
static FakeWindow shimWindow;
#define SDL3_WINDOW      ((SDL_Window *) &shimWindow)
#define SDL3_WINDOW_ID   ((SDL_WindowID) 1)
#define SDL3_DISPLAY_ID  ((SDL_DisplayID) 1)
#define PROPS_GLOBAL     ((SDL_PropertiesID) 1)

/* ------------------------------------------------------------------ */
/* pojav runtime binding (resolved via dlsym, shared state comes from  */
/* pojav_environ which every br_common copy syncs through the          */
/* POJAV_ENVIRON environment variable)                                 */
/* ------------------------------------------------------------------ */

static struct {
    void    *handle;
    int     (*pojavInit)(void);
    void    *(*pojavCreateContext)(void *);
    void    (*pojavMakeCurrent)(void *);
    void    (*pojavSwapBuffers)(void);
    void    (*pojavSwapInterval)(int);
    void    *(*maybe_load_vulkan)(void);
} pojav;

static void bind_pojav(void) {
    static bool bindTried;
    if (pojav.handle || bindTried || !pojav_environ) return;
    bindTried = true;

    void *h = dlopen("libpojavexec.so", RTLD_NOW | RTLD_LOCAL);
    if (!h) {
        const char *nativedir = getenv("POJAV_NATIVEDIR");
        if (nativedir) {
            char path[512];
            snprintf(path, sizeof(path), "%s/libpojavexec.so", nativedir);
            h = dlopen(path, RTLD_NOW | RTLD_LOCAL);
        }
    }
    if (!h) {
        SDL3SHIM_LOG("SDL3: unable to load libpojavexec.so");
        return;
    }
    pojav.handle = h;
    pojav.pojavInit          = (int (*)(void)) dlsym(h, "pojavInit");
    pojav.pojavCreateContext = (void *(*)(void *)) dlsym(h, "pojavCreateContext");
    pojav.pojavMakeCurrent   = (void (*)(void *)) dlsym(h, "pojavMakeCurrent");
    pojav.pojavSwapBuffers   = (void (*)(void)) dlsym(h, "pojavSwapBuffers");
    pojav.pojavSwapInterval  = (void (*)(int)) dlsym(h, "pojavSwapInterval");
    pojav.maybe_load_vulkan  = (void *(*)(void)) dlsym(h, "maybe_load_vulkan");
    if (!pojav.pojavInit) SDL3SHIM_LOG("SDL3: libpojavexec binding incomplete");
}

/* ------------------------------------------------------------------ */
/* ndlopen hook (mirrors jvm_hooks/lwjgl_dlopen_hook.c so the SDL mode  */
/* also gets the vulkan remap and the namespace nesting workaround)     */
/* ------------------------------------------------------------------ */

static jlong shim_ndlopen(JNIEnv *env, jclass clazz, jlong name, jint jmode) {
    (void) env; (void) clazz;
    const char *filename = (const char *) (uintptr_t) name;
    if (filename) {
        if (strncmp(filename, "libvulkan.so", 12) == 0 && pojav.maybe_load_vulkan) {
            return (jlong) (uintptr_t) pojav.maybe_load_vulkan();
        }
        const char *sdl3Path = getenv("POJAV_SDL3_LIB");
        if (sdl3Path && strncmp(filename, "libSDL3.so", 10) == 0) {
            void *h = dlopen(sdl3Path, jmode ? (int) jmode : (RTLD_LAZY | RTLD_LOCAL));
            if (h) return (jlong) (uintptr_t) h;
        }
    }
    return (jlong) (uintptr_t) dlopen(filename, (int) jmode);
}

static JavaVM *find_game_vm(void) {
    if (pojav_environ && pojav_environ->runtimeJavaVMPtr)
        return pojav_environ->runtimeJavaVMPtr;

    typedef jint (*GetVMsFn)(JavaVM **, jsize, jsize *);
    GetVMsFn fn = (GetVMsFn) dlsym(RTLD_DEFAULT, "JNI_GetCreatedJavaVMs");
    if (!fn) {
        /* The OpenJDK libjvm may not live in the global namespace; find it
         * through /proc/self/maps and resolve the function from its handle */
        char path[512] = {0};
        FILE *maps = fopen("/proc/self/maps", "r");
        if (maps) {
            char line[1024];
            while (fgets(line, sizeof(line), maps)) {
                if (strstr(line, "libjvm.so") && sscanf(line, "%*s %*s %*s %*s %*s %511s", path) == 1 && path[0] == '/') {
                    void *h = dlopen(path, RTLD_NOW | RTLD_LOCAL);
                    if (h) fn = (GetVMsFn) dlsym(h, "JNI_GetCreatedJavaVMs");
                    break;
                }
            }
            fclose(maps);
        }
    }
    if (!fn) return NULL;

    JavaVM *vms[8];
    jsize count = 0;
    if (fn(vms, 8, &count) != JNI_OK) return NULL;
    for (int i = 0; i < count; i++) {
        if (!pojav_environ || vms[i] != pojav_environ->dalvikJavaVMPtr) return vms[i];
    }
    return NULL;
}

static void install_dlopen_hook(void) {
    JavaVM *gameVM = find_game_vm();
    if (!gameVM) {
        SDL3SHIM_LOG("SDL3: game VM not found, ndlopen hook not installed");
        return;
    }
    JNIEnv *env = NULL;
    if ((*gameVM)->GetEnv(gameVM, (void **) &env, JNI_VERSION_1_4) != JNI_OK)
        (*gameVM)->AttachCurrentThread(gameVM, &env, NULL);
    if (!env) return;

    jclass loader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if (!loader) {
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod method[] = {
        { "ndlopen", "(JI)J", (void *) &shim_ndlopen }
    };
    if ((*env)->RegisterNatives(env, loader, method, 1) == 0)
        SDL3SHIM_LOG("SDL3: ndlopen hook installed");
    else
        (*env)->ExceptionClear(env);
}

/* ------------------------------------------------------------------ */
/* Dalvik clipboard bridge (accessAndroidClipboard is cached inside     */
/* pojav_environ by the launcher's pojavexec)                           */
/* ------------------------------------------------------------------ */

#define CLIPBOARD_COPY  2000
#define CLIPBOARD_PASTE 2001
#define CLIPBOARD_OPEN  2002

/* Delivers a grab state change to CallbackBridge.onGrabStateChanged on the
 * Dalvik side, mirroring input_bridge_v3.c nativeSetGrabbing. That method has
 * no JavaCritical variant to dlsym, so the JNI call is made directly through
 * the cached bridge class and method id */
static void shim_notify_grab(bool grabbing) {
    JavaVM *dvm = pojav_environ ? pojav_environ->dalvikJavaVMPtr : NULL;
    if (!dvm || !pojav_environ->bridgeClazz) return;
    JNIEnv *env = NULL;
    bool attachedHere = false;
    if ((*dvm)->GetEnv(dvm, (void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        (*dvm)->AttachCurrentThread(dvm, &env, NULL);
        attachedHere = true;
    }
    if (!env) return;
    (*env)->CallStaticVoidMethod(env, pojav_environ->bridgeClazz,
            pojav_environ->method_onGrabStateChanged, grabbing ? JNI_TRUE : JNI_FALSE);
    if (attachedHere) (*dvm)->DetachCurrentThread(dvm);
    pojav_environ->isGrabbing = grabbing;
}

/* Returns an SDL_malloc'd string (caller frees), or NULL */
char *sdl3_clipboard_via_dalvik(int action, const char *copyText) {
    JavaVM *dvm = pojav_environ ? pojav_environ->dalvikJavaVMPtr : NULL;
    if (!dvm || !pojav_environ->bridgeClazz) return NULL;

    JNIEnv *env = NULL;
    bool attachedHere = false;
    if ((*dvm)->GetEnv(dvm, (void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        (*dvm)->AttachCurrentThread(dvm, &env, NULL);
        attachedHere = true;
    }
    if (!env) return NULL;

    jstring copyDst = NULL;
    if (copyText) copyDst = (*env)->NewStringUTF(env, copyText);
    jstring result = (jstring) (*env)->CallStaticObjectMethod(
            env, pojav_environ->bridgeClazz,
            pojav_environ->method_accessAndroidClipboard, action, copyDst);
    char *out = NULL;
    if (result) {
        const char *utf = (*env)->GetStringUTFChars(env, result, NULL);
        if (utf) {
            out = (char *) SDL_malloc(strlen(utf) + 1);
            if (out) strcpy(out, utf);
            (*env)->ReleaseStringUTFChars(env, result, utf);
        }
        (*env)->DeleteLocalRef(env, result);
    }
    if (copyDst) (*env)->DeleteLocalRef(env, copyDst);
    if (attachedHere) (*dvm)->DetachCurrentThread(dvm);
    return out;
}

/* ------------------------------------------------------------------ */
/* Shared state                                                         */
/* ------------------------------------------------------------------ */

static Uint32 initializedSubsystems;
static bool windowSetupDone;
static bool relMouseMode, mouseGrabbed, kbGrabbed, textInputActive;
static bool cursorVisible = true;
static int windowLogicalW = 1280, windowLogicalH = 720;
static char windowTitle[256] = "Minecraft";
static void *currentGLContext;
static void *firstGLContext;   /* first context created; used to self-heal swaps after MakeCurrent(NULL) */
static int currentSwapInterval = 1;
static SDL_Keymod currentModState;
static bool keyState[SDL_SCANCODE_COUNT];
static Uint32 mouseButtonState;
static float mouseX, mouseY;
static Uint64 baseTicksNs;
static Uint32 nextCustomEventId = SDL_EVENT_USER;

/* Staging queue: filled by SDL_PumpEvents, drained by SDL_PollEvent */
#define STAGE_CAPACITY 256
static SDL_Event staging[STAGE_CAPACITY];
static char *stagingText[STAGE_CAPACITY]; /* owned by TEXT_INPUT events */
static int stagingHead, stagingCount;

static bool mouseEnteredWindow;

static inline Uint64 now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (Uint64) ts.tv_sec * 1000000000ull + (Uint64) ts.tv_nsec;
}

static void stage_event(const SDL_Event *event, char *ownedText) {
    if (stagingCount >= STAGE_CAPACITY) {
        SDL_free(ownedText);
        return;
    }
    int slot = (stagingHead + stagingCount) % STAGE_CAPACITY;
    if (stagingText[slot]) SDL_free(stagingText[slot]);
    staging[slot] = *event;
    stagingText[slot] = ownedText;
    stagingCount++;
}

/* Owns the text of the most recently delivered/removed TEXT_INPUT event so it
 * stays readable until the next one, matching SDL3 ownership semantics */
static char *deliveredText;

static bool stage_pop(SDL_Event *event) {
    if (stagingCount == 0) return false;
    int slot = stagingHead;
    SDL_Event ev = staging[slot];
    char *text = stagingText[slot];
    stagingText[slot] = NULL;
    stagingHead = (stagingHead + 1) % STAGE_CAPACITY;
    stagingCount--;
    if (event) {
        SDL_free(deliveredText);
        deliveredText = text;
        *event = ev;
    } else {
        SDL_free(text);
    }
    return true;
}

/* ------------------------------------------------------------------ */
/* Event conversion (GLFW-semantic ring -> SDL3 events)                 */
/* ------------------------------------------------------------------ */

static void stage_window_size(int w, int h) {
    SDL_Event ev;
    SDL_zero(ev);
    if (w > 0 && h > 0) windowLogicalW = w, windowLogicalH = h;
    ev.window.type = SDL_EVENT_WINDOW_RESIZED;
    ev.window.timestamp = now_ns() - baseTicksNs;
    ev.window.windowID = SDL3_WINDOW_ID;
    ev.window.data1 = w;
    ev.window.data2 = h;
    stage_event(&ev, NULL);
    ev.window.type = SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED;
    stage_event(&ev, NULL);
}

static void stage_mouse_motion(float x, float y, float dx, float dy) {
    SDL_Event ev;
    SDL_zero(ev);
    if (!mouseEnteredWindow) {
        mouseEnteredWindow = true;
        ev.window.type = SDL_EVENT_WINDOW_MOUSE_ENTER;
        ev.window.timestamp = now_ns() - baseTicksNs;
        ev.window.windowID = SDL3_WINDOW_ID;
        stage_event(&ev, NULL);
    }
    SDL_zero(ev);
    ev.motion.type = SDL_EVENT_MOUSE_MOTION;
    ev.motion.timestamp = now_ns() - baseTicksNs;
    ev.motion.windowID = SDL3_WINDOW_ID;
    ev.motion.x = x;
    ev.motion.y = y;
    ev.motion.xrel = dx;
    ev.motion.yrel = dy;
    ev.motion.state = mouseButtonState;
    stage_event(&ev, NULL);
}

/* UTF-16 code units arrive one at a time; buffer surrogate pairs */
static Uint32 pendingHighSurrogate;

static void stage_text_input(Uint32 codepoint) {
    if (codepoint >= 0xD800 && codepoint <= 0xDBFF) {
        pendingHighSurrogate = codepoint;
        return;
    }
    if (codepoint >= 0xDC00 && codepoint <= 0xDFFF) {
        if (!pendingHighSurrogate) return;
        codepoint = 0x10000 + ((pendingHighSurrogate - 0xD800) << 10) + (codepoint - 0xDC00);
        pendingHighSurrogate = 0;
    }
    pendingHighSurrogate = 0;

    char utf8[8];
    int len = 0;
    if (codepoint < 0x80) utf8[len++] = (char) codepoint;
    else if (codepoint < 0x800) {
        utf8[len++] = (char) (0xC0 | (codepoint >> 6));
        utf8[len++] = (char) (0x80 | (codepoint & 0x3F));
    } else if (codepoint < 0x10000) {
        utf8[len++] = (char) (0xE0 | (codepoint >> 12));
        utf8[len++] = (char) (0x80 | ((codepoint >> 6) & 0x3F));
        utf8[len++] = (char) (0x80 | (codepoint & 0x3F));
    } else {
        utf8[len++] = (char) (0xF0 | (codepoint >> 18));
        utf8[len++] = (char) (0x80 | ((codepoint >> 12) & 0x3F));
        utf8[len++] = (char) (0x80 | ((codepoint >> 6) & 0x3F));
        utf8[len++] = (char) (0x80 | (codepoint & 0x3F));
    }
    utf8[len] = '\0';

    char *text = (char *) SDL_malloc(len + 1);
    if (!text) return;
    memcpy(text, utf8, len + 1);

    SDL_Event ev;
    SDL_zero(ev);
    ev.text.type = SDL_EVENT_TEXT_INPUT;
    ev.text.timestamp = now_ns() - baseTicksNs;
    ev.text.windowID = SDL3_WINDOW_ID;
    ev.text.text = text;
    stage_event(&ev, text);
}

static void convert_ring_event(const GLFWInputEvent *ev) {
    switch (ev->type) {
        case 1000: /* EVENT_TYPE_CHAR */
            stage_text_input((Uint32) ev->i1);
            break;
        case 1001: /* EVENT_TYPE_CHAR_MODS: GLFW-only duplicate of CHAR */
            break;
        case 1002: /* EVENT_TYPE_CURSOR_ENTER */
            mouseEnteredWindow = ev->i1 != 0;
            break;
        case 1004: /* EVENT_TYPE_FRAMEBUFFER_SIZE */
        case 1008: /* EVENT_TYPE_WINDOW_SIZE */
            stage_window_size(ev->i1, ev->i2);
            break;
        case 1005: { /* EVENT_TYPE_KEY */
            int scancode = sdl3_glfw_to_scancode(ev->i1);
            int keycode = sdl3_glfw_to_keycode(ev->i1);
            if (!scancode && !keycode) break;
            SDL_Keymod mod = sdl3_glfwmods_to_sdl(ev->i4);
            currentModState = mod;
            if (scancode > 0 && scancode < SDL_SCANCODE_COUNT)
                keyState[scancode] = ev->i3 != 0;
            SDL_Event out;
            SDL_zero(out);
            out.key.type = ev->i3 != 0 ? SDL_EVENT_KEY_DOWN : SDL_EVENT_KEY_UP;
            out.key.timestamp = now_ns() - baseTicksNs;
            out.key.windowID = SDL3_WINDOW_ID;
            out.key.scancode = (SDL_Scancode) scancode;
            out.key.key = (SDL_Keycode) keycode;
            out.key.mod = mod;
            out.key.down = ev->i3 != 0;
            out.key.repeat = ev->i3 == 2;
            stage_event(&out, NULL);
            break;
        }
        case 1006: { /* EVENT_TYPE_MOUSE_BUTTON */
            Uint8 button = (Uint8) (ev->i1 + 1); /* GLFW is 0-based, SDL is 1-based */
            Uint32 mask = SDL_BUTTON_MASK(button);
            if (ev->i2) mouseButtonState |= mask;
            else mouseButtonState &= ~mask;
            SDL_Event out;
            SDL_zero(out);
            out.button.type = ev->i2 ? SDL_EVENT_MOUSE_BUTTON_DOWN : SDL_EVENT_MOUSE_BUTTON_UP;
            out.button.timestamp = now_ns() - baseTicksNs;
            out.button.windowID = SDL3_WINDOW_ID;
            out.button.button = button;
            out.button.down = ev->i2 != 0;
            out.button.clicks = 1;
            out.button.x = mouseX = (float) pojav_environ->cursorX;
            out.button.y = mouseY = (float) pojav_environ->cursorY;
            stage_event(&out, NULL);
            break;
        }
        case 1007: { /* EVENT_TYPE_SCROLL */
            SDL_Event out;
            SDL_zero(out);
            out.wheel.type = SDL_EVENT_MOUSE_WHEEL;
            out.wheel.timestamp = now_ns() - baseTicksNs;
            out.wheel.windowID = SDL3_WINDOW_ID;
            out.wheel.x = (float) ev->i1;
            out.wheel.y = (float) ev->i2;
            out.wheel.integer_x = ev->i1;
            out.wheel.integer_y = ev->i2;
            out.wheel.direction = SDL_MOUSEWHEEL_NORMAL;
            out.wheel.mouse_x = (float) pojav_environ->cursorX;
            out.wheel.mouse_y = (float) pojav_environ->cursorY;
            stage_event(&out, NULL);
            break;
        }
        default:
            break;
    }
}

static void pump_events(void) {
    if (!pojav_environ) return;
    bind_pojav();

    size_t counter = atomic_load_explicit(&pojav_environ->eventCounter, memory_order_acquire);
    size_t index = pojav_environ->outEventIndex;
    size_t target = index + counter;
    if (target >= EVENT_WINDOW_SIZE) target -= EVENT_WINDOW_SIZE;
    pojav_environ->inEventCount = counter;
    pojav_environ->outTargetIndex = target;

    /* Absolute cursor motion, mirroring the GLFW pump's cursor flush */
    if (pojav_environ->cLastX != pojav_environ->cursorX ||
        pojav_environ->cLastY != pojav_environ->cursorY) {
        float nx = (float) floor(pojav_environ->cursorX);
        float ny = (float) floor(pojav_environ->cursorY);
        stage_mouse_motion(nx, ny, nx - (float) pojav_environ->cLastX, ny - (float) pojav_environ->cLastY);
        pojav_environ->cLastX = pojav_environ->cursorX;
        pojav_environ->cLastY = pojav_environ->cursorY;
    }

    while (index != target) {
        GLFWInputEvent event = pojav_environ->events[index];
        convert_ring_event(&event);
        index++;
        if (index >= EVENT_WINDOW_SIZE) index -= EVENT_WINDOW_SIZE;
    }

    pojav_environ->outEventIndex = target;
    atomic_fetch_sub_explicit(&pojav_environ->eventCounter, pojav_environ->inEventCount, memory_order_acquire);
    pojav_environ->shouldUpdateMouse = false;
    pojav_environ->shouldUpdateMonitorSize = false;
    pojav_environ->monitorSizeConsumed = true;
}

/* ------------------------------------------------------------------ */
/* SDL_Init / subsystems                                                */
/* ------------------------------------------------------------------ */

static void bridge_activate(void) {
    if (!pojav_environ || pojav_environ->sdlBridgeActive) return;
    pojav_environ->sdlBridgeActive = true;
    /* SDL pumps the ring buffer itself; never use the GLFW direct-call path */
    pojav_environ->isUseStackQueueCall = true;
    pojav_environ->isInputReady = true;
    if (!baseTicksNs) baseTicksNs = now_ns();
    SDL3SHIM_LOG("SDL3 bridge activated");
}

bool SDL_Init(SDL_InitFlags flags) {
    SDL3SHIM_LOG("SDL_Init(0x%x)", flags);
    return SDL_InitSubSystem(flags);
}

bool SDL_InitSubSystem(SDL_InitFlags flags) {
    bridge_activate();
    if (!baseTicksNs) baseTicksNs = now_ns();
    initializedSubsystems |= flags;
    return true;
}

void SDL_QuitSubSystem(SDL_InitFlags flags) {
    initializedSubsystems &= ~flags;
}

SDL_InitFlags SDL_WasInit(SDL_InitFlags flags) {
    return flags ? (initializedSubsystems & flags) : initializedSubsystems;
}

void SDL_Quit(void) {
    initializedSubsystems = 0;
}

bool SDL_IsMainThread(void) {
    return true;
}

bool SDL_RunOnMainThread(SDL_MainThreadCallback callback, void *userdata, bool wait_complete) {
    if (callback) callback(userdata);
    (void) wait_complete;
    return true;
}

bool SDL_SetAppMetadata(const char *appname, const char *appversion, const char *appidentifier) {
    (void) appname; (void) appversion; (void) appidentifier;
    return true;
}

bool SDL_SetAppMetadataProperty(const char *name, const char *value) {
    (void) name; (void) value;
    return true;
}

const char *SDL_GetAppMetadataProperty(const char *name) {
    (void) name;
    return "";
}

/* ------------------------------------------------------------------ */
/* Error                                                                */
/* ------------------------------------------------------------------ */

static __thread char errorBuf[256] = "no error";

bool SDL_SetErrorV(const char *fmt, va_list ap) {
    if (fmt && *fmt) vsnprintf(errorBuf, sizeof(errorBuf), fmt, ap);
    else snprintf(errorBuf, sizeof(errorBuf), "unknown error");
    return false;
}

bool SDL_SetError(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    SDL_SetErrorV(fmt, ap);
    va_end(ap);
    return false;
}

bool SDL_OutOfMemory(void) {
    snprintf(errorBuf, sizeof(errorBuf), "Out of memory");
    return false;
}

const char *SDL_GetError(void) {
    return errorBuf;
}

bool SDL_ClearError(void) {
    snprintf(errorBuf, sizeof(errorBuf), "no error");
    return true;
}

/* ------------------------------------------------------------------ */
/* Hints                                                                */
/* ------------------------------------------------------------------ */

#define MAX_HINTS 32
typedef struct { char *name; char *value; } shim_hint;
static shim_hint hints[MAX_HINTS];
static int hintCount;

static const char *hint_get(const char *name) {
    for (int i = 0; i < hintCount; i++)
        if (strcmp(hints[i].name, name) == 0) return hints[i].value;
    return NULL;
}

bool SDL_SetHintWithPriority(const char *name, const char *value, SDL_HintPriority priority) {
    (void) priority;
    if (!name) return false;
    const char *existing = hint_get(name);
    if (existing && value && strcmp(existing, value) == 0) return true;
    for (int i = 0; i < hintCount; i++) {
        if (strcmp(hints[i].name, name) == 0) {
            SDL_free(hints[i].value);
            hints[i].value = value ? SDL_strdup(value) : NULL;
            return true;
        }
    }
    if (hintCount < MAX_HINTS) {
        hints[hintCount].name = SDL_strdup(name);
        hints[hintCount].value = value ? SDL_strdup(value) : NULL;
        hintCount++;
        return true;
    }
    return false;
}

bool SDL_SetHint(const char *name, const char *value) {
    return SDL_SetHintWithPriority(name, value, SDL_HINT_OVERRIDE);
}

const char *SDL_GetHint(const char *name) {
    const char *value = hint_get(name);
    return value ? value : "";
}

bool SDL_GetHintBoolean(const char *name, bool default_value) {
    const char *value = hint_get(name);
    if (!value) return default_value;
    return strcmp(value, "0") != 0 && strcasecmp(value, "false") != 0;
}

bool SDL_ResetHint(const char *name) {
    for (int i = 0; i < hintCount; i++) {
        if (strcmp(hints[i].name, name) == 0) {
            SDL_free(hints[i].name);
            SDL_free(hints[i].value);
            hints[i] = hints[hintCount - 1];
            hintCount--;
            return true;
        }
    }
    return false;
}

void SDL_ResetHints(void) {
    for (int i = 0; i < hintCount; i++) {
        SDL_free(hints[i].name);
        SDL_free(hints[i].value);
    }
    hintCount = 0;
}

typedef struct { char *name; SDL_HintCallback callback; void *userdata; } shim_hint_cb;
static shim_hint_cb hintCallbacks[MAX_HINTS];
static int hintCallbackCount;

bool SDL_AddHintCallback(const char *name, SDL_HintCallback callback, void *userdata) {
    if (hintCallbackCount < MAX_HINTS) {
        hintCallbacks[hintCallbackCount].name = SDL_strdup(name);
        hintCallbacks[hintCallbackCount].callback = callback;
        hintCallbacks[hintCallbackCount].userdata = userdata;
        hintCallbackCount++;
        if (callback) callback(userdata, name, SDL_GetHint(name), SDL_GetHint(name));
        return true;
    }
    return false;
}

void SDL_RemoveHintCallback(const char *name, SDL_HintCallback callback, void *userdata) {
    for (int i = 0; i < hintCallbackCount; i++) {
        if (strcmp(hintCallbacks[i].name, name) == 0 &&
            hintCallbacks[i].callback == callback && hintCallbacks[i].userdata == userdata) {
            SDL_free(hintCallbacks[i].name);
            hintCallbacks[i] = hintCallbacks[hintCallbackCount - 1];
            hintCallbackCount--;
            return;
        }
    }
}

/* ------------------------------------------------------------------ */
/* Properties                                                           */
/* ------------------------------------------------------------------ */

#define MAX_PROPS 96
typedef struct {
    SDL_PropertiesID id;
    char *name;
    SDL_PropertyType type;
    union { Sint64 i; float f; bool b; char *s; void *p; } value;
} shim_prop;
static shim_prop props[MAX_PROPS];
static int propCount;
static SDL_PropertiesID nextPropId = PROPS_GLOBAL + 1;

static shim_prop *prop_find(SDL_PropertiesID id, const char *name) {
    for (int i = 0; i < propCount; i++)
        if (props[i].id == id && strcmp(props[i].name, name) == 0) return &props[i];
    return NULL;
}

static bool prop_set(SDL_PropertiesID id, const char *name, SDL_PropertyType type, shim_prop **out) {
    shim_prop *p = prop_find(id, name);
    if (!p) {
        if (propCount >= MAX_PROPS) return false;
        p = &props[propCount++];
        memset(p, 0, sizeof(*p));
        p->id = id;
        p->name = SDL_strdup(name ? name : "");
    } else if (p->type == SDL_PROPERTY_TYPE_STRING) {
        SDL_free(p->value.s);
        p->value.s = NULL;
    }
    p->type = type;
    if (out) *out = p;
    return true;
}

SDL_PropertiesID SDL_CreateProperties(void) {
    return nextPropId++;
}

SDL_PropertiesID SDL_GetGlobalProperties(void) {
    return PROPS_GLOBAL;
}

bool SDL_CopyProperties(SDL_PropertiesID src, SDL_PropertiesID dst) {
    for (int i = 0; i < propCount; i++) {
        if (props[i].id != src) continue;
        shim_prop *p = NULL;
        if (!prop_set(dst, props[i].name, props[i].type, &p)) return false;
        p->value = props[i].value;
        if (props[i].type == SDL_PROPERTY_TYPE_STRING)
            p->value.s = SDL_strdup(props[i].value.s ? props[i].value.s : "");
    }
    return true;
}

bool SDL_LockProperties(SDL_PropertiesID props_id) { (void) props_id; return true; }
void SDL_UnlockProperties(SDL_PropertiesID props_id) { (void) props_id; }

bool SDL_SetNumberProperty(SDL_PropertiesID id, const char *name, Sint64 value) {
    shim_prop *p;
    if (!prop_set(id, name, SDL_PROPERTY_TYPE_NUMBER, &p)) return false;
    p->value.i = value;
    return true;
}

bool SDL_SetFloatProperty(SDL_PropertiesID id, const char *name, float value) {
    shim_prop *p;
    if (!prop_set(id, name, SDL_PROPERTY_TYPE_FLOAT, &p)) return false;
    p->value.f = value;
    return true;
}

bool SDL_SetBooleanProperty(SDL_PropertiesID id, const char *name, bool value) {
    shim_prop *p;
    if (!prop_set(id, name, SDL_PROPERTY_TYPE_BOOLEAN, &p)) return false;
    p->value.b = value;
    return true;
}

bool SDL_SetStringProperty(SDL_PropertiesID id, const char *name, const char *value) {
    shim_prop *p;
    if (!prop_set(id, name, SDL_PROPERTY_TYPE_STRING, &p)) return false;
    p->value.s = SDL_strdup(value ? value : "");
    return true;
}

bool SDL_SetPointerProperty(SDL_PropertiesID id, const char *name, void *value) {
    shim_prop *p;
    if (!prop_set(id, name, SDL_PROPERTY_TYPE_POINTER, &p)) return false;
    p->value.p = value;
    return true;
}

bool SDL_SetPointerPropertyWithCleanup(SDL_PropertiesID id, const char *name, void *value, SDL_CleanupPropertyCallback cleanup, void *userdata) {
    (void) cleanup; (void) userdata;
    return SDL_SetPointerProperty(id, name, value);
}

bool SDL_HasProperty(SDL_PropertiesID id, const char *name) {
    return prop_find(id, name) != NULL;
}

Sint64 SDL_GetNumberProperty(SDL_PropertiesID id, const char *name, Sint64 default_value) {
    shim_prop *p = prop_find(id, name);
    return p && p->type == SDL_PROPERTY_TYPE_NUMBER ? p->value.i : default_value;
}

float SDL_GetFloatProperty(SDL_PropertiesID id, const char *name, float default_value) {
    shim_prop *p = prop_find(id, name);
    return p && p->type == SDL_PROPERTY_TYPE_FLOAT ? p->value.f : default_value;
}

bool SDL_GetBooleanProperty(SDL_PropertiesID id, const char *name, bool default_value) {
    shim_prop *p = prop_find(id, name);
    return p && p->type == SDL_PROPERTY_TYPE_BOOLEAN ? p->value.b : default_value;
}

const char *SDL_GetStringProperty(SDL_PropertiesID id, const char *name, const char *default_value) {
    shim_prop *p = prop_find(id, name);
    return p && p->type == SDL_PROPERTY_TYPE_STRING ? p->value.s : default_value;
}

void *SDL_GetPointerProperty(SDL_PropertiesID id, const char *name, void *default_value) {
    shim_prop *p = prop_find(id, name);
    return p && p->type == SDL_PROPERTY_TYPE_POINTER ? p->value.p : default_value;
}

SDL_PropertyType SDL_GetPropertyType(SDL_PropertiesID id, const char *name) {
    shim_prop *p = prop_find(id, name);
    return p ? p->type : SDL_PROPERTY_TYPE_INVALID;
}

bool SDL_ClearProperty(SDL_PropertiesID id, const char *name) {
    for (int i = 0; i < propCount; i++) {
        if (props[i].id == id && strcmp(props[i].name, name) == 0) {
            if (props[i].type == SDL_PROPERTY_TYPE_STRING) SDL_free(props[i].value.s);
            SDL_free(props[i].name);
            props[i] = props[propCount - 1];
            propCount--;
            return true;
        }
    }
    return false;
}

void SDL_DestroyProperties(SDL_PropertiesID id) {
    for (int i = 0; i < propCount; ) {
        if (props[i].id == id) {
            if (props[i].type == SDL_PROPERTY_TYPE_STRING) SDL_free(props[i].value.s);
            SDL_free(props[i].name);
            props[i] = props[propCount - 1];
            propCount--;
        } else i++;
    }
}

bool SDL_EnumerateProperties(SDL_PropertiesID id, SDL_EnumeratePropertiesCallback callback, void *userdata) {
    for (int i = 0; i < propCount; i++) {
        if (props[i].id == id) callback(userdata, id, props[i].name);
    }
    return true;
}

/* ------------------------------------------------------------------ */
/* Video: window lifecycle                                              */
/* ------------------------------------------------------------------ */

static void ensure_window_setup(SDL_WindowFlags flags) {
    if (windowSetupDone) return;
    windowSetupDone = true;
    bind_pojav();
    if (!pojav_environ) return;

    pojav_environ->showingWindow = (long) SDL3_WINDOW_ID;

    if ((flags & SDL_WINDOW_VULKAN) && !(flags & SDL_WINDOW_OPENGL)) {
        /* Mirror the GLFW path: vulkan needs no renderer init here */
        pojav_environ->config_renderer = RENDERER_VULKAN;
        SDL3SHIM_LOG("SDL3: window in Vulkan mode");
    } else if (pojav.pojavInit) {
        if (pojav.pojavInit()) SDL3SHIM_LOG("SDL3: pojavInit ok");
        else SDL3SHIM_LOG("SDL3: pojavInit FAILED");
    }

    int physW = pojav_environ->savedWidth, physH = pojav_environ->savedHeight;
    if (physW <= 0 || physH <= 0) {
        physW = ANativeWindow_getWidth(pojav_environ->pojavWindow);
        physH = ANativeWindow_getHeight(pojav_environ->pojavWindow);
    }
    const char *envW = getenv("SDL3_WINDOW_WIDTH");
    const char *envH = getenv("SDL3_WINDOW_HEIGHT");
    if (envW) windowLogicalW = atoi(envW);
    if (envH) windowLogicalH = atoi(envH);
    if (windowLogicalW <= 0) windowLogicalW = physW > 0 ? physW : 1280;
    if (windowLogicalH <= 0) windowLogicalH = physH > 0 ? physH : 720;

    SDL_Event ev;
    SDL_zero(ev);
    ev.window.type = SDL_EVENT_WINDOW_FOCUS_GAINED;
    ev.window.timestamp = now_ns() - baseTicksNs;
    ev.window.windowID = SDL3_WINDOW_ID;
    stage_event(&ev, NULL);
}

SDL_Window *SDL_CreateWindow(const char *title, int w, int h, SDL_WindowFlags flags) {
    SDL3SHIM_LOG("SDL_CreateWindow(\"%s\", %dx%d, flags=0x%llx)", title ? title : "", w, h, (unsigned long long) flags);
    if (title) snprintf(windowTitle, sizeof(windowTitle), "%s", title);
    if (w > 0) windowLogicalW = w;
    if (h > 0) windowLogicalH = h;
    ensure_window_setup(flags);
    return SDL3_WINDOW;
}

SDL_Window *SDL_CreateWindowWithProperties(SDL_PropertiesID props_id) {
    const char *title = SDL_GetStringProperty(props_id, "SDL.window.create.title", NULL);
    Sint64 w = SDL_GetNumberProperty(props_id, "SDL.window.create.width", 0);
    Sint64 h = SDL_GetNumberProperty(props_id, "SDL.window.create.height", 0);
    Sint64 flags = SDL_GetNumberProperty(props_id, "SDL.window.create.flags", 0);
    return SDL_CreateWindow(title, (int) w, (int) h, (SDL_WindowFlags) flags);
}

void SDL_DestroyWindow(SDL_Window *window) {
    (void) window;
    SDL3SHIM_LOG("SDL_DestroyWindow");
}

bool SDL_GetWindowSize(SDL_Window *window, int *w, int *h) {
    (void) window;
    if (w) *w = windowLogicalW;
    if (h) *h = windowLogicalH;
    return true;
}

bool SDL_GetWindowSizeInPixels(SDL_Window *window, int *w, int *h) {
    (void) window;
    if (pojav_environ && pojav_environ->savedWidth > 0) {
        if (w) *w = pojav_environ->savedWidth;
        if (h) *h = pojav_environ->savedHeight;
    } else {
        if (w) *w = windowLogicalW;
        if (h) *h = windowLogicalH;
    }
    return true;
}

bool SDL_SetWindowSize(SDL_Window *window, int w, int h) {
    (void) window;
    if (w > 0) windowLogicalW = w;
    if (h > 0) windowLogicalH = h;
    return true;
}

bool SDL_SetWindowTitle(SDL_Window *window, const char *title) {
    (void) window;
    if (title) snprintf(windowTitle, sizeof(windowTitle), "%s", title);
    return true;
}

const char *SDL_GetWindowTitle(SDL_Window *window) {
    (void) window;
    return windowTitle;
}

SDL_WindowID SDL_GetWindowID(SDL_Window *window) {
    (void) window;
    return SDL3_WINDOW_ID;
}

SDL_Window *SDL_GetWindowFromID(SDL_WindowID id) {
    return id == SDL3_WINDOW_ID ? SDL3_WINDOW : NULL;
}

SDL_Window *SDL_GetWindowParent(SDL_Window *window) { (void) window; return NULL; }
SDL_PropertiesID SDL_GetWindowProperties(SDL_Window *window) { (void) window; return PROPS_GLOBAL; }

SDL_WindowFlags SDL_GetWindowFlags(SDL_Window *window) {
    (void) window;
    SDL_WindowFlags flags = SDL_WINDOW_INPUT_FOCUS | SDL_WINDOW_MOUSE_FOCUS | SDL_WINDOW_RESIZABLE;
    if (currentGLContext) flags |= SDL_WINDOW_OPENGL;
    if (pojav_environ && pojav_environ->config_renderer == RENDERER_VULKAN) flags |= SDL_WINDOW_VULKAN;
    flags |= SDL_WINDOW_HIGH_PIXEL_DENSITY;
    if (mouseGrabbed) flags |= SDL_WINDOW_MOUSE_GRABBED;
    if (kbGrabbed) flags |= SDL_WINDOW_KEYBOARD_GRABBED;
    if (relMouseMode) flags |= SDL_WINDOW_MOUSE_GRABBED;
    return flags;
}

bool SDL_SetWindowPosition(SDL_Window *window, int x, int y) { (void) window; (void) x; (void) y; return true; }
bool SDL_GetWindowPosition(SDL_Window *window, int *x, int *y) { (void) window; if (x) *x = 0; if (y) *y = 0; return true; }
bool SDL_SetWindowMinimumSize(SDL_Window *window, int w, int h) { (void) window; (void) w; (void) h; return true; }
bool SDL_GetWindowMinimumSize(SDL_Window *window, int *w, int *h) { (void) window; if (w) *w = 0; if (h) *h = 0; return true; }
bool SDL_SetWindowMaximumSize(SDL_Window *window, int w, int h) { (void) window; (void) w; (void) h; return true; }
bool SDL_GetWindowMaximumSize(SDL_Window *window, int *w, int *h) { (void) window; if (w) *w = 0; if (h) *h = 0; return true; }
bool SDL_SetWindowBordered(SDL_Window *window, bool bordered) { (void) window; (void) bordered; return true; }
bool SDL_SetWindowResizable(SDL_Window *window, bool resizable) { (void) window; (void) resizable; return true; }
bool SDL_SetWindowAlwaysOnTop(SDL_Window *window, bool on_top) { (void) window; (void) on_top; return true; }
bool SDL_SetWindowFillDocument(SDL_Window *window, bool fill) { (void) window; (void) fill; return true; }
bool SDL_SetWindowFocusable(SDL_Window *window, bool focusable) { (void) window; (void) focusable; return true; }
bool SDL_ShowWindow(SDL_Window *window) { (void) window; return true; }
bool SDL_HideWindow(SDL_Window *window) { (void) window; return true; }
bool SDL_RaiseWindow(SDL_Window *window) { (void) window; return true; }
bool SDL_MaximizeWindow(SDL_Window *window) { (void) window; return true; }
bool SDL_MinimizeWindow(SDL_Window *window) { (void) window; return true; }
bool SDL_RestoreWindow(SDL_Window *window) { (void) window; return true; }
bool SDL_SetWindowFullscreen(SDL_Window *window, bool fullscreen) { (void) window; (void) fullscreen; return true; }
bool SDL_SyncWindow(SDL_Window *window) { (void) window; return true; }
bool SDL_WindowHasSurface(SDL_Window *window) { (void) window; return true; }
SDL_Surface *SDL_GetWindowSurface(SDL_Window *window) { (void) window; return NULL; }
bool SDL_SetWindowSurfaceVSync(SDL_Window *window, int vsync) { (void) window; (void) vsync; return false; }
bool SDL_GetWindowSurfaceVSync(SDL_Window *window, int *vsync) { (void) window; if (vsync) *vsync = 0; return false; }
bool SDL_DestroyWindowSurface(SDL_Window *window) { (void) window; return true; }

bool SDL_SetWindowKeyboardGrab(SDL_Window *window, bool grabbed) {
    SDL3SHIM_LOG("SDL_SetWindowKeyboardGrab(%d)", grabbed);
    (void) window;
    kbGrabbed = grabbed;
    return true;
}
bool SDL_GetWindowKeyboardGrab(SDL_Window *window) { (void) window; return kbGrabbed; }

bool SDL_SetWindowMouseGrab(SDL_Window *window, bool grabbed) {
    SDL3SHIM_LOG("SDL_SetWindowMouseGrab(%d)", grabbed);
    (void) window;
    shim_notify_grab(grabbed);
    mouseGrabbed = grabbed;
    return true;
}
bool SDL_GetWindowMouseGrab(SDL_Window *window) { (void) window; return mouseGrabbed; }
SDL_Window *SDL_GetGrabbedWindow(void) { return mouseGrabbed ? SDL3_WINDOW : NULL; }

bool SDL_SetWindowMouseRect(SDL_Window *window, const SDL_Rect *rect) {
    (void) window; (void) rect;
    return true;
}
const SDL_Rect *SDL_GetWindowMouseRect(SDL_Window *window) { (void) window; return NULL; }
bool SDL_SetWindowOpacity(SDL_Window *window, float opacity) { (void) window; (void) opacity; return true; }
float SDL_GetWindowOpacity(SDL_Window *window) { (void) window; return 1.0f; }
bool SDL_SetWindowParent(SDL_Window *window, SDL_Window *parent) { (void) window; (void) parent; return true; }
bool SDL_SetWindowModal(SDL_Window *window, bool modal) { (void) window; (void) modal; return true; }
bool SDL_SetWindowHitTest(SDL_Window *window, SDL_HitTest callback, void *userdata) {
    (void) window; (void) callback; (void) userdata;
    return false;
}
bool SDL_SetWindowShape(SDL_Window *window, SDL_Surface *shape) { (void) window; (void) shape; return false; }
bool SDL_SetWindowIcon(SDL_Window *window, SDL_Surface *icon) { (void) window; (void) icon; return true; }
bool SDL_SetWindowAspectRatio(SDL_Window *window, float min_aspect, float max_aspect) {
    (void) window; (void) min_aspect; (void) max_aspect;
    return true;
}
bool SDL_GetWindowAspectRatio(SDL_Window *window, float *min_aspect, float *max_aspect) {
    (void) window;
    if (min_aspect) *min_aspect = 0.0f;
    if (max_aspect) *max_aspect = 0.0f;
    return true;
}
bool SDL_GetWindowBordersSize(SDL_Window *window, int *top, int *left, int *bottom, int *right) {
    (void) window;
    if (top) *top = 0;
    if (left) *left = 0;
    if (bottom) *bottom = 0;
    if (right) *right = 0;
    return true;
}
void *SDL_GetWindowICCProfile(SDL_Window *window, size_t *size) {
    (void) window;
    if (size) *size = 0;
    return NULL;
}
SDL_PixelFormat SDL_GetWindowPixelFormat(SDL_Window *window) {
    (void) window;
    return SDL_PIXELFORMAT_XRGB8888;
}
SDL_Window **SDL_GetWindows(int *count) {
    SDL_Window **out = (SDL_Window **) SDL_malloc(sizeof(SDL_Window *));
    if (out) out[0] = SDL3_WINDOW;
    if (count) *count = out ? 1 : 0;
    return out;
}
float SDL_GetWindowPixelDensity(SDL_Window *window) {
    (void) window;
    int pw = 1, ph = 1;
    SDL_GetWindowSizeInPixels(window, &pw, &ph);
    if (windowLogicalW <= 0 || windowLogicalH <= 0) return 1.0f;
    float density = (float) pw / (float) windowLogicalW;
    return density > 0.0f ? density : 1.0f;
}
float SDL_GetWindowDisplayScale(SDL_Window *window) { return SDL_GetWindowPixelDensity(window); }
SDL_DisplayID SDL_GetDisplayForWindow(SDL_Window *window) { (void) window; return SDL3_DISPLAY_ID; }
bool SDL_SetWindowProgressState(SDL_Window *window, SDL_ProgressState state) { (void) window; (void) state; return true; }
bool SDL_SetWindowProgressValue(SDL_Window *window, float value) { (void) window; (void) value; return true; }
SDL_ProgressState SDL_GetWindowProgressState(SDL_Window *window) { (void) window; return SDL_PROGRESS_STATE_NONE; }
float SDL_GetWindowProgressValue(SDL_Window *window) { (void) window; return 0.0f; }
bool SDL_ShowWindowSystemMenu(SDL_Window *window, int x, int y) { (void) window; (void) x; (void) y; return false; }

/* ------------------------------------------------------------------ */
/* Video: displays                                                      */
/* ------------------------------------------------------------------ */

int SDL_GetNumVideoDrivers(void) { return 1; }
const char *SDL_GetVideoDriver(int index) { (void) index; return "pojav"; }
const char *SDL_GetCurrentVideoDriver(void) { return "pojav"; }
SDL_DisplayID SDL_GetPrimaryDisplay(void) { return SDL3_DISPLAY_ID; }
SDL_DisplayID *SDL_GetDisplays(int *count) {
    SDL_DisplayID *out = (SDL_DisplayID *) SDL_malloc(sizeof(SDL_DisplayID));
    if (out) out[0] = SDL3_DISPLAY_ID;
    if (count) *count = out ? 1 : 0;
    return out;
}
SDL_PropertiesID SDL_GetDisplayProperties(SDL_DisplayID displayID) { (void) displayID; return PROPS_GLOBAL; }
const char *SDL_GetDisplayName(SDL_DisplayID displayID) { (void) displayID; return "Display"; }

static void fill_display_rect(SDL_Rect *rect) {
    if (!rect) return;
    rect->x = 0;
    rect->y = 0;
    rect->w = windowLogicalW;
    rect->h = windowLogicalH;
}

bool SDL_GetDisplayBounds(SDL_DisplayID displayID, SDL_Rect *rect) {
    (void) displayID;
    fill_display_rect(rect);
    return true;
}
bool SDL_GetDisplayUsableBounds(SDL_DisplayID displayID, SDL_Rect *rect) {
    (void) displayID;
    fill_display_rect(rect);
    return true;
}
float SDL_GetDisplayContentScale(SDL_DisplayID displayID) { (void) displayID; return 1.0f; }
SDL_DisplayID SDL_GetDisplayForPoint(const SDL_Point *point) { (void) point; return SDL3_DISPLAY_ID; }
SDL_DisplayID SDL_GetDisplayForRect(const SDL_Rect *rect) { (void) rect; return SDL3_DISPLAY_ID; }
SDL_DisplayOrientation SDL_GetNaturalDisplayOrientation(SDL_DisplayID displayID) {
    (void) displayID;
    return SDL_ORIENTATION_LANDSCAPE;
}
SDL_DisplayOrientation SDL_GetCurrentDisplayOrientation(SDL_DisplayID displayID) {
    (void) displayID;
    return SDL_ORIENTATION_LANDSCAPE;
}

static const SDL_DisplayMode *current_display_mode(void) {
    static SDL_DisplayMode mode;
    mode.displayID = SDL3_DISPLAY_ID;
    mode.format = SDL_PIXELFORMAT_XRGB8888;
    mode.w = windowLogicalW;
    mode.h = windowLogicalH;
    mode.pixel_density = 1.0f;
    mode.refresh_rate = 60.0f;
    mode.refresh_rate_numerator = 60;
    mode.refresh_rate_denominator = 1;
    mode.internal = NULL;
    return &mode;
}

const SDL_DisplayMode *SDL_GetDesktopDisplayMode(SDL_DisplayID displayID) {
    (void) displayID;
    return current_display_mode();
}
const SDL_DisplayMode *SDL_GetCurrentDisplayMode(SDL_DisplayID displayID) {
    (void) displayID;
    return current_display_mode();
}
SDL_DisplayMode **SDL_GetFullscreenDisplayModes(SDL_DisplayID displayID, int *count) {
    (void) displayID;
    if (count) *count = 0;
    return NULL;
}
bool SDL_GetClosestFullscreenDisplayMode(SDL_DisplayID displayID, int w, int h, float refresh_rate, bool include_high_density_modes, SDL_DisplayMode *closest) {
    (void) displayID; (void) w; (void) h; (void) refresh_rate; (void) include_high_density_modes;
    if (closest) {
        *closest = *current_display_mode();
        return true;
    }
    return false;
}
SDL_SystemTheme SDL_GetSystemTheme(void) { return SDL_SYSTEM_THEME_UNKNOWN; }
bool SDL_EnableScreenSaver(void) { return true; }
bool SDL_DisableScreenSaver(void) { return true; }
bool SDL_ScreenSaverEnabled(void) { return false; }

/* ------------------------------------------------------------------ */
/* OpenGL                                                               */
/* ------------------------------------------------------------------ */

#define GL_ATTR_COUNT 32
static int glAttrs[GL_ATTR_COUNT];
static bool glAttrSet[GL_ATTR_COUNT];

bool SDL_GL_LoadLibrary(const char *path) { (void) path; return true; }
void SDL_GL_UnloadLibrary(void) {}

static SDL_FunctionPointer gl_proc_address(const char *proc) {
    void *fn = dlsym(RTLD_DEFAULT, proc);
    if (!fn) {
        /* Fall back to EGL's extension lookup when the renderer did not
         * export the symbol globally */
        SDL_FunctionPointer (*eglGetProcAddress)(const char *) =
                (SDL_FunctionPointer (*)(const char *)) dlsym(RTLD_DEFAULT, "eglGetProcAddress");
        if (eglGetProcAddress) fn = (void *) eglGetProcAddress(proc);
    }
    return (SDL_FunctionPointer) fn;
}

SDL_FunctionPointer SDL_GL_GetProcAddress(const char *proc) {
    return gl_proc_address(proc);
}
SDL_FunctionPointer SDL_EGL_GetProcAddress(const char *proc) {
    return gl_proc_address(proc);
}
bool SDL_GL_ExtensionSupported(const char *extension) {
    return extension && gl_proc_address(extension) != NULL;
}
void SDL_GL_ResetAttributes(void) {
    memset(glAttrs, 0, sizeof(glAttrs));
    memset(glAttrSet, 0, sizeof(glAttrSet));
}
bool SDL_GL_SetAttribute(SDL_GLAttr attr, int value) {
    if (attr < 0 || attr >= GL_ATTR_COUNT) return false;
    glAttrs[attr] = value;
    glAttrSet[attr] = true;
    return true;
}
bool SDL_GL_GetAttribute(SDL_GLAttr attr, int *value) {
    if (attr < 0 || attr >= GL_ATTR_COUNT) return false;
    if (value) *value = glAttrs[attr];
    return true;
}

SDL_GLContext SDL_GL_CreateContext(SDL_Window *window) {
    (void) window;
    bind_pojav();
    if (!windowSetupDone) ensure_window_setup(SDL_WINDOW_OPENGL);
    if (!pojav.pojavCreateContext) {
        SDL_SetError("SDL3: pojav bridge unavailable");
        return NULL;
    }
    void *ctx = pojav.pojavCreateContext(NULL);
    if (!ctx) {
        SDL_SetError("SDL3: context creation failed");
        return NULL;
    }
    currentGLContext = ctx;
    if (!firstGLContext) firstGLContext = ctx;
    if (pojav.pojavMakeCurrent) pojav.pojavMakeCurrent(ctx);
    SDL3SHIM_LOG("SDL_GL_CreateContext -> %p", ctx);
    return (SDL_GLContext) ctx;
}

bool SDL_GL_MakeCurrent(SDL_Window *window, SDL_GLContext context) {
    SDL3SHIM_LOG("SDL_GL_MakeCurrent(win=%p ctx=%p)", (void *) window, context);
    (void) window;
    if (!pojav.pojavMakeCurrent) return false;
    pojav.pojavMakeCurrent((void *) context);
    currentGLContext = (void *) context;
    return true;
}
SDL_Window *SDL_GL_GetCurrentWindow(void) { return SDL3_WINDOW; }
SDL_GLContext SDL_GL_GetCurrentContext(void) { return (SDL_GLContext) currentGLContext; }
bool SDL_GL_SetSwapInterval(int interval) {
    if (pojav.pojavSwapInterval) pojav.pojavSwapInterval(interval);
    currentSwapInterval = interval;
    return true;
}
bool SDL_GL_GetSwapInterval(int *interval) {
    if (interval) *interval = currentSwapInterval;
    return true;
}
bool SDL_GL_SwapWindow(SDL_Window *window) {
    (void) window;
    /* Real SDL swaps the window's own surface regardless of which context is
     * current; the pojav bridge needs one. RenderPearl releases the context
     * (MakeCurrent(NULL)) between surface rebuilds, so rebind the first
     * context before presenting. */
    static int swapTrace = 0;
    if (swapTrace++ < 5)
        SDL3SHIM_LOG("SDL_GL_SwapWindow: cur=%p first=%p bridge=%p", currentGLContext, firstGLContext, (void *) pojav.pojavMakeCurrent);
    /* Always rebind: real SDL swaps the window surface regardless of the
     * current context, while the pojav bridge resolves its context state
     * through thread-local storage that must match this call site */
    if ((currentGLContext || firstGLContext) && pojav.pojavMakeCurrent)
        pojav.pojavMakeCurrent((void *) (currentGLContext ? currentGLContext : firstGLContext));
    if (pojav.pojavSwapBuffers) pojav.pojavSwapBuffers();
    else SDL3SHIM_LOG("SDL_GL_SwapWindow: no bridge!");
    return true;
}
bool SDL_GL_DestroyContext(SDL_GLContext context) {
    SDL3SHIM_LOG("SDL_GL_DestroyContext(ctx=%p)", context);
    (void) context;
    if (currentGLContext == (void *) context) currentGLContext = NULL;
    return true;
}
SDL_EGLDisplay SDL_EGL_GetCurrentDisplay(void) {
    void *(*eglGetDisplay)(void *) = (void *(*)(void *)) dlsym(RTLD_DEFAULT, "eglGetDisplay");
    return eglGetDisplay ? (SDL_EGLDisplay) eglGetDisplay(NULL) : NULL;
}
SDL_EGLConfig SDL_EGL_GetCurrentConfig(void) { return NULL; }
SDL_EGLSurface SDL_EGL_GetWindowSurface(SDL_Window *window) { (void) window; return NULL; }
void SDL_EGL_SetAttributeCallbacks(SDL_EGLAttribArrayCallback platformAttribCallback,
                                   SDL_EGLIntArrayCallback surfaceAttribCallback,
                                   SDL_EGLIntArrayCallback contextAttribCallback, void *userdata) {
    (void) platformAttribCallback; (void) surfaceAttribCallback; (void) contextAttribCallback; (void) userdata;
}

/* ------------------------------------------------------------------ */
/* Vulkan                                                               */
/* ------------------------------------------------------------------ */

static void *vkGetInstanceProcAddrFn;

static bool ensure_vulkan_loader(void) {
    if (vkGetInstanceProcAddrFn) return true;
    bind_pojav();
    if (!pojav.maybe_load_vulkan) return false;
    void *lib = pojav.maybe_load_vulkan();
    if (!lib) return false;
    vkGetInstanceProcAddrFn = dlsym(lib, "vkGetInstanceProcAddr");
    return vkGetInstanceProcAddrFn != NULL;
}

bool SDL_Vulkan_LoadLibrary(const char *path) {
    (void) path;
    if (pojav_environ) pojav_environ->config_renderer = RENDERER_VULKAN;
    return ensure_vulkan_loader();
}
void SDL_Vulkan_UnloadLibrary(void) {}
SDL_FunctionPointer SDL_Vulkan_GetVkGetInstanceProcAddr(void) {
    if (!ensure_vulkan_loader()) return NULL;
    return (SDL_FunctionPointer) vkGetInstanceProcAddrFn;
}
char const *const *SDL_Vulkan_GetInstanceExtensions(Uint32 *count) {
    static const char *extensions[] = { "VK_KHR_surface", "VK_KHR_android_surface" };
    if (count) *count = 2;
    return extensions;
}

/* Minimal Vulkan surface types, ABI-compatible with vulkan_core.h (the
 * vendored SDL_vulkan.h only typedefs the dispatchable/non-dispatchable
 * handles) */
typedef int VkResult_shim;
typedef struct VkAndroidSurfaceCreateInfoKHR_shim {
    int sType; /* VkStructureType */
    const void *pNext;
    unsigned int flags;
    struct ANativeWindow *window;
} VkAndroidSurfaceCreateInfoKHR_shim;

typedef VkResult_shim (*PFN_vkCreateAndroidSurfaceKHR_shim)(VkInstance, const VkAndroidSurfaceCreateInfoKHR_shim *, const struct VkAllocationCallbacks *, VkSurfaceKHR *);
typedef void (*PFN_vkDestroySurfaceKHR_shim)(VkInstance, VkSurfaceKHR, const struct VkAllocationCallbacks *);

bool SDL_Vulkan_CreateSurface(SDL_Window *window, VkInstance instance, const struct VkAllocationCallbacks *allocator, VkSurfaceKHR *surface) {
    (void) window;
    if (!ensure_vulkan_loader() || !vkGetInstanceProcAddrFn) {
        SDL_SetError("SDL3: Vulkan loader unavailable");
        return false;
    }
    if (pojav_environ) pojav_environ->config_renderer = RENDERER_VULKAN;

    PFN_vkCreateAndroidSurfaceKHR_shim createAndroidSurface =
            (PFN_vkCreateAndroidSurfaceKHR_shim) ((SDL_FunctionPointer (*)(VkInstance, const char *))
                    vkGetInstanceProcAddrFn)(instance, "vkCreateAndroidSurfaceKHR");
    if (!createAndroidSurface) {
        SDL_SetError("SDL3: vkCreateAndroidSurfaceKHR unavailable");
        return false;
    }
    VkAndroidSurfaceCreateInfoKHR_shim info;
    memset(&info, 0, sizeof(info));
    info.sType = (int) 1000000000; /* VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR */
    info.pNext = NULL;
    info.flags = 0;
    info.window = pojav_environ ? pojav_environ->pojavWindow : NULL;
    VkResult_shim result = createAndroidSurface(instance, &info, allocator, surface);
    SDL3SHIM_LOG("SDL_Vulkan_CreateSurface -> %d (surface=%p)", result, surface ? (void *) *surface : NULL);
    return result == 0;
}

void SDL_Vulkan_DestroySurface(VkInstance instance, VkSurfaceKHR surface, const struct VkAllocationCallbacks *allocator) {
    if (!vkGetInstanceProcAddrFn || instance == NULL || surface == 0) return;
    PFN_vkDestroySurfaceKHR_shim destroySurface =
            (PFN_vkDestroySurfaceKHR_shim) ((SDL_FunctionPointer (*)(VkInstance, const char *))
                    vkGetInstanceProcAddrFn)(instance, "vkDestroySurfaceKHR");
    if (destroySurface) destroySurface(instance, surface, allocator);
}

bool SDL_Vulkan_GetPresentationSupport(VkInstance instance, VkPhysicalDevice physicalDevice, Uint32 queueFamilyIndex) {
    (void) instance; (void) physicalDevice; (void) queueFamilyIndex;
    return true;
}

/* ------------------------------------------------------------------ */
/* Events: public API over the staging queue                            */
/* ------------------------------------------------------------------ */

void SDL_PumpEvents(void) {
    pump_events();
}

bool SDL_PollEvent(SDL_Event *event) {
    if (stagingCount == 0) pump_events();
    return stage_pop(event);
}

bool SDL_WaitEvent(SDL_Event *event) {
    return SDL_WaitEventTimeout(event, -1);
}

bool SDL_WaitEventTimeout(SDL_Event *event, Sint32 timeoutMS) {
    Uint64 deadline = timeoutMS < 0 ? UINT64_MAX : (now_ns() / 1000000ull) + (Uint64) timeoutMS;
    for (;;) {
        if (stagingCount == 0) pump_events();
        if (stagingCount > 0) return stage_pop(event);
        if (timeoutMS >= 0 && (now_ns() / 1000000ull) >= deadline) return false;
        usleep(2000);
    }
}

static void stage_remove_at(int position, bool discard);

int SDL_PeepEvents(SDL_Event *events, int numevents, SDL_EventAction action, Uint32 minType, Uint32 maxType) {
    if (action == SDL_ADDEVENT) {
        for (int i = 0; i < numevents; i++) {
            char *owned = NULL;
            SDL_Event copy = events[i];
            if (copy.type == SDL_EVENT_TEXT_INPUT && copy.text.text) {
                owned = SDL_strdup(copy.text.text);
                copy.text.text = owned;
            }
            stage_event(&copy, owned);
        }
        return numevents;
    }

    int collected = 0;
    int i = 0;
    while (i < stagingCount && collected < numevents) {
        SDL_Event *ev = &staging[(stagingHead + i) % STAGE_CAPACITY];
        if (ev->type < minType || ev->type > maxType) {
            i++;
            continue;
        }
        if (action == SDL_PEEKEVENT) {
            events[collected++] = *ev;
            i++;
        } else { /* SDL_GETEVENT: copy, then compact the ring in place */
            events[collected++] = *ev;
            stage_remove_at(i, false);
        }
    }
    return collected;
}

bool SDL_HasEvent(Uint32 type) {
    for (int i = 0; i < stagingCount; i++) {
        if (staging[(stagingHead + i) % STAGE_CAPACITY].type == type) return true;
    }
    return false;
}

bool SDL_HasEvents(Uint32 minType, Uint32 maxType) {
    for (int i = 0; i < stagingCount; i++) {
        Uint32 type = staging[(stagingHead + i) % STAGE_CAPACITY].type;
        if (type >= minType && type <= maxType) return true;
    }
    return false;
}

static void stage_remove_at(int position, bool discard) {
    int slot = (stagingHead + position) % STAGE_CAPACITY;
    if (stagingText[slot]) {
        if (discard) {
            SDL_free(stagingText[slot]);
        } else {
            SDL_free(deliveredText);
            deliveredText = stagingText[slot];
        }
        stagingText[slot] = NULL;
    }
    for (int j = position + 1; j < stagingCount; j++) {
        int cur = (stagingHead + j) % STAGE_CAPACITY;
        int prev = (stagingHead + j - 1) % STAGE_CAPACITY;
        staging[prev] = staging[cur];
        stagingText[prev] = stagingText[cur];
        stagingText[cur] = NULL;
    }
    stagingCount--;
}

void SDL_FlushEvent(Uint32 type) {
    for (int i = 0; i < stagingCount; ) {
        if (staging[(stagingHead + i) % STAGE_CAPACITY].type == type) stage_remove_at(i, true);
        else i++;
    }
}

void SDL_FlushEvents(Uint32 minType, Uint32 maxType) {
    for (int i = 0; i < stagingCount; ) {
        Uint32 type = staging[(stagingHead + i) % STAGE_CAPACITY].type;
        if (type >= minType && type <= maxType) stage_remove_at(i, true);
        else i++;
    }
}

bool SDL_PushEvent(SDL_Event *event) {
    stage_event(event, NULL);
    return true;
}

static SDL_EventFilter eventFilter;
static void *eventFilterUserdata;

void SDL_SetEventFilter(SDL_EventFilter filter, void *userdata) {
    eventFilter = filter;
    eventFilterUserdata = userdata;
}

bool SDL_GetEventFilter(SDL_EventFilter *filter, void **userdata) {
    if (!eventFilter) return false;
    if (filter) *filter = eventFilter;
    if (userdata) *userdata = eventFilterUserdata;
    return true;
}

static struct { SDL_EventFilter filter; void *userdata; } eventWatches[16];
static int eventWatchCount;

bool SDL_AddEventWatch(SDL_EventFilter filter, void *userdata) {
    if (eventWatchCount >= 16) return false;
    eventWatches[eventWatchCount].filter = filter;
    eventWatches[eventWatchCount].userdata = userdata;
    eventWatchCount++;
    return true;
}

void SDL_RemoveEventWatch(SDL_EventFilter filter, void *userdata) {
    for (int i = 0; i < eventWatchCount; i++) {
        if (eventWatches[i].filter == filter && eventWatches[i].userdata == userdata) {
            eventWatches[i] = eventWatches[eventWatchCount - 1];
            eventWatchCount--;
            return;
        }
    }
}

void SDL_FilterEvents(SDL_EventFilter filter, void *userdata) {
    for (int i = 0; i < stagingCount; ) {
        SDL_Event *ev = &staging[(stagingHead + i) % STAGE_CAPACITY];
        if (!filter(userdata, ev)) stage_remove_at(i, true);
        else i++;
    }
}

void SDL_SetEventEnabled(Uint32 type, bool enabled) {
    (void) type; (void) enabled;
}

bool SDL_EventEnabled(Uint32 type) {
    (void) type;
    return true;
}

Uint32 SDL_RegisterEvents(int numevents) {
    Uint32 base = nextCustomEventId;
    if (numevents > 0) nextCustomEventId += (Uint32) numevents;
    return base;
}

int SDL_GetEventDescription(const SDL_Event *event, char *buf, int buflen) {
    if (!event || !buf || buflen <= 0) return 0;
    return snprintf(buf, (size_t) buflen, "type=%u", (unsigned) event->type);
}

SDL_Window *SDL_GetWindowFromEvent(const SDL_Event *event) {
    if (event && event->window.windowID == SDL3_WINDOW_ID) return SDL3_WINDOW;
    return NULL;
}

/* ------------------------------------------------------------------ */
/* Keyboard                                                             */
/* ------------------------------------------------------------------ */

static char *scancodeNameOverrides[SDL_SCANCODE_COUNT];

SDL_Window *SDL_GetKeyboardFocus(void) { return SDL3_WINDOW; }
const bool *SDL_GetKeyboardState(int *numkeys) {
    if (numkeys) *numkeys = SDL_SCANCODE_COUNT;
    return keyState;
}
void SDL_ResetKeyboard(void) {
    memset(keyState, 0, sizeof(keyState));
    currentModState = SDL_KMOD_NONE;
}
SDL_Keymod SDL_GetModState(void) { return currentModState; }
void SDL_SetModState(SDL_Keymod modstate) { currentModState = modstate; }

SDL_Keycode SDL_GetKeyFromScancode(SDL_Scancode scancode, SDL_Keymod modstate, bool key_event) {
    (void) modstate; (void) key_event;
    return (SDL_Keycode) sdl3_scancode_to_keycode((int) scancode);
}

SDL_Scancode SDL_GetScancodeFromKey(SDL_Keycode key, SDL_Keymod *modstate) {
    if (modstate) *modstate = SDL_KMOD_NONE;
    return (SDL_Scancode) sdl3_keycode_to_scancode((int) key);
}

const char *SDL_GetScancodeName(SDL_Scancode scancode) {
    int index = (int) scancode;
    if (index > 0 && index < SDL_SCANCODE_COUNT && scancodeNameOverrides[index])
        return scancodeNameOverrides[index];
    return sdl3_scancode_name(index);
}

const char *SDL_GetKeyName(SDL_Keycode key) {
    return sdl3_keycode_name((int) key);
}

bool SDL_SetScancodeName(SDL_Scancode scancode, const char *name) {
    int index = (int) scancode;
    if (index <= 0 || index >= SDL_SCANCODE_COUNT) return false;
    SDL_free(scancodeNameOverrides[index]);
    scancodeNameOverrides[index] = name ? SDL_strdup(name) : NULL;
    return true;
}

static int name_matches(const char *a, const char *b) {
    return a && b && strcasecmp(a, b) == 0;
}

SDL_Scancode SDL_GetScancodeFromName(const char *name) {
    for (int scancode = 1; scancode < SDL_SCANCODE_COUNT; scancode++) {
        if (name_matches(sdl3_scancode_name(scancode), name)) return (SDL_Scancode) scancode;
    }
    return SDL_SCANCODE_UNKNOWN;
}

SDL_Keycode SDL_GetKeyFromName(const char *name) {
    SDL_Scancode scancode = SDL_GetScancodeFromName(name);
    return (SDL_Keycode) sdl3_scancode_to_keycode((int) scancode);
}

bool SDL_StartTextInput(SDL_Window *window) {
    (void) window;
    textInputActive = true;
    return true;
}
bool SDL_StartTextInputWithProperties(SDL_Window *window, SDL_PropertiesID props) {
    (void) props;
    return SDL_StartTextInput(window);
}
bool SDL_StopTextInput(SDL_Window *window) {
    (void) window;
    textInputActive = false;
    return true;
}
bool SDL_TextInputActive(SDL_Window *window) {
    (void) window;
    return textInputActive;
}
bool SDL_ClearComposition(SDL_Window *window) { (void) window; return true; }
bool SDL_SetTextInputArea(SDL_Window *window, const SDL_Rect *rect, int cursor) {
    (void) window; (void) rect; (void) cursor;
    return true;
}
bool SDL_GetTextInputArea(SDL_Window *window, SDL_Rect *rect, int *cursor) {
    (void) window;
    fill_display_rect(rect);
    if (cursor) *cursor = 0;
    return true;
}
bool SDL_HasKeyboard(void) { return true; }
SDL_KeyboardID *SDL_GetKeyboards(int *count) {
    SDL_KeyboardID *out = (SDL_KeyboardID *) SDL_malloc(sizeof(SDL_KeyboardID));
    if (out) out[0] = 1;
    if (count) *count = out ? 1 : 0;
    return out;
}
const char *SDL_GetKeyboardNameForID(SDL_KeyboardID instance_id) {
    (void) instance_id;
    return "Android keyboard";
}
bool SDL_HasScreenKeyboardSupport(void) { return false; }

/* ------------------------------------------------------------------ */
/* Mouse                                                                */
/* ------------------------------------------------------------------ */

static SDL_Cursor *currentCursor;
static SDL_Cursor *shim_defaultCursor(void) {
    /* Minecraft passes cursors straight into SDL_SetCursor, whose LWJGL
     * binding rejects NULL; hand out a stable non-NULL dummy */
    static SDL_Cursor *dummy;
    if (!dummy) dummy = (SDL_Cursor *) SDL_malloc(8);
    return dummy;
}

SDL_MouseButtonFlags SDL_GetMouseState(float *x, float *y) {
    if (x) *x = (float) (pojav_environ ? pojav_environ->cursorX : 0.0);
    if (y) *y = (float) (pojav_environ ? pojav_environ->cursorY : 0.0);
    return mouseButtonState;
}
SDL_MouseButtonFlags SDL_GetGlobalMouseState(float *x, float *y) {
    return SDL_GetMouseState(x, y);
}
SDL_MouseButtonFlags SDL_GetRelativeMouseState(float *x, float *y) {
    if (x) *x = 0.0f;
    if (y) *y = 0.0f;
    return mouseButtonState;
}
void SDL_WarpMouseInWindow(SDL_Window *window, float x, float y) {
    SDL3SHIM_LOG("SDL_WarpMouseInWindow(%.1f, %.1f)", x, y);
    (void) window;
    if (!pojav_environ) return;
    pojav_environ->cursorX = x;
    pojav_environ->cursorY = y;
    pojav_environ->cLastX = x;
    pojav_environ->cLastY = y;
}
bool SDL_WarpMouseGlobal(float x, float y) {
    SDL_WarpMouseInWindow(NULL, x, y);
    return true;
}
bool SDL_SetWindowRelativeMouseMode(SDL_Window *window, bool enabled) {
    SDL3SHIM_LOG("SDL_SetWindowRelativeMouseMode(%d)", enabled);
    (void) window;
    /* SDL hides the cursor while relative mode is active; Minecraft keys its
     * free-cursor vs locked-camera input handling off cursor visibility */
    cursorVisible = !enabled;
    shim_notify_grab(enabled);
    relMouseMode = enabled;
    mouseGrabbed = enabled;
    return true;
}
bool SDL_GetWindowRelativeMouseMode(SDL_Window *window) {
    (void) window;
    return relMouseMode;
}
bool SDL_CaptureMouse(bool enabled) { (void) enabled; return true; }
SDL_Cursor *SDL_CreateCursor(const Uint8 *data, const Uint8 *mask, int w, int h, int hot_x, int hot_y) {
    (void) data; (void) mask; (void) w; (void) h; (void) hot_x; (void) hot_y;
    return shim_defaultCursor();
}
SDL_Cursor *SDL_CreateColorCursor(SDL_Surface *surface, int hot_x, int hot_y) {
    (void) surface; (void) hot_x; (void) hot_y;
    return shim_defaultCursor();
}
SDL_Cursor *SDL_CreateAnimatedCursor(SDL_CursorFrameInfo *frames, int frame_count, int hot_x, int hot_y) {
    (void) frames; (void) frame_count; (void) hot_x; (void) hot_y;
    return shim_defaultCursor();
}
SDL_Cursor *SDL_CreateSystemCursor(SDL_SystemCursor id) {
    (void) id;
    return shim_defaultCursor();
}
void SDL_DestroyCursor(SDL_Cursor *cursor) {
    if (cursor && cursor != shim_defaultCursor()) SDL_free(cursor);
}
bool SDL_SetCursor(SDL_Cursor *cursor) { currentCursor = cursor; return true; }
SDL_Cursor *SDL_GetCursor(void) { return currentCursor ? currentCursor : shim_defaultCursor(); }
SDL_Cursor *SDL_GetDefaultCursor(void) { return shim_defaultCursor(); }
bool SDL_ShowCursor(void) { SDL3SHIM_LOG("SDL_ShowCursor"); cursorVisible = true; return true; }
bool SDL_HideCursor(void) { SDL3SHIM_LOG("SDL_HideCursor"); cursorVisible = false; return true; }
bool SDL_CursorVisible(void) {
    static int cvLog = 0;
    if (cvLog++ < 6) SDL3SHIM_LOG("SDL_CursorVisible -> %d", cursorVisible);
    return cursorVisible;
}
SDL_MouseID *SDL_GetMice(int *count) {
    SDL_MouseID *out = (SDL_MouseID *) SDL_malloc(sizeof(SDL_MouseID));
    if (out) out[0] = 1;
    if (count) *count = out ? 1 : 0;
    return out;
}
const char *SDL_GetMouseNameForID(SDL_MouseID instance_id) {
    (void) instance_id;
    return "Android mouse";
}
SDL_Window *SDL_GetMouseFocus(void) { return SDL3_WINDOW; }
bool SDL_HasMouse(void) { return true; }
bool SDL_SetRelativeMouseTransform(SDL_MouseMotionTransformCallback callback, void *userdata) {
    (void) callback; (void) userdata;
    return false;
}

/* ------------------------------------------------------------------ */
/* Clipboard                                                            */
/* ------------------------------------------------------------------ */

bool SDL_SetClipboardText(const char *text) {
    char *result = sdl3_clipboard_via_dalvik(CLIPBOARD_COPY, text);
    SDL_free(result);
    return true;
}

char *SDL_GetClipboardText(void) {
    char *text = sdl3_clipboard_via_dalvik(CLIPBOARD_PASTE, NULL);
    if (!text) {
        text = (char *) SDL_malloc(1);
        if (text) text[0] = '\0';
    }
    return text; /* owned by caller, freed through SDL_free */
}

bool SDL_HasClipboardText(void) {
    char *text = SDL_GetClipboardText();
    bool has = text && text[0] != '\0';
    SDL_free(text);
    return has;
}

bool SDL_ClearClipboardData(void) {
    SDL_SetClipboardText("");
    return true;
}
void *SDL_GetClipboardData(const char *mime_type, size_t *size) {
    (void) mime_type;
    if (size) *size = 0;
    return NULL;
}
char **SDL_GetClipboardMimeTypes(size_t *num_mime_types) {
    if (num_mime_types) *num_mime_types = 0;
    return NULL;
}
bool SDL_HasClipboardData(const char *mime_type) {
    (void) mime_type;
    return false;
}
bool SDL_SetClipboardData(SDL_ClipboardDataCallback callback, SDL_ClipboardCleanupCallback cleanup, void *userdata, const char *const *mime_types, size_t num_mime_types) {
    (void) callback; (void) cleanup; (void) userdata; (void) mime_types; (void) num_mime_types;
    return false;
}
bool SDL_SetPrimarySelectionText(const char *text) {
    return SDL_SetClipboardText(text);
}
char *SDL_GetPrimarySelectionText(void) {
    return SDL_GetClipboardText();
}
bool SDL_HasPrimarySelectionText(void) {
    return SDL_HasClipboardText();
}
