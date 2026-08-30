/*
 * SDL3 compatibility layer for Pojav Glow·Worm, peripheral modules.
 *
 * Companion to sdl3_shim_core.c: standard library shims, timers, logging,
 * filesystem, pixel/surface stubs and the always-empty gamepad/joystick/
 * touch/sensor enumeration. Symbols are the exact dlsym contract of the
 * bundled LWJGL org.lwjgl.sdl bindings.
 */

#define SDL_MAIN_HANDLED

#include <SDL3/SDL.h>
#include <SDL3/SDL_main.h>
#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <android/log.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#include "environ/environ.h"

#define SDL3SHIM_LOGI(...) do { \
    __android_log_print(ANDROID_LOG_INFO, "SDL3Shim", __VA_ARGS__); \
    printf(__VA_ARGS__); printf("\n"); \
} while (0)

extern char **environ;

/* ------------------------------------------------------------------ */
/* Stdinc: allocation, environment, sorting, checksums, PRNG            */
/* ------------------------------------------------------------------ */

void *SDL_malloc(size_t size) { return malloc(size); }
void *SDL_calloc(size_t nmemb, size_t size) { return calloc(nmemb, size); }
void *SDL_realloc(void *mem, size_t size) { return realloc(mem, size); }
void SDL_free(void *mem) { free(mem); }

char *SDL_strdup(const char *str) {
    if (!str) return NULL;
    size_t len = strlen(str) + 1;
    char *out = (char *) SDL_malloc(len);
    if (out) memcpy(out, str, len);
    return out;
}

void *SDL_aligned_alloc(size_t alignment, size_t size) {
    void *mem = NULL;
    if (posix_memalign(&mem, alignment < sizeof(void *) ? sizeof(void *) : alignment, size) != 0) return NULL;
    return mem;
}
void SDL_aligned_free(void *mem) { free(mem); }
int SDL_GetNumAllocations(void) { return 0; }

void SDL_GetMemoryFunctions(SDL_malloc_func *malloc_func, SDL_calloc_func *calloc_func,
                            SDL_realloc_func *realloc_func, SDL_free_func *free_func) {
    if (malloc_func) *malloc_func = SDL_malloc;
    if (calloc_func) *calloc_func = SDL_calloc;
    if (realloc_func) *realloc_func = SDL_realloc;
    if (free_func) *free_func = SDL_free;
}

void SDL_GetOriginalMemoryFunctions(SDL_malloc_func *malloc_func, SDL_calloc_func *calloc_func,
                                    SDL_realloc_func *realloc_func, SDL_free_func *free_func) {
    SDL_GetMemoryFunctions(malloc_func, calloc_func, realloc_func, free_func);
}

bool SDL_SetMemoryFunctions(SDL_malloc_func malloc_func, SDL_calloc_func calloc_func,
                            SDL_realloc_func realloc_func, SDL_free_func free_func) {
    (void) malloc_func; (void) calloc_func; (void) realloc_func; (void) free_func;
    return true;
}

/* Environment: the global process environment is shared, created
 * environments are treated identically which is sufficient for the game */
typedef struct SDL_Environment { bool isGlobal; } SDL_Environment;
static SDL_Environment globalEnvironment = { true };

SDL_Environment *SDL_GetEnvironment(void) { return &globalEnvironment; }
SDL_Environment *SDL_CreateEnvironment(bool populated) {
    SDL_Environment *env = (SDL_Environment *) SDL_malloc(sizeof(SDL_Environment));
    if (env) env->isGlobal = populated;
    return env;
}
void SDL_DestroyEnvironment(SDL_Environment *env) {
    if (env && env != &globalEnvironment) SDL_free(env);
}
const char *SDL_GetEnvironmentVariable(SDL_Environment *env, const char *name) {
    (void) env;
    return getenv(name);
}
char **SDL_GetEnvironmentVariables(SDL_Environment *env) {
    (void) env;
    size_t count = 0;
    while (environ[count]) count++;
    char **out = (char **) SDL_malloc((count + 1) * sizeof(char *));
    if (!out) return NULL;
    for (size_t i = 0; i < count; i++) out[i] = environ[i];
    out[count] = NULL;
    return out;
}
bool SDL_SetEnvironmentVariable(SDL_Environment *env, const char *name, const char *value, bool overwrite) {
    (void) env;
    return setenv(name, value ? value : "", overwrite ? 1 : 0) == 0;
}
bool SDL_UnsetEnvironmentVariable(SDL_Environment *env, const char *name) {
    (void) env;
    return unsetenv(name) == 0;
}
const char *SDL_getenv(const char *name) { return getenv(name); }
const char *SDL_getenv_unsafe(const char *name) { return getenv(name); }
int SDL_setenv_unsafe(const char *name, const char *value, int overwrite) {
    return setenv(name, value, overwrite);
}
int SDL_unsetenv_unsafe(const char *name) { return unsetenv(name); }

static SDL_CompareCallback_r activeCompare;
static void *activeUserdata;

static int qsort_r_trampoline(const void *a, const void *b) {
    return activeCompare(activeUserdata, a, b);
}

void SDL_qsort(void *base, size_t nmemb, size_t size, SDL_CompareCallback compare) {
    qsort(base, nmemb, size, (int (*)(const void *, const void *)) compare);
}

void *SDL_bsearch(const void *key, const void *base, size_t nmemb, size_t size, SDL_CompareCallback compare) {
    return bsearch(key, base, nmemb, size, (int (*)(const void *, const void *)) compare);
}

void SDL_qsort_r(void *base, size_t nmemb, size_t size, SDL_CompareCallback_r compare, void *userdata) {
    activeCompare = compare;
    activeUserdata = userdata;
    qsort(base, nmemb, size, qsort_r_trampoline);
}

void *SDL_bsearch_r(const void *key, const void *base, size_t nmemb, size_t size, SDL_CompareCallback_r compare, void *userdata) {
    activeCompare = compare;
    activeUserdata = userdata;
    return bsearch(key, base, nmemb, size, qsort_r_trampoline);
}

void *SDL_memset4(void *dst, Uint32 val, size_t dwords) {
    Uint32 *out = (Uint32 *) dst;
    for (size_t i = 0; i < dwords; i++) out[i] = val;
    return dst;
}

int SDL_memcmp(const void *s1, const void *s2, size_t len) {
    return memcmp(s1, s2, len);
}

Uint16 SDL_crc16(Uint16 crc, const void *data, size_t len) {
    const Uint8 *buffer = (const Uint8 *) data;
    while (len-- > 0) {
        crc ^= (Uint16) (*buffer++ << 8);
        for (int i = 0; i < 8; i++)
            crc = (crc & 0x8000) ? (Uint16) ((crc << 1) ^ 0x1021) : (Uint16) (crc << 1);
    }
    return crc;
}

Uint32 SDL_crc32(Uint32 crc, const void *data, size_t len) {
    const Uint8 *buffer = (const Uint8 *) data;
    Uint32 value = ~crc;
    while (len-- > 0) {
        value ^= *buffer++;
        for (int k = 0; k < 8; k++)
            value = (value & 1) ? (value >> 1) ^ 0xEDB88320u : (value >> 1);
    }
    return ~value;
}

Uint32 SDL_murmur3_32(const void *data, size_t len, Uint32 seed) {
    const Uint8 *bytes = (const Uint8 *) data;
    Uint32 h1 = seed;
    const size_t nblocks = len / 4;
    for (size_t i = 0; i < nblocks; i++) {
        Uint32 k1;
        memcpy(&k1, bytes + i * 4, 4);
        k1 *= 0xCC9E2D51u;
        k1 = (k1 << 15) | (k1 >> 17);
        k1 *= 0x1B873593u;
        h1 ^= k1;
        h1 = (h1 << 13) | (h1 >> 19);
        h1 = h1 * 5 + 0xE6546B64u;
    }
    const Uint8 *tail = bytes + nblocks * 4;
    Uint32 k1 = 0;
    switch (len & 3) {
        case 3: k1 ^= (Uint32) tail[2] << 16; /* fallthrough */
        case 2: k1 ^= (Uint32) tail[1] << 8;  /* fallthrough */
        case 1:
            k1 ^= tail[0];
            k1 *= 0xCC9E2D51u;
            k1 = (k1 << 15) | (k1 >> 17);
            k1 *= 0x1B873593u;
            h1 ^= k1;
        default:
            break;
    }
    h1 ^= (Uint32) len;
    h1 ^= h1 >> 16;
    h1 *= 0x85EBCA6Bu;
    h1 ^= h1 >> 13;
    h1 *= 0xC2B2AE35u;
    h1 ^= h1 >> 16;
    return h1;
}

static Uint64 randState = 0x9E3779B97F4A7C15ull;

static Uint32 next_rand_bits(Uint64 *state) {
    Uint64 x = *state;
    x ^= x << 13;
    x ^= x >> 7;
    x ^= x << 17;
    *state = x;
    return (Uint32) (x >> 32);
}

void SDL_srand(Uint64 seed) {
    randState = seed ? seed : 0x9E3779B97F4A7C15ull;
}

Sint32 SDL_rand(Sint32 n) {
    if (n <= 0) return 0;
    return (Sint32) (next_rand_bits(&randState) % (Uint32) n);
}
float SDL_randf(void) {
    return (float) (next_rand_bits(&randState) >> 8) * (1.0f / 16777216.0f);
}
Uint32 SDL_rand_bits(void) {
    return next_rand_bits(&randState);
}
Sint32 SDL_rand_r(Uint64 *state, Sint32 n) {
    if (n <= 0) return 0;
    return (Sint32) (next_rand_bits(state) % (Uint32) n);
}
float SDL_randf_r(Uint64 *state) {
    return (float) (next_rand_bits(state) >> 8) * (1.0f / 16777216.0f);
}
Uint32 SDL_rand_bits_r(Uint64 *state) {
    return next_rand_bits(state);
}

/* ------------------------------------------------------------------ */
/* Timer                                                                */
/* ------------------------------------------------------------------ */

static Uint64 shim_start_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (Uint64) ts.tv_sec * 1000000000ull + (Uint64) ts.tv_nsec;
}

Uint64 SDL_GetTicks(void) {
    static Uint64 base;
    if (!base) base = shim_start_ns();
    return (shim_start_ns() - base) / 1000000ull;
}
Uint64 SDL_GetTicksNS(void) {
    static Uint64 base;
    if (!base) base = shim_start_ns();
    return shim_start_ns() - base;
}
Uint64 SDL_GetPerformanceCounter(void) { return shim_start_ns(); }
Uint64 SDL_GetPerformanceFrequency(void) { return 1000000000ull; }
void SDL_Delay(Uint32 ms) { usleep((useconds_t) ms * 1000u); }
void SDL_DelayNS(Uint64 ns) {
    struct timespec ts = { (time_t) (ns / 1000000000ull), (long) (ns % 1000000000ull) };
    nanosleep(&ts, NULL);
}
void SDL_DelayPrecise(Uint64 ns) { SDL_DelayNS(ns); }

#define MAX_TIMERS 16
typedef struct {
    bool used;
    bool stop;
    pthread_t thread;
    Uint64 intervalNs;
    bool nsMode;
    SDL_TimerCallback callback;
    SDL_NSTimerCallback nsCallback;
    void *userdata;
} shim_timer;
static shim_timer timers[MAX_TIMERS];

static void *timer_thread(void *arg) {
    shim_timer *timer = (shim_timer *) arg;
    while (!timer->stop) {
        struct timespec ts = {
            (time_t) (timer->intervalNs / 1000000000ull),
            (long) (timer->intervalNs % 1000000000ull)
        };
        nanosleep(&ts, NULL);
        if (timer->stop) break;
        if (timer->nsMode) {
            if (timer->nsCallback) {
                Uint64 next = timer->nsCallback(timer->userdata, 0, timer->intervalNs);
                if (next == 0) break;
                timer->intervalNs = next;
            }
        } else if (timer->callback) {
            Uint32 next = timer->callback(timer->userdata, 0, (Uint32) (timer->intervalNs / 1000000ull));
            if (next == 0) break;
            timer->intervalNs = (Uint64) next * 1000000ull;
        }
    }
    return NULL;
}

SDL_TimerID SDL_AddTimer(Uint32 interval, SDL_TimerCallback callback, void *userdata) {
    for (int i = 0; i < MAX_TIMERS; i++) {
        if (!timers[i].used) {
            timers[i].used = true;
            timers[i].stop = false;
            timers[i].nsMode = false;
            timers[i].intervalNs = (Uint64) interval * 1000000ull;
            timers[i].callback = callback;
            timers[i].userdata = userdata;
            pthread_create(&timers[i].thread, NULL, timer_thread, &timers[i]);
            return i + 1;
        }
    }
    return 0;
}

SDL_TimerID SDL_AddTimerNS(Uint64 interval, SDL_NSTimerCallback callback, void *userdata) {
    SDL_TimerID id = SDL_AddTimer((Uint32) 1, NULL, userdata);
    if (id > 0 && id <= MAX_TIMERS) {
        shim_timer *timer = &timers[id - 1];
        timer->nsMode = true;
        timer->intervalNs = interval;
        timer->nsCallback = callback;
    }
    return id;
}

bool SDL_RemoveTimer(SDL_TimerID id) {
    if (id <= 0 || id > MAX_TIMERS || !timers[id - 1].used) return false;
    timers[id - 1].stop = true;
    pthread_join(timers[id - 1].thread, NULL);
    timers[id - 1].used = false;
    return true;
}

/* ------------------------------------------------------------------ */
/* Time                                                                 */
/* ------------------------------------------------------------------ */

static void civil_from_days(Sint64 z, int *year, int *month, int *day) {
    z += 719468;
    Sint64 era = (z >= 0 ? z : z - 146096) / 146097;
    unsigned doe = (unsigned) (z - era * 146097);
    unsigned yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    Sint64 y = (Sint64) yoe + era * 400;
    unsigned doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    unsigned mp = (5 * doy + 2) / 153;
    unsigned d = doy - (153 * mp + 2) / 5 + 1;
    unsigned m = mp < 10 ? mp + 3 : mp - 9;
    *year = (int) (y + (m <= 2 ? 1 : 0));
    *month = (int) m;
    *day = (int) d;
}

static Sint64 days_from_civil(int y, int m, int d) {
    y -= m <= 2;
    Sint64 era = (y >= 0 ? y : y - 399) / 400;
    unsigned yoe = (unsigned) (y - era * 400);
    unsigned doy = (153u * (unsigned) (m + (m > 2 ? -3 : 9)) + 2u) / 5u + (unsigned) d - 1u;
    unsigned doe = yoe * 365u + yoe / 4u - yoe / 100u + doy;
    return era * 146097 + (Sint64) doe - 719468;
}

bool SDL_GetCurrentTime(SDL_Time *ticks) {
    if (!ticks) return false;
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    *ticks = (SDL_Time) ts.tv_sec * SDL_NS_PER_SECOND + ts.tv_nsec;
    return true;
}

bool SDL_TimeToDateTime(SDL_Time ticks, SDL_DateTime *dt, bool localTime) {
    if (!dt) return false;
    time_t seconds = (time_t) (ticks / SDL_NS_PER_SECOND);
    struct tm tmValue;
    if (localTime) {
        tzset();
        localtime_r(&seconds, &tmValue);
        dt->utc_offset = (int) tmValue.tm_gmtoff;
    } else {
        gmtime_r(&seconds, &tmValue);
        dt->utc_offset = 0;
    }
    dt->year = tmValue.tm_year + 1900;
    dt->month = tmValue.tm_mon + 1;
    dt->day = tmValue.tm_mday;
    dt->hour = tmValue.tm_hour;
    dt->minute = tmValue.tm_min;
    dt->second = tmValue.tm_sec;
    dt->nanosecond = (int) (ticks % SDL_NS_PER_SECOND);
    dt->day_of_week = SDL_GetDayOfWeek(dt->year, dt->month, dt->day);
    return true;
}

bool SDL_DateTimeToTime(const SDL_DateTime *dt, SDL_Time *ticks) {
    if (!dt || !ticks) return false;
    struct tm tmValue;
    memset(&tmValue, 0, sizeof(tmValue));
    tmValue.tm_year = dt->year - 1900;
    tmValue.tm_mon = dt->month - 1;
    tmValue.tm_mday = dt->day;
    tmValue.tm_hour = dt->hour;
    tmValue.tm_min = dt->minute;
    tmValue.tm_sec = dt->second;
    tmValue.tm_isdst = -1;
    time_t seconds = timegm(&tmValue);
    if (seconds == (time_t) -1) return false;
    seconds -= dt->utc_offset;
    *ticks = (SDL_Time) seconds * SDL_NS_PER_SECOND + dt->nanosecond;
    return true;
}

void SDL_TimeToWindows(SDL_Time ticks, Uint32 *dwLowDateTime, Uint32 *dwHighDateTime) {
    /* FILETIME epoch is 1601-01-01; SDL epoch 1970-01-01 = 11644473600s */
    Uint64 windows100ns = (Uint64) ((ticks / 100) + 116444736000000000ll);
    if (dwLowDateTime) *dwLowDateTime = (Uint32) (windows100ns & 0xFFFFFFFFu);
    if (dwHighDateTime) *dwHighDateTime = (Uint32) (windows100ns >> 32);
}

SDL_Time SDL_TimeFromWindows(Uint32 dwLowDateTime, Uint32 dwHighDateTime) {
    Uint64 windows100ns = ((Uint64) dwHighDateTime << 32) | dwLowDateTime;
    return (SDL_Time) ((windows100ns - 116444736000000000ull) * 100);
}

bool SDL_GetDateTimeLocalePreferences(SDL_DateFormat *dateFormat, SDL_TimeFormat *timeFormat) {
    if (dateFormat) *dateFormat = SDL_DATE_FORMAT_YYYYMMDD;
    if (timeFormat) *timeFormat = SDL_TIME_FORMAT_24HR;
    return true;
}

int SDL_GetDaysInMonth(int year, int month) {
    static const int days[] = { 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 };
    if (month < 1 || month > 12) return -1;
    if (month == 2 && ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0)) return 29;
    return days[month - 1];
}

int SDL_GetDayOfYear(int year, int month, int day) {
    int y = year, m = month;
    return (int) (days_from_civil(y, m, day) - days_from_civil(year, 1, 1)) + 1;
}

int SDL_GetDayOfWeek(int year, int month, int day) {
    Sint64 days = days_from_civil(year, month, day);
    int weekday = (int) ((days + 4) % 7); /* 1970-01-01 was a Thursday */
    if (weekday < 0) weekday += 7;
    return weekday;
}

/* ------------------------------------------------------------------ */
/* Log                                                                  */
/* ------------------------------------------------------------------ */

static void default_log_output(void *userdata, int category, SDL_LogPriority priority, const char *message) {
    (void) userdata;
    android_LogPriority level = ANDROID_LOG_INFO;
    if (priority >= SDL_LOG_PRIORITY_CRITICAL) level = ANDROID_LOG_FATAL;
    else if (priority == SDL_LOG_PRIORITY_ERROR) level = ANDROID_LOG_ERROR;
    else if (priority == SDL_LOG_PRIORITY_WARN) level = ANDROID_LOG_WARN;
    else if (priority <= SDL_LOG_PRIORITY_DEBUG) level = ANDROID_LOG_DEBUG;
    __android_log_print(level, "SDL3Shim", "[%d] %s", category, message ? message : "");
    printf("%s\n", message ? message : "");
}

static SDL_LogOutputFunction logOutput = default_log_output;
static void *logOutputUserdata;
static SDL_LogPriority logPriorities[SDL_LOG_CATEGORY_CUSTOM + 8];
static char *logPriorityPrefixes[8];

void SDL_SetLogPriorities(SDL_LogPriority priority) {
    for (int i = 0; i < SDL_LOG_CATEGORY_CUSTOM + 8; i++) logPriorities[i] = priority;
}
void SDL_SetLogPriority(int category, SDL_LogPriority priority) {
    if (category >= 0 && category < SDL_LOG_CATEGORY_CUSTOM + 8) logPriorities[category] = priority;
}
SDL_LogPriority SDL_GetLogPriority(int category) {
    if (category < 0 || category >= SDL_LOG_CATEGORY_CUSTOM + 8) return SDL_LOG_PRIORITY_INVALID;
    return logPriorities[category] ? logPriorities[category] : SDL_LOG_PRIORITY_INFO;
}
void SDL_ResetLogPriorities(void) {
    memset(logPriorities, 0, sizeof(logPriorities));
}
bool SDL_SetLogPriorityPrefix(SDL_LogPriority priority, const char *prefix) {
    if (priority <= 0 || priority > 8) return false;
    SDL_free(logPriorityPrefixes[priority - 1]);
    logPriorityPrefixes[priority - 1] = prefix ? SDL_strdup(prefix) : NULL;
    return true;
}

static void log_message(int category, SDL_LogPriority priority, const char *fmt, va_list ap) {
    char message[1024];
    vsnprintf(message, sizeof(message), fmt ? fmt : "", ap);
    if (logOutput) logOutput(logOutputUserdata, category, priority, message);
}

void SDL_LogMessageV(int category, SDL_LogPriority priority, const char *fmt, va_list ap) {
    log_message(category, priority, fmt, ap);
}

void SDL_LogMessage(int category, SDL_LogPriority priority, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    log_message(category, priority, fmt, ap);
    va_end(ap);
}

void SDL_Log(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    log_message(SDL_LOG_CATEGORY_APPLICATION, SDL_LOG_PRIORITY_INFO, fmt, ap);
    va_end(ap);
}

void SDL_LogTrace(int category, const char *fmt, ...) {
    va_list ap; va_start(ap, fmt);
    log_message(category, SDL_LOG_PRIORITY_TRACE, fmt, ap); va_end(ap);
}
void SDL_LogVerbose(int category, const char *fmt, ...) {
    va_list ap; va_start(ap, fmt);
    log_message(category, SDL_LOG_PRIORITY_VERBOSE, fmt, ap); va_end(ap);
}
void SDL_LogDebug(int category, const char *fmt, ...) {
    va_list ap; va_start(ap, fmt);
    log_message(category, SDL_LOG_PRIORITY_DEBUG, fmt, ap); va_end(ap);
}
void SDL_LogInfo(int category, const char *fmt, ...) {
    va_list ap; va_start(ap, fmt);
    log_message(category, SDL_LOG_PRIORITY_INFO, fmt, ap); va_end(ap);
}
void SDL_LogWarn(int category, const char *fmt, ...) {
    va_list ap; va_start(ap, fmt);
    log_message(category, SDL_LOG_PRIORITY_WARN, fmt, ap); va_end(ap);
}
void SDL_LogError(int category, const char *fmt, ...) {
    va_list ap; va_start(ap, fmt);
    log_message(category, SDL_LOG_PRIORITY_ERROR, fmt, ap); va_end(ap);
}
void SDL_LogCritical(int category, const char *fmt, ...) {
    va_list ap; va_start(ap, fmt);
    log_message(category, SDL_LOG_PRIORITY_CRITICAL, fmt, ap); va_end(ap);
}

SDL_LogOutputFunction SDL_GetDefaultLogOutputFunction(void) {
    return default_log_output;
}
void SDL_GetLogOutputFunction(SDL_LogOutputFunction *callback, void **userdata) {
    if (callback) *callback = logOutput;
    if (userdata) *userdata = logOutputUserdata;
}
void SDL_SetLogOutputFunction(SDL_LogOutputFunction callback, void *userdata) {
    logOutput = callback;
    logOutputUserdata = userdata;
}

/* ------------------------------------------------------------------ */
/* Main / misc / system                                                 */
/* ------------------------------------------------------------------ */

void SDL_SetMainReady(void) {}
int SDL_RunApp(int argc, char *argv[], SDL_main_func mainFunction, void *reserved) {
    (void) reserved;
    return mainFunction ? mainFunction(argc, argv) : -1;
}
int SDL_EnterAppMainCallbacks(int argc, char *argv[], SDL_AppInit_func appinit, SDL_AppIterate_func appiter, SDL_AppEvent_func appevent, SDL_AppQuit_func appquit) {
    (void) argv; (void) appiter; (void) appevent; (void) appquit;
    void *appState = NULL;
    if (appinit) return (int) appinit(&appState, argc, (char **) argv);
    return -1;
}
bool SDL_RegisterApp(const char *name, Uint32 style, void *hInst) {
    (void) name; (void) style; (void) hInst;
    return true;
}
void SDL_UnregisterApp(void) {}

bool SDL_OpenURL(const char *url) {
    if (!url || !url[0]) return false;
    extern char *sdl3_clipboard_via_dalvik(int action, const char *copyText);
    char *result = sdl3_clipboard_via_dalvik(2002 /* CLIPBOARD_OPEN */, url);
    SDL_free(result);
    return true;
}

bool SDL_IsTablet(void) { return false; }
bool SDL_IsTV(void) { return false; }
SDL_Sandbox SDL_GetSandbox(void) { return SDL_SANDBOX_NONE; }
bool SDL_SetLinuxThreadPriority(Sint64 threadID, int priority) {
    (void) threadID; (void) priority;
    return false;
}
bool SDL_SetLinuxThreadPriorityAndPolicy(Sint64 threadID, int sdlPriority, int schedPolicy) {
    (void) threadID; (void) sdlPriority; (void) schedPolicy;
    return false;
}

/* ------------------------------------------------------------------ */
/* Thread-local storage                                                 */
/* ------------------------------------------------------------------ */

/* SDL_TLSID is an SDL_AtomicInt whose value holds the pthread key */
void *SDL_GetTLS(SDL_TLSID *id) {
    if (!id || id->value == 0) return NULL;
    return pthread_getspecific((pthread_key_t) id->value);
}

bool SDL_SetTLS(SDL_TLSID *id, const void *value, SDL_TLSDestructorCallback destructor) {
    if (!id) return false;
    if (id->value == 0) {
        pthread_key_t key;
        if (pthread_key_create(&key, (void (*)(void *)) destructor) != 0) return false;
        id->value = (int) key;
    }
    return pthread_setspecific((pthread_key_t) id->value, value) == 0;
}

void SDL_CleanupTLS(void) {}

/* ------------------------------------------------------------------ */
/* GUID / LoadSO / Locale / Platform                                    */
/* ------------------------------------------------------------------ */

void SDL_GUIDToString(SDL_GUID guid, char *pszGUID, int cbGUID) {
    if (!pszGUID || cbGUID <= 0) return;
    int offset = 0;
    for (int i = 0; i < 16 && offset < cbGUID - 1; i++)
        offset += snprintf(pszGUID + offset, (size_t) (cbGUID - offset), "%.2x", guid.data[i]);
}

SDL_GUID SDL_StringToGUID(const char *pchGUID) {
    SDL_GUID guid;
    memset(&guid, 0, sizeof(guid));
    if (!pchGUID) return guid;
    for (int i = 0; i < 16 && strlen(pchGUID) >= (size_t) (i * 2 + 2); i++) {
        char hex[3] = { pchGUID[i * 2], pchGUID[i * 2 + 1], '\0' };
        guid.data[i] = (Uint8) strtoul(hex, NULL, 16);
    }
    return guid;
}

SDL_SharedObject *SDL_LoadObject(const char *sofile) {
    return (SDL_SharedObject *) dlopen(sofile, RTLD_NOW | RTLD_LOCAL);
}
SDL_FunctionPointer SDL_LoadFunction(SDL_SharedObject *handle, const char *name) {
    return (SDL_FunctionPointer) dlsym((void *) handle, name);
}
void SDL_UnloadObject(SDL_SharedObject *handle) {
    dlclose((void *) handle);
}

static SDL_Locale shimLocaleEnUs = { "en", "US" };
static SDL_Locale *shimLocales[] = { &shimLocaleEnUs, NULL };

SDL_Locale **SDL_GetPreferredLocales(int *count) {
    if (count) *count = 1;
    return shimLocales;
}

const char *SDL_GetPlatform(void) {
    return "Android";
}

/* ------------------------------------------------------------------ */
/* Filesystem                                                           */
/* ------------------------------------------------------------------ */

static char *duplicate_with_trailing_slash(const char *path) {
    if (!path) return NULL;
    size_t len = strlen(path);
    char *out = (char *) SDL_malloc(len + 3);
    if (!out) return NULL;
    memcpy(out, path, len);
    if (len && out[len - 1] != '/') out[len++] = '/';
    out[len] = '\0';
    return out;
}

static const char *game_home_dir(void) {
    const char *pref = getenv("SDL3_PREF_DIR");
    return pref ? pref : ".";
}

const char *SDL_GetBasePath(void) {
    static char *basePath;
    if (!basePath) basePath = duplicate_with_trailing_slash(game_home_dir());
    return basePath;
}

char *SDL_GetPrefPath(const char *org, const char *app) {
    char path[1024];
    if (org && app) snprintf(path, sizeof(path), "%s/%s/%s", game_home_dir(), org, app);
    else if (app) snprintf(path, sizeof(path), "%s/%s", game_home_dir(), app);
    else snprintf(path, sizeof(path), "%s", game_home_dir());
    return duplicate_with_trailing_slash(path);
}

const char *SDL_GetUserFolder(SDL_Folder folder) {
    (void) folder;
    return SDL_GetBasePath();
}

bool SDL_CreateDirectory(const char *path) {
    if (!path) return false;
    char copy[1024];
    snprintf(copy, sizeof(copy), "%s", path);
    for (char *p = copy + 1; *p; p++) {
        if (*p == '/') {
            *p = '\0';
            if (mkdir(copy, 0777) != 0 && errno != EEXIST) return false;
            *p = '/';
        }
    }
    return mkdir(copy, 0777) == 0 || errno == EEXIST;
}

bool SDL_EnumerateDirectory(const char *path, SDL_EnumerateDirectoryCallback callback, void *userdata) {
    DIR *dir = opendir(path);
    if (!dir) return false;
    struct dirent *entry;
    bool result = true;
    while ((entry = readdir(dir)) != NULL) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
        char child[1024];
        snprintf(child, sizeof(child), "%s/%s", path, entry->d_name);
        if (!callback(userdata, child, entry->d_name)) {
            result = false;
            break;
        }
    }
    closedir(dir);
    return result;
}

bool SDL_RemovePath(const char *path) { return remove(path) == 0; }
bool SDL_RenamePath(const char *oldpath, const char *newpath) { return rename(oldpath, newpath) == 0; }
bool SDL_CopyFile(const char *oldpath, const char *newpath) {
    FILE *src = fopen(oldpath, "rb");
    if (!src) return false;
    FILE *dst = fopen(newpath, "wb");
    if (!dst) { fclose(src); return false; }
    char buffer[65536];
    size_t read;
    bool ok = true;
    while ((read = fread(buffer, 1, sizeof(buffer), src)) > 0) {
        if (fwrite(buffer, 1, read, dst) != read) { ok = false; break; }
    }
    fclose(src);
    fclose(dst);
    return ok;
}

bool SDL_GetPathInfo(const char *path, SDL_PathInfo *info) {
    struct stat st;
    if (!path || !info || stat(path, &st) != 0) {
        if (info) memset(info, 0, sizeof(*info));
        return false;
    }
    memset(info, 0, sizeof(*info));
    info->type = S_ISDIR(st.st_mode) ? SDL_PATHTYPE_DIRECTORY :
                 S_ISREG(st.st_mode) ? SDL_PATHTYPE_FILE : SDL_PATHTYPE_OTHER;
    info->size = (Uint64) st.st_size;
    info->modify_time = (SDL_Time) st.st_mtime * SDL_NS_PER_SECOND;
    info->create_time = info->modify_time;
    info->access_time = info->modify_time;
    return true;
}

char **SDL_GlobDirectory(const char *path, const char *pattern, SDL_GlobFlags flags, int *count) {
    (void) path; (void) pattern; (void) flags;
    if (count) *count = 0;
    return NULL;
}

char *SDL_GetCurrentDirectory(void) {
    char buffer[1024];
    if (!getcwd(buffer, sizeof(buffer))) return NULL;
    return SDL_strdup(buffer);
}

/* ------------------------------------------------------------------ */
/* Rect                                                                 */
/* ------------------------------------------------------------------ */

bool SDL_HasRectIntersection(const SDL_Rect *A, const SDL_Rect *B) {
    int ax = A->x + A->w - 1, ay = A->y + A->h - 1;
    int bx = B->x + B->w - 1, by = B->y + B->h - 1;
    return A->x <= bx && B->x <= ax && A->y <= by && B->y <= ay;
}

bool SDL_GetRectIntersection(const SDL_Rect *A, const SDL_Rect *B, SDL_Rect *result) {
    if (!SDL_HasRectIntersection(A, B)) {
        if (result) SDL_zerop(result);
        return false;
    }
    if (result) {
        int maxx = A->x > B->x ? A->x : B->x;
        int maxy = A->y > B->y ? A->y : B->y;
        int minx = A->x + A->w < B->x + B->w ? A->x + A->w : B->x + B->w;
        int miny = A->y + A->h < B->y + B->h ? A->y + A->h : B->y + B->h;
        result->x = maxx;
        result->y = maxy;
        result->w = minx - maxx + 1;
        result->h = miny - maxy + 1;
    }
    return true;
}

bool SDL_GetRectUnion(const SDL_Rect *A, const SDL_Rect *B, SDL_Rect *result) {
    if (!A || !B || !result) return false;
    int minx = A->x < B->x ? A->x : B->x;
    int miny = A->y < B->y ? A->y : B->y;
    int maxx = A->x + A->w > B->x + B->w ? A->x + A->w : B->x + B->w;
    int maxy = A->y + A->h > B->y + B->h ? A->y + A->h : B->y + B->h;
    result->x = minx;
    result->y = miny;
    result->w = maxx - minx;
    result->h = maxy - miny;
    return true;
}

bool SDL_GetRectEnclosingPoints(const SDL_Point *points, int count, const SDL_Rect *clip, SDL_Rect *result) {
    if (!points || count <= 0 || !result) return false;
    int minx = points[0].x, miny = points[0].y, maxx = points[0].x, maxy = points[0].y;
    for (int i = 1; i < count; i++) {
        if (points[i].x < minx) minx = points[i].x;
        if (points[i].y < miny) miny = points[i].y;
        if (points[i].x > maxx) maxx = points[i].x;
        if (points[i].y > maxy) maxy = points[i].y;
    }
    SDL_Rect bounds = { minx, miny, maxx - minx + 1, maxy - miny + 1 };
    if (clip && !SDL_HasRectIntersection(clip, &bounds)) return false;
    if (clip && !SDL_GetRectIntersection(clip, &bounds, &bounds)) return false;
    *result = bounds;
    return true;
}

bool SDL_GetRectAndLineIntersection(const SDL_Rect *rect, int *X1, int *Y1, int *X2, int *Y2) {
    (void) rect; (void) X1; (void) Y1; (void) X2; (void) Y2;
    return false;
}

bool SDL_HasRectIntersectionFloat(const SDL_FRect *A, const SDL_FRect *B) {
    if (!A || !B) return false;
    float ax = A->x + A->w, ay = A->y + A->h;
    float bx = B->x + B->w, by = B->y + B->h;
    return A->x <= bx && B->x <= ax && A->y <= by && B->y <= ay;
}
bool SDL_GetRectIntersectionFloat(const SDL_FRect *A, const SDL_FRect *B, SDL_FRect *result) {
    if (!SDL_HasRectIntersectionFloat(A, B)) {
        if (result) SDL_zerop(result);
        return false;
    }
    if (result) {
        result->x = A->x > B->x ? A->x : B->x;
        result->y = A->y > B->y ? A->y : B->y;
        float maxx = A->x + A->w < B->x + B->w ? A->x + A->w : B->x + B->w;
        float maxy = A->y + A->h < B->y + B->h ? A->y + A->h : B->y + B->h;
        result->w = maxx - result->x;
        result->h = maxy - result->y;
    }
    return true;
}
bool SDL_GetRectUnionFloat(const SDL_FRect *A, const SDL_FRect *B, SDL_FRect *result) {
    if (!A || !B || !result) return false;
    result->x = A->x < B->x ? A->x : B->x;
    result->y = A->y < B->y ? A->y : B->y;
    float maxx = A->x + A->w > B->x + B->w ? A->x + A->w : B->x + B->w;
    float maxy = A->y + A->h > B->y + B->h ? A->y + A->h : B->y + B->h;
    result->w = maxx - result->x;
    result->h = maxy - result->y;
    return true;
}
bool SDL_GetRectEnclosingPointsFloat(const SDL_FPoint *points, int count, const SDL_FRect *clip, SDL_FRect *result) {
    if (!points || count <= 0 || !result) return false;
    SDL_zerop(result);
    return true;
}
bool SDL_GetRectAndLineIntersectionFloat(const SDL_FRect *rect, float *X1, float *Y1, float *X2, float *Y2) {
    (void) rect; (void) X1; (void) Y1; (void) X2; (void) Y2;
    return false;
}

/* ------------------------------------------------------------------ */
/* Touch / Sensor / Gamepad / Joystick: always empty                    */
/* ------------------------------------------------------------------ */

SDL_TouchID *SDL_GetTouchDevices(int *count) {
    if (count) *count = 0;
    return NULL;
}
const char *SDL_GetTouchDeviceName(SDL_TouchID touchID) { (void) touchID; return NULL; }
SDL_TouchDeviceType SDL_GetTouchDeviceType(SDL_TouchID touchID) {
    (void) touchID;
    return SDL_TOUCH_DEVICE_INVALID;
}
SDL_Finger **SDL_GetTouchFingers(SDL_TouchID touchID, int *count) {
    (void) touchID;
    if (count) *count = 0;
    return NULL;
}

SDL_SensorID *SDL_GetSensors(int *count) {
    if (count) *count = 0;
    return NULL;
}
const char *SDL_GetSensorNameForID(SDL_SensorID instance_id) { (void) instance_id; return NULL; }
SDL_SensorType SDL_GetSensorTypeForID(SDL_SensorID instance_id) {
    (void) instance_id;
    return SDL_SENSOR_INVALID;
}
int SDL_GetSensorNonPortableTypeForID(SDL_SensorID instance_id) {
    (void) instance_id;
    return -1;
}
SDL_Sensor *SDL_OpenSensor(SDL_SensorID instance_id) { (void) instance_id; return NULL; }
SDL_Sensor *SDL_GetSensorFromID(SDL_SensorID instance_id) { (void) instance_id; return NULL; }
SDL_PropertiesID SDL_GetSensorProperties(SDL_Sensor *sensor) { (void) sensor; return 0; }
const char *SDL_GetSensorName(SDL_Sensor *sensor) { (void) sensor; return NULL; }
SDL_SensorType SDL_GetSensorType(SDL_Sensor *sensor) {
    (void) sensor;
    return SDL_SENSOR_INVALID;
}
int SDL_GetSensorNonPortableType(SDL_Sensor *sensor) { (void) sensor; return -1; }
SDL_SensorID SDL_GetSensorID(SDL_Sensor *sensor) { (void) sensor; return 0; }
bool SDL_GetSensorData(SDL_Sensor *sensor, float *data, int num_values) {
    (void) sensor; (void) data; (void) num_values;
    return false;
}
void SDL_CloseSensor(SDL_Sensor *sensor) { (void) sensor; }

void SDL_LockJoysticks(void) {}
void SDL_UnlockJoysticks(void) {}
bool SDL_HasJoystick(void) { return false; }
SDL_JoystickID *SDL_GetJoysticks(int *count) {
    if (count) *count = 0;
    return NULL;
}
const char *SDL_GetJoystickNameForID(SDL_JoystickID instance_id) { (void) instance_id; return NULL; }
const char *SDL_GetJoystickPathForID(SDL_JoystickID instance_id) { (void) instance_id; return NULL; }
int SDL_GetJoystickPlayerIndexForID(SDL_JoystickID instance_id) { (void) instance_id; return -1; }
SDL_GUID SDL_GetJoystickGUIDForID(SDL_JoystickID instance_id) {
    SDL_GUID guid;
    memset(&guid, 0, sizeof(guid));
    (void) instance_id;
    return guid;
}
Uint16 SDL_GetJoystickVendorForID(SDL_JoystickID instance_id) { (void) instance_id; return 0; }
Uint16 SDL_GetJoystickProductForID(SDL_JoystickID instance_id) { (void) instance_id; return 0; }
Uint16 SDL_GetJoystickProductVersionForID(SDL_JoystickID instance_id) { (void) instance_id; return 0; }
SDL_JoystickType SDL_GetJoystickTypeForID(SDL_JoystickID instance_id) {
    (void) instance_id;
    return SDL_JOYSTICK_TYPE_UNKNOWN;
}
SDL_Joystick *SDL_OpenJoystick(SDL_JoystickID instance_id) { (void) instance_id; return NULL; }
SDL_Joystick *SDL_GetJoystickFromID(SDL_JoystickID instance_id) { (void) instance_id; return NULL; }
SDL_Joystick *SDL_GetJoystickFromPlayerIndex(int player_index) { (void) player_index; return NULL; }
SDL_JoystickID SDL_AttachVirtualJoystick(const SDL_VirtualJoystickDesc *desc) {
    (void) desc;
    return 0;
}
bool SDL_DetachVirtualJoystick(SDL_JoystickID instance_id) { (void) instance_id; return false; }
bool SDL_IsJoystickVirtual(SDL_JoystickID instance_id) { (void) instance_id; return false; }
bool SDL_SetJoystickVirtualAxis(SDL_Joystick *joystick, int axis, Sint16 value) {
    (void) joystick; (void) axis; (void) value;
    return false;
}
bool SDL_SetJoystickVirtualBall(SDL_Joystick *joystick, int ball, Sint16 xrel, Sint16 yrel) {
    (void) joystick; (void) ball; (void) xrel; (void) yrel;
    return false;
}
bool SDL_SetJoystickVirtualButton(SDL_Joystick *joystick, int button, bool down) {
    (void) joystick; (void) button; (void) down;
    return false;
}
bool SDL_SetJoystickVirtualHat(SDL_Joystick *joystick, int hat, Uint8 value) {
    (void) joystick; (void) hat; (void) value;
    return false;
}
bool SDL_SetJoystickVirtualTouchpad(SDL_Joystick *joystick, int touchpad, int finger, bool down, float x, float y, float pressure) {
    (void) joystick; (void) touchpad; (void) finger; (void) down; (void) x; (void) y; (void) pressure;
    return false;
}
SDL_PropertiesID SDL_GetJoystickProperties(SDL_Joystick *joystick) { (void) joystick; return 0; }
const char *SDL_GetJoystickName(SDL_Joystick *joystick) { (void) joystick; return NULL; }
const char *SDL_GetJoystickPath(SDL_Joystick *joystick) { (void) joystick; return NULL; }
int SDL_GetJoystickPlayerIndex(SDL_Joystick *joystick) { (void) joystick; return -1; }
bool SDL_SetJoystickPlayerIndex(SDL_Joystick *joystick, int player_index) {
    (void) joystick; (void) player_index;
    return false;
}
SDL_GUID SDL_GetJoystickGUID(SDL_Joystick *joystick) {
    SDL_GUID guid;
    memset(&guid, 0, sizeof(guid));
    (void) joystick;
    return guid;
}
Uint16 SDL_GetJoystickVendor(SDL_Joystick *joystick) { (void) joystick; return 0; }
Uint16 SDL_GetJoystickProduct(SDL_Joystick *joystick) { (void) joystick; return 0; }
Uint16 SDL_GetJoystickProductVersion(SDL_Joystick *joystick) { (void) joystick; return 0; }
Uint16 SDL_GetJoystickFirmwareVersion(SDL_Joystick *joystick) { (void) joystick; return 0; }
const char *SDL_GetJoystickSerial(SDL_Joystick *joystick) { (void) joystick; return NULL; }
SDL_JoystickType SDL_GetJoystickType(SDL_Joystick *joystick) {
    (void) joystick;
    return SDL_JOYSTICK_TYPE_UNKNOWN;
}
void SDL_GetJoystickGUIDInfo(SDL_GUID guid, Uint16 *vendor, Uint16 *product, Uint16 *version, Uint16 *crc16) {
    if (vendor) *vendor = 0;
    if (product) *product = 0;
    if (version) *version = 0;
    if (crc16) *crc16 = 0;
    (void) guid;
}
SDL_JoystickID SDL_GetJoystickID(SDL_Joystick *joystick) { (void) joystick; return 0; }
void SDL_SetJoystickEventsEnabled(bool enabled) { (void) enabled; }
bool SDL_JoystickEventsEnabled(void) { return false; }
void SDL_UpdateJoysticks(void) {}
Sint16 SDL_GetJoystickAxis(SDL_Joystick *joystick, int axis) { (void) joystick; (void) axis; return 0; }
bool SDL_GetJoystickAxisInitialState(SDL_Joystick *joystick, int axis, Sint16 *state) {
    (void) joystick; (void) axis;
    if (state) *state = 0;
    return false;
}
bool SDL_GetJoystickBall(SDL_Joystick *joystick, int ball, int *dx, int *dy) {
    (void) joystick; (void) ball;
    if (dx) *dx = 0;
    if (dy) *dy = 0;
    return false;
}
Uint8 SDL_GetJoystickHat(SDL_Joystick *joystick, int hat) { (void) joystick; (void) hat; return 0; }
bool SDL_GetJoystickButton(SDL_Joystick *joystick, int button) { (void) joystick; (void) button; return false; }
bool SDL_SetJoystickLED(SDL_Joystick *joystick, Uint8 red, Uint8 green, Uint8 blue) {
    (void) joystick; (void) red; (void) green; (void) blue;
    return false;
}
SDL_JoystickConnectionState SDL_GetJoystickConnectionState(SDL_Joystick *joystick) {
    (void) joystick;
    return SDL_JOYSTICK_CONNECTION_INVALID;
}
SDL_PowerState SDL_GetJoystickPowerInfo(SDL_Joystick *joystick, int *percent) {
    (void) joystick;
    if (percent) *percent = -1;
    return SDL_POWERSTATE_UNKNOWN;
}

bool SDL_HasGamepad(void) { return false; }
SDL_JoystickID *SDL_GetGamepads(int *count) {
    if (count) *count = 0;
    return NULL;
}
bool SDL_IsGamepad(SDL_JoystickID instance_id) { (void) instance_id; return false; }
const char *SDL_GetGamepadNameForID(SDL_JoystickID instance_id) { (void) instance_id; return NULL; }
const char *SDL_GetGamepadPathForID(SDL_JoystickID instance_id) { (void) instance_id; return NULL; }
int SDL_GetGamepadPlayerIndexForID(SDL_JoystickID instance_id) { (void) instance_id; return -1; }
SDL_GUID SDL_GetGamepadGUIDForID(SDL_JoystickID instance_id) {
    SDL_GUID guid;
    memset(&guid, 0, sizeof(guid));
    (void) instance_id;
    return guid;
}
Uint16 SDL_GetGamepadVendorForID(SDL_JoystickID instance_id) { (void) instance_id; return 0; }
Uint16 SDL_GetGamepadProductForID(SDL_JoystickID instance_id) { (void) instance_id; return 0; }
Uint16 SDL_GetGamepadProductVersionForID(SDL_JoystickID instance_id) { (void) instance_id; return 0; }
SDL_GamepadType SDL_GetGamepadTypeForID(SDL_JoystickID instance_id) {
    (void) instance_id;
    return SDL_GAMEPAD_TYPE_UNKNOWN;
}
SDL_GamepadType SDL_GetRealGamepadTypeForID(SDL_JoystickID instance_id) {
    (void) instance_id;
    return SDL_GAMEPAD_TYPE_UNKNOWN;
}
char *SDL_GetGamepadMappingForID(SDL_JoystickID instance_id) { (void) instance_id; return NULL; }
char *SDL_GetGamepadMappingForGUID(SDL_GUID guid) { (void) guid; return NULL; }
char *SDL_GetGamepadMapping(SDL_Gamepad *gamepad) { (void) gamepad; return NULL; }
bool SDL_SetGamepadMapping(SDL_JoystickID instance_id, const char *mapping) {
    (void) instance_id; (void) mapping;
    return false;
}
char **SDL_GetGamepadMappings(int *count) {
    if (count) *count = 0;
    return NULL;
}
SDL_Gamepad *SDL_OpenGamepad(SDL_JoystickID instance_id) { (void) instance_id; return NULL; }
SDL_Gamepad *SDL_GetGamepadFromID(SDL_JoystickID instance_id) { (void) instance_id; return NULL; }
SDL_Gamepad *SDL_GetGamepadFromPlayerIndex(int player_index) { (void) player_index; return NULL; }
SDL_PropertiesID SDL_GetGamepadProperties(SDL_Gamepad *gamepad) { (void) gamepad; return 0; }
SDL_JoystickID SDL_GetGamepadID(SDL_Gamepad *gamepad) { (void) gamepad; return 0; }
const char *SDL_GetGamepadName(SDL_Gamepad *gamepad) { (void) gamepad; return NULL; }
const char *SDL_GetGamepadPath(SDL_Gamepad *gamepad) { (void) gamepad; return NULL; }
SDL_GamepadType SDL_GetGamepadType(SDL_Gamepad *gamepad) {
    (void) gamepad;
    return SDL_GAMEPAD_TYPE_UNKNOWN;
}
int SDL_GetGamepadPlayerIndex(SDL_Gamepad *gamepad) { (void) gamepad; return -1; }
bool SDL_SetGamepadPlayerIndex(SDL_Gamepad *gamepad, int player_index) {
    (void) gamepad; (void) player_index;
    return false;
}
Uint16 SDL_GetGamepadVendor(SDL_Gamepad *gamepad) { (void) gamepad; return 0; }
Uint16 SDL_GetGamepadProduct(SDL_Gamepad *gamepad) { (void) gamepad; return 0; }
Uint16 SDL_GetGamepadProductVersion(SDL_Gamepad *gamepad) { (void) gamepad; return 0; }
Uint16 SDL_GetGamepadFirmwareVersion(SDL_Gamepad *gamepad) { (void) gamepad; return 0; }
const char *SDL_GetGamepadSerial(SDL_Gamepad *gamepad) { (void) gamepad; return NULL; }
Uint64 SDL_GetGamepadSteamHandle(SDL_Gamepad *gamepad) { (void) gamepad; return 0; }
SDL_JoystickConnectionState SDL_GetGamepadConnectionState(SDL_Gamepad *gamepad) {
    (void) gamepad;
    return SDL_JOYSTICK_CONNECTION_INVALID;
}
SDL_PowerState SDL_GetGamepadPowerInfo(SDL_Gamepad *gamepad, int *percent) {
    (void) gamepad;
    if (percent) *percent = -1;
    return SDL_POWERSTATE_UNKNOWN;
}
SDL_Joystick *SDL_GetGamepadJoystick(SDL_Gamepad *gamepad) { (void) gamepad; return NULL; }
void SDL_SetGamepadEventsEnabled(bool enabled) { (void) enabled; }
bool SDL_GamepadEventsEnabled(void) { return false; }
SDL_GamepadBinding **SDL_GetGamepadBindings(SDL_Gamepad *gamepad, int *count) {
    (void) gamepad;
    if (count) *count = 0;
    return NULL;
}
SDL_GamepadType SDL_GetGamepadTypeFromString(const char *str) {
    (void) str;
    return SDL_GAMEPAD_TYPE_UNKNOWN;
}
const char *SDL_GetGamepadStringForType(SDL_GamepadType type) {
    (void) type;
    return "Unknown";
}
SDL_GamepadAxis SDL_GetGamepadAxisFromString(const char *str) {
    (void) str;
    return SDL_GAMEPAD_AXIS_INVALID;
}
const char *SDL_GetGamepadStringForAxis(SDL_GamepadAxis axis) {
    (void) axis;
    return NULL;
}
Sint16 SDL_GetGamepadAxis(SDL_Gamepad *gamepad, SDL_GamepadAxis axis) {
    (void) gamepad; (void) axis;
    return 0;
}
SDL_GamepadButton SDL_GetGamepadButtonFromString(const char *str) {
    (void) str;
    return SDL_GAMEPAD_BUTTON_INVALID;
}
const char *SDL_GetGamepadStringForButton(SDL_GamepadButton button) {
    (void) button;
    return NULL;
}
bool SDL_GetGamepadButton(SDL_Gamepad *gamepad, SDL_GamepadButton button) {
    (void) gamepad; (void) button;
    return false;
}
SDL_GamepadButtonLabel SDL_GetGamepadButtonLabelForType(SDL_GamepadType type, SDL_GamepadButton button) {
    (void) type; (void) button;
    return SDL_GAMEPAD_BUTTON_LABEL_UNKNOWN;
}
SDL_GamepadButtonLabel SDL_GetGamepadButtonLabel(SDL_Gamepad *gamepad, SDL_GamepadButton button) {
    (void) gamepad; (void) button;
    return SDL_GAMEPAD_BUTTON_LABEL_UNKNOWN;
}
bool SDL_GetGamepadTouchpadFinger(SDL_Gamepad *gamepad, int touchpad, int finger, bool *down, float *x, float *y, float *pressure) {
    (void) gamepad; (void) touchpad; (void) finger;
    if (down) *down = false;
    if (x) *x = 0.0f;
    if (y) *y = 0.0f;
    if (pressure) *pressure = 0.0f;
    return false;
}
bool SDL_SetGamepadSensorEnabled(SDL_Gamepad *gamepad, SDL_SensorType type, bool enabled) {
    (void) gamepad; (void) type; (void) enabled;
    return false;
}
float SDL_GetGamepadSensorDataRate(SDL_Gamepad *gamepad, SDL_SensorType type) {
    (void) gamepad; (void) type;
    return 0.0f;
}
bool SDL_GetGamepadSensorData(SDL_Gamepad *gamepad, SDL_SensorType type, float *data, int num_values) {
    (void) gamepad; (void) type; (void) data; (void) num_values;
    return false;
}
bool SDL_RumbleGamepad(SDL_Gamepad *gamepad, Uint16 low_frequency_rumble, Uint16 high_frequency_rumble, Uint32 duration_ms) {
    (void) gamepad; (void) low_frequency_rumble; (void) high_frequency_rumble; (void) duration_ms;
    return false;
}
bool SDL_RumbleGamepadTriggers(SDL_Gamepad *gamepad, Uint16 left_rumble, Uint16 right_rumble, Uint32 duration_ms) {
    (void) gamepad; (void) left_rumble; (void) right_rumble; (void) duration_ms;
    return false;
}
bool SDL_SetGamepadLED(SDL_Gamepad *gamepad, Uint8 red, Uint8 green, Uint8 blue) {
    (void) gamepad; (void) red; (void) green; (void) blue;
    return false;
}
void SDL_CloseGamepad(SDL_Gamepad *gamepad) { (void) gamepad; }
const char *SDL_GetGamepadAppleSFSymbolsNameForButton(SDL_Gamepad *gamepad, SDL_GamepadButton button) {
    (void) gamepad; (void) button;
    return NULL;
}
const char *SDL_GetGamepadAppleSFSymbolsNameForAxis(SDL_Gamepad *gamepad, SDL_GamepadAxis axis) {
    (void) gamepad; (void) axis;
    return NULL;
}

/* ------------------------------------------------------------------ */
/* Pixels                                                               */
/* ------------------------------------------------------------------ */

static const SDL_PixelFormatDetails xrgb8888Details = {
    SDL_PIXELFORMAT_XRGB8888, 32, 4, { 0, 0 },
    0x00FF0000, 0x0000FF00, 0x000000FF, 0x00000000,
    8, 8, 8, 8, 16, 8, 0, 0
};
static const SDL_PixelFormatDetails rgba8888Details = {
    SDL_PIXELFORMAT_RGBA8888, 32, 4, { 0, 0 },
    0xFF000000, 0x00FF0000, 0x0000FF00, 0x000000FF,
    8, 8, 8, 8, 24, 16, 8, 0
};

const char *SDL_GetPixelFormatName(SDL_PixelFormat format) {
    switch (format) {
        case SDL_PIXELFORMAT_XRGB8888: return "SDL_PIXELFORMAT_XRGB8888";
        case SDL_PIXELFORMAT_ARGB8888: return "SDL_PIXELFORMAT_ARGB8888";
        case SDL_PIXELFORMAT_RGBA8888: return "SDL_PIXELFORMAT_RGBA8888";
        case SDL_PIXELFORMAT_ABGR8888: return "SDL_PIXELFORMAT_ABGR8888";
        case SDL_PIXELFORMAT_RGBX8888: return "SDL_PIXELFORMAT_RGBX8888";
        case SDL_PIXELFORMAT_BGRX8888: return "SDL_PIXELFORMAT_BGRX8888";
        default: return "SDL_PIXELFORMAT_UNKNOWN";
    }
}

bool SDL_GetMasksForPixelFormat(SDL_PixelFormat format, int *bpp, Uint32 *Rmask, Uint32 *Gmask, Uint32 *Bmask, Uint32 *Amask) {
    const SDL_PixelFormatDetails *details = SDL_GetPixelFormatDetails(format);
    if (!details) return false;
    if (bpp) *bpp = details->bits_per_pixel;
    if (Rmask) *Rmask = details->Rmask;
    if (Gmask) *Gmask = details->Gmask;
    if (Bmask) *Bmask = details->Bmask;
    if (Amask) *Amask = details->Amask;
    return true;
}

SDL_PixelFormat SDL_GetPixelFormatForMasks(int bpp, Uint32 Rmask, Uint32 Gmask, Uint32 Bmask, Uint32 Amask) {
    (void) bpp; (void) Rmask; (void) Gmask; (void) Bmask; (void) Amask;
    return SDL_PIXELFORMAT_UNKNOWN;
}

const SDL_PixelFormatDetails *SDL_GetPixelFormatDetails(SDL_PixelFormat format) {
    switch (format) {
        case SDL_PIXELFORMAT_RGBA8888: return &rgba8888Details;
        case SDL_PIXELFORMAT_XRGB8888:
        case SDL_PIXELFORMAT_ARGB8888:
        default: return &xrgb8888Details;
    }
}

SDL_Palette *SDL_CreatePalette(int ncolors) {
    SDL_Palette *palette = (SDL_Palette *) SDL_malloc(sizeof(SDL_Palette));
    if (!palette) return NULL;
    memset(palette, 0, sizeof(*palette));
    if (ncolors > 0) {
        palette->colors = (SDL_Color *) SDL_calloc((size_t) ncolors, sizeof(SDL_Color));
        palette->ncolors = palette->colors ? ncolors : 0;
    }
    return palette;
}

bool SDL_SetPaletteColors(SDL_Palette *palette, const SDL_Color *colors, int firstcolor, int ncolors) {
    if (!palette || !colors || firstcolor < 0 || ncolors < 0 ||
        firstcolor + ncolors > palette->ncolors) return false;
    memcpy(palette->colors + firstcolor, colors, (size_t) ncolors * sizeof(SDL_Color));
    return true;
}

Uint32 SDL_MapRGB(const SDL_PixelFormatDetails *format, const SDL_Palette *palette, Uint8 r, Uint8 g, Uint8 b) {
    (void) palette;
    if (!format) return 0;
    return ((Uint32) (r >> (8 - format->Rbits)) << format->Rshift) |
           ((Uint32) (g >> (8 - format->Gbits)) << format->Gshift) |
           ((Uint32) (b >> (8 - format->Bbits)) << format->Bshift) |
           (format->Abits ? 0xFFu << format->Ashift : 0);
}

Uint32 SDL_MapRGBA(const SDL_PixelFormatDetails *format, const SDL_Palette *palette, Uint8 r, Uint8 g, Uint8 b, Uint8 a) {
    (void) palette;
    if (!format) return 0;
    return ((Uint32) (r >> (8 - format->Rbits)) << format->Rshift) |
           ((Uint32) (g >> (8 - format->Gbits)) << format->Gshift) |
           ((Uint32) (b >> (8 - format->Bbits)) << format->Bshift) |
           ((Uint32) (a >> (8 - format->Abits)) << format->Ashift);
}

void SDL_GetRGB(Uint32 pixelvalue, const SDL_PixelFormatDetails *format, const SDL_Palette *palette, Uint8 *r, Uint8 *g, Uint8 *b) {
    (void) palette;
    if (!format) return;
    if (r) *r = (Uint8) ((pixelvalue >> format->Rshift) & (format->Rmask >> format->Rshift));
    if (g) *g = (Uint8) ((pixelvalue >> format->Gshift) & (format->Gmask >> format->Gshift));
    if (b) *b = (Uint8) ((pixelvalue >> format->Bshift) & (format->Bmask >> format->Bshift));
}

void SDL_GetRGBA(Uint32 pixelvalue, const SDL_PixelFormatDetails *format, const SDL_Palette *palette, Uint8 *r, Uint8 *g, Uint8 *b, Uint8 *a) {
    SDL_GetRGB(pixelvalue, format, palette, r, g, b);
    if (a && format) *a = (Uint8) ((pixelvalue >> format->Ashift) & (format->Amask >> format->Ashift));
}

/* ------------------------------------------------------------------ */
/* Surfaces: allocation only, no blit pipeline                          */
/* ------------------------------------------------------------------ */

SDL_Surface *SDL_CreateSurface(int width, int height, SDL_PixelFormat format) {
    if (width <= 0 || height <= 0) return NULL;
    SDL_Surface *surface = (SDL_Surface *) SDL_malloc(sizeof(SDL_Surface));
    if (!surface) return NULL;
    memset(surface, 0, sizeof(*surface));
    surface->format = format;
    surface->w = width;
    surface->h = height;
    surface->pitch = width * 4;
    surface->pixels = SDL_calloc((size_t) surface->pitch, (size_t) height);
    if (!surface->pixels) {
        SDL_free(surface);
        return NULL;
    }
    return surface;
}

SDL_Surface *SDL_CreateSurfaceFrom(int width, int height, SDL_PixelFormat format, void *pixels, int pitch) {
    SDL_Surface *surface = SDL_CreateSurface(width, height, format);
    if (!surface) return NULL;
    if (pixels) {
        /* Caller-owned memory: never freed by SDL_DestroySurface */
        SDL_free(surface->pixels);
        surface->pixels = pixels;
        surface->pitch = pitch;
        surface->flags |= SDL_SURFACE_PREALLOCATED;
    }
    return surface;
}

void SDL_DestroySurface(SDL_Surface *surface) {
    if (!surface) return;
    if (!(surface->flags & SDL_SURFACE_PREALLOCATED)) SDL_free(surface->pixels);
    SDL_free(surface);
}

SDL_PropertiesID SDL_GetSurfaceProperties(SDL_Surface *surface) { (void) surface; return 0; }
bool SDL_SetSurfaceColorspace(SDL_Surface *surface, SDL_Colorspace colorspace) {
    (void) surface; (void) colorspace;
    return false;
}
SDL_Colorspace SDL_GetSurfaceColorspace(SDL_Surface *surface) {
    (void) surface;
    return SDL_COLORSPACE_SRGB;
}
SDL_Palette *SDL_CreateSurfacePalette(SDL_Surface *surface) {
    (void) surface;
    return NULL;
}
bool SDL_SetSurfacePalette(SDL_Surface *surface, SDL_Palette *palette) {
    (void) surface; (void) palette;
    return false;
}
SDL_Palette *SDL_GetSurfacePalette(SDL_Surface *surface) { (void) surface; return NULL; }
bool SDL_AddSurfaceAlternateImage(SDL_Surface *surface, SDL_Surface *image) {
    (void) surface; (void) image;
    return false;
}
bool SDL_SurfaceHasAlternateImages(SDL_Surface *surface) { (void) surface; return false; }
SDL_Surface **SDL_GetSurfaceImages(SDL_Surface *surface, int *count) {
    (void) surface;
    if (count) *count = 0;
    return NULL;
}
void SDL_RemoveSurfaceAlternateImages(SDL_Surface *surface) { (void) surface; }
bool SDL_LockSurface(SDL_Surface *surface) { (void) surface; return true; }
void SDL_UnlockSurface(SDL_Surface *surface) { (void) surface; }
SDL_Surface *SDL_LoadSurface_IO(SDL_IOStream *src, bool closeio) { (void) src; (void) closeio; return NULL; }
SDL_Surface *SDL_LoadSurface(const char *file) { (void) file; return NULL; }
SDL_Surface *SDL_LoadBMP_IO(SDL_IOStream *src, bool closeio) { (void) src; (void) closeio; return NULL; }
SDL_Surface *SDL_LoadBMP(const char *file) { (void) file; return NULL; }
bool SDL_SaveBMP_IO(SDL_Surface *surface, SDL_IOStream *dst, bool closeio) {
    (void) surface; (void) dst; (void) closeio;
    return false;
}
bool SDL_SaveBMP(SDL_Surface *surface, const char *file) { (void) surface; (void) file; return false; }
SDL_Surface *SDL_LoadPNG_IO(SDL_IOStream *src, bool closeio) { (void) src; (void) closeio; return NULL; }
SDL_Surface *SDL_LoadPNG(const char *file) { (void) file; return NULL; }
bool SDL_SavePNG_IO(SDL_Surface *surface, SDL_IOStream *dst, bool closeio) {
    (void) surface; (void) dst; (void) closeio;
    return false;
}
bool SDL_SavePNG(SDL_Surface *surface, const char *file) { (void) surface; (void) file; return false; }
bool SDL_SetSurfaceRLE(SDL_Surface *surface, bool enabled) { (void) surface; (void) enabled; return false; }
bool SDL_SurfaceHasRLE(SDL_Surface *surface) { (void) surface; return false; }
bool SDL_SetSurfaceColorKey(SDL_Surface *surface, bool enabled, Uint32 key) {
    (void) surface; (void) enabled; (void) key;
    return false;
}
bool SDL_SurfaceHasColorKey(SDL_Surface *surface) { (void) surface; return false; }
bool SDL_GetSurfaceColorKey(SDL_Surface *surface, Uint32 *key) {
    (void) surface;
    if (key) *key = 0;
    return false;
}
bool SDL_SetSurfaceColorMod(SDL_Surface *surface, Uint8 r, Uint8 g, Uint8 b) {
    (void) surface; (void) r; (void) g; (void) b;
    return false;
}
bool SDL_GetSurfaceColorMod(SDL_Surface *surface, Uint8 *r, Uint8 *g, Uint8 *b) {
    (void) surface;
    if (r) *r = 255;
    if (g) *g = 255;
    if (b) *b = 255;
    return false;
}
bool SDL_SetSurfaceAlphaMod(SDL_Surface *surface, Uint8 alpha) { (void) surface; (void) alpha; return false; }
bool SDL_GetSurfaceAlphaMod(SDL_Surface *surface, Uint8 *alpha) {
    (void) surface;
    if (alpha) *alpha = 255;
    return false;
}
bool SDL_SetSurfaceBlendMode(SDL_Surface *surface, SDL_BlendMode blendMode) {
    (void) surface; (void) blendMode;
    return false;
}
bool SDL_GetSurfaceBlendMode(SDL_Surface *surface, SDL_BlendMode *blendMode) {
    (void) surface;
    if (blendMode) *blendMode = SDL_BLENDMODE_NONE;
    return false;
}
bool SDL_SetSurfaceClipRect(SDL_Surface *surface, const SDL_Rect *rect) {
    (void) surface; (void) rect;
    return false;
}
bool SDL_GetSurfaceClipRect(SDL_Surface *surface, SDL_Rect *rect) {
    if (!surface || !rect) return false;
    rect->x = 0;
    rect->y = 0;
    rect->w = surface->w;
    rect->h = surface->h;
    return true;
}
bool SDL_FlipSurface(SDL_Surface *surface, SDL_FlipMode flip) { (void) surface; (void) flip; return false; }
SDL_Surface *SDL_RotateSurface(SDL_Surface *surface, float angle) { (void) surface; (void) angle; return NULL; }
SDL_Surface *SDL_DuplicateSurface(SDL_Surface *surface) {
    if (!surface) return NULL;
    SDL_Surface *out = SDL_CreateSurface(surface->w, surface->h, surface->format);
    if (out && surface->pixels)
        memcpy(out->pixels, surface->pixels, (size_t) surface->pitch * (size_t) surface->h);
    return out;
}
SDL_Surface *SDL_ScaleSurface(SDL_Surface *surface, int width, int height, SDL_ScaleMode scaleMode) {
    (void) surface; (void) width; (void) height; (void) scaleMode;
    return NULL;
}
SDL_Surface *SDL_ConvertSurface(SDL_Surface *surface, SDL_PixelFormat format) {
    (void) surface; (void) format;
    return NULL;
}
SDL_Surface *SDL_ConvertSurfaceAndColorspace(SDL_Surface *surface, SDL_PixelFormat format, SDL_Palette *palette, SDL_Colorspace colorspace, SDL_PropertiesID props) {
    (void) surface; (void) format; (void) palette; (void) colorspace; (void) props;
    return NULL;
}
bool SDL_ConvertPixels(int width, int height, SDL_PixelFormat src_format, const void *src, int src_pitch, SDL_PixelFormat dst_format, void *dst, int dst_pitch) {
    (void) width; (void) height; (void) src_format; (void) src; (void) src_pitch;
    (void) dst_format; (void) dst; (void) dst_pitch;
    return false;
}
bool SDL_ConvertPixelsAndColorspace(int width, int height, SDL_PixelFormat src_format, SDL_Colorspace src_colorspace, SDL_PropertiesID src_properties, const void *src, int src_pitch, SDL_PixelFormat dst_format, SDL_Colorspace dst_colorspace, SDL_PropertiesID dst_properties, void *dst, int dst_pitch) {
    (void) width; (void) height; (void) src_format; (void) src_colorspace; (void) src_properties;
    (void) src; (void) src_pitch; (void) dst_format; (void) dst_colorspace; (void) dst_properties;
    (void) dst; (void) dst_pitch;
    return false;
}
bool SDL_PremultiplyAlpha(int width, int height, SDL_PixelFormat src_format, const void *src, int src_pitch, SDL_PixelFormat dst_format, void *dst, int dst_pitch, bool linear) {
    (void) width; (void) height; (void) src_format; (void) src; (void) src_pitch;
    (void) dst_format; (void) dst; (void) dst_pitch; (void) linear;
    return false;
}
bool SDL_PremultiplySurfaceAlpha(SDL_Surface *surface, bool linear) {
    (void) surface; (void) linear;
    return false;
}
bool SDL_ClearSurface(SDL_Surface *surface, float r, float g, float b, float a) {
    (void) surface; (void) r; (void) g; (void) b; (void) a;
    return false;
}
bool SDL_FillSurfaceRect(SDL_Surface *dst, const SDL_Rect *rect, Uint32 color) {
    (void) dst; (void) rect; (void) color;
    return false;
}
bool SDL_FillSurfaceRects(SDL_Surface *dst, const SDL_Rect *rects, int count, Uint32 color) {
    (void) dst; (void) rects; (void) count; (void) color;
    return false;
}
bool SDL_BlitSurface(SDL_Surface *src, const SDL_Rect *srcrect, SDL_Surface *dst, const SDL_Rect *dstrect) {
    (void) src; (void) srcrect; (void) dst; (void) dstrect;
    return false;
}
bool SDL_BlitSurfaceUnchecked(SDL_Surface *src, const SDL_Rect *srcrect, SDL_Surface *dst, const SDL_Rect *dstrect) {
    (void) src; (void) srcrect; (void) dst; (void) dstrect;
    return false;
}
bool SDL_BlitSurfaceScaled(SDL_Surface *src, const SDL_Rect *srcrect, SDL_Surface *dst, const SDL_Rect *dstrect, SDL_ScaleMode scaleMode) {
    (void) src; (void) srcrect; (void) dst; (void) dstrect; (void) scaleMode;
    return false;
}
bool SDL_BlitSurfaceUncheckedScaled(SDL_Surface *src, const SDL_Rect *srcrect, SDL_Surface *dst, const SDL_Rect *dstrect, SDL_ScaleMode scaleMode) {
    (void) src; (void) srcrect; (void) dst; (void) dstrect; (void) scaleMode;
    return false;
}
bool SDL_StretchSurface(SDL_Surface *src, const SDL_Rect *srcrect, SDL_Surface *dst, const SDL_Rect *dstrect, SDL_ScaleMode scaleMode) {
    (void) src; (void) srcrect; (void) dst; (void) dstrect; (void) scaleMode;
    return false;
}
bool SDL_BlitSurfaceTiled(SDL_Surface *src, const SDL_Rect *srcrect, SDL_Surface *dst, const SDL_Rect *dstrect) {
    (void) src; (void) srcrect; (void) dst; (void) dstrect;
    return false;
}
bool SDL_BlitSurfaceTiledWithScale(SDL_Surface *src, const SDL_Rect *srcrect, float scale, SDL_ScaleMode scaleMode, SDL_Surface *dst, const SDL_Rect *dstrect) {
    (void) src; (void) srcrect; (void) scale; (void) scaleMode; (void) dst; (void) dstrect;
    return false;
}
bool SDL_BlitSurface9Grid(SDL_Surface *src, const SDL_Rect *srcrect, int left_width, int right_width, int top_height, int bottom_height, float scale, SDL_ScaleMode scaleMode, SDL_Surface *dst, const SDL_Rect *dstrect) {
    (void) src; (void) srcrect; (void) left_width; (void) right_width; (void) top_height;
    (void) bottom_height; (void) scale; (void) scaleMode; (void) dst; (void) dstrect;
    return false;
}
Uint32 SDL_MapSurfaceRGB(SDL_Surface *surface, Uint8 r, Uint8 g, Uint8 b) {
    return SDL_MapRGB(SDL_GetPixelFormatDetails(surface ? surface->format : SDL_PIXELFORMAT_XRGB8888), NULL, r, g, b);
}
Uint32 SDL_MapSurfaceRGBA(SDL_Surface *surface, Uint8 r, Uint8 g, Uint8 b, Uint8 a) {
    return SDL_MapRGBA(SDL_GetPixelFormatDetails(surface ? surface->format : SDL_PIXELFORMAT_XRGB8888), NULL, r, g, b, a);
}
bool SDL_ReadSurfacePixel(SDL_Surface *surface, int x, int y, Uint8 *r, Uint8 *g, Uint8 *b, Uint8 *a) {
    (void) surface; (void) x; (void) y;
    if (r) *r = 0;
    if (g) *g = 0;
    if (b) *b = 0;
    if (a) *a = 0;
    return false;
}
bool SDL_ReadSurfacePixelFloat(SDL_Surface *surface, int x, int y, float *r, float *g, float *b, float *a) {
    (void) surface; (void) x; (void) y;
    if (r) *r = 0.0f;
    if (g) *g = 0.0f;
    if (b) *b = 0.0f;
    if (a) *a = 0.0f;
    return false;
}
bool SDL_WriteSurfacePixel(SDL_Surface *surface, int x, int y, Uint8 r, Uint8 g, Uint8 b, Uint8 a) {
    (void) surface; (void) x; (void) y; (void) r; (void) g; (void) b; (void) a;
    return false;
}
bool SDL_WriteSurfacePixelFloat(SDL_Surface *surface, int x, int y, float r, float g, float b, float a) {
    (void) surface; (void) x; (void) y; (void) r; (void) g; (void) b; (void) a;
    return false;
}

/* ------------------------------------------------------------------ */
/* Message box / power / CPU / blend mode                               */
/* ------------------------------------------------------------------ */

bool SDL_ShowMessageBox(const SDL_MessageBoxData *messageboxdata, int *buttonid) {
    if (messageboxdata) {
        SDL3SHIM_LOGI("SDL message box [%s]: %s",
                      messageboxdata->title ? messageboxdata->title : "",
                      messageboxdata->message ? messageboxdata->message : "");
        if (buttonid) *buttonid = 0;
    }
    return false;
}

bool SDL_ShowSimpleMessageBox(SDL_MessageBoxFlags flags, const char *title, const char *message, SDL_Window *window) {
    (void) flags; (void) window;
    SDL3SHIM_LOGI("SDL message box [%s]: %s", title ? title : "", message ? message : "");
    return false;
}

SDL_PowerState SDL_GetPowerInfo(int *seconds, int *percent) {
    if (seconds) *seconds = -1;
    if (percent) *percent = -1;
    return SDL_POWERSTATE_NO_BATTERY;
}

int SDL_GetCPUCacheLineSize(void) { return 64; }
int SDL_GetSystemRAM(void) {
    long pages = sysconf(_SC_PHYS_PAGES);
    long pageSize = sysconf(_SC_PAGESIZE);
    return pages > 0 && pageSize > 0 ? (int) ((long long) pages * pageSize / (1024 * 1024)) : 0;
}
size_t SDL_GetSIMDAlignment(void) { return 16; }
int SDL_GetSystemPageSize(void) { return getpagesize(); }
bool SDL_HasAltiVec(void) { return false; }
bool SDL_HasMMX(void) { return false; }
bool SDL_HasSSE(void) { return false; }
bool SDL_HasSSE2(void) { return false; }
bool SDL_HasSSE3(void) { return false; }
bool SDL_HasSSE41(void) { return false; }
bool SDL_HasSSE42(void) { return false; }
bool SDL_HasAVX(void) { return false; }
bool SDL_HasAVX2(void) { return false; }
bool SDL_HasAVX512F(void) { return false; }
#if defined(__ARM_NEON) || defined(__ARM_NEON__)
bool SDL_HasARMSIMD(void) { return false; }
bool SDL_HasNEON(void) { return true; }
#else
bool SDL_HasARMSIMD(void) { return false; }
bool SDL_HasNEON(void) { return false; }
#endif
bool SDL_HasLSX(void) { return false; }
bool SDL_HasLASX(void) { return false; }

SDL_BlendMode SDL_ComposeCustomBlendMode(SDL_BlendFactor srcColorFactor,
                                         SDL_BlendFactor dstColorFactor,
                                         SDL_BlendOperation colorOperation,
                                         SDL_BlendFactor srcAlphaFactor,
                                         SDL_BlendFactor dstAlphaFactor,
                                         SDL_BlendOperation alphaOperation) {
    /* Not reachable from the game (no SDL_Renderer usage); report invalid */
    (void) srcColorFactor; (void) dstColorFactor; (void) colorOperation;
    (void) srcAlphaFactor; (void) dstAlphaFactor; (void) alphaOperation;
    return SDL_BLENDMODE_INVALID;
}

/* ------------------------------------------------------------------ */
/* Late additions: symbols referenced by the bindings' Functions tables */
/* ------------------------------------------------------------------ */

SDL_GamepadType SDL_GetRealGamepadType(SDL_Gamepad *gamepad) {
    (void) gamepad;
    return SDL_GAMEPAD_TYPE_UNKNOWN;
}
bool SDL_GamepadConnected(SDL_Gamepad *gamepad) { (void) gamepad; return false; }
bool SDL_GamepadHasAxis(SDL_Gamepad *gamepad, SDL_GamepadAxis axis) {
    (void) gamepad; (void) axis;
    return false;
}
bool SDL_GamepadHasButton(SDL_Gamepad *gamepad, SDL_GamepadButton button) {
    (void) gamepad; (void) button;
    return false;
}
bool SDL_GamepadHasSensor(SDL_Gamepad *gamepad, SDL_SensorType type) {
    (void) gamepad; (void) type;
    return false;
}
bool SDL_GamepadSensorEnabled(SDL_Gamepad *gamepad, SDL_SensorType type) {
    (void) gamepad; (void) type;
    return false;
}
int SDL_GetNumGamepadTouchpads(SDL_Gamepad *gamepad) { (void) gamepad; return 0; }
int SDL_GetNumGamepadTouchpadFingers(SDL_Gamepad *gamepad, int touchpad) {
    (void) gamepad; (void) touchpad;
    return 0;
}
bool SDL_SendGamepadEffect(SDL_Gamepad *gamepad, const void *data, int size) {
    (void) gamepad; (void) data; (void) size;
    return false;
}
int SDL_AddGamepadMapping(const char *mapping) { (void) mapping; return 0; }
int SDL_AddGamepadMappingsFromIO(SDL_IOStream *src, bool closeio) {
    (void) src; (void) closeio;
    return -1;
}
int SDL_AddGamepadMappingsFromFile(const char *file) { (void) file; return -1; }
bool SDL_ReloadGamepadMappings(void) { return false; }
void SDL_UpdateGamepads(void) {}

void SDL_CloseJoystick(SDL_Joystick *joystick) { (void) joystick; }
bool SDL_JoystickConnected(SDL_Joystick *joystick) { (void) joystick; return false; }
bool SDL_RumbleJoystick(SDL_Joystick *joystick, Uint16 low_frequency_rumble, Uint16 high_frequency_rumble, Uint32 duration_ms) {
    (void) joystick; (void) low_frequency_rumble; (void) high_frequency_rumble; (void) duration_ms;
    return false;
}
bool SDL_RumbleJoystickTriggers(SDL_Joystick *joystick, Uint16 left_rumble, Uint16 right_rumble, Uint32 duration_ms) {
    (void) joystick; (void) left_rumble; (void) right_rumble; (void) duration_ms;
    return false;
}
bool SDL_SendJoystickEffect(SDL_Joystick *joystick, const void *data, int size) {
    (void) joystick; (void) data; (void) size;
    return false;
}
int SDL_GetNumJoystickAxes(SDL_Joystick *joystick) { (void) joystick; return 0; }
int SDL_GetNumJoystickBalls(SDL_Joystick *joystick) { (void) joystick; return 0; }
int SDL_GetNumJoystickButtons(SDL_Joystick *joystick) { (void) joystick; return 0; }
int SDL_GetNumJoystickHats(SDL_Joystick *joystick) { (void) joystick; return 0; }
void SDL_UpdateSensors(void) {}

int SDL_GetNumLogicalCPUCores(void) {
    long count = sysconf(_SC_NPROCESSORS_ONLN);
    return count > 0 ? (int) count : 1;
}

SDL_Window *SDL_CreatePopupWindow(SDL_Window *parent, int offset_x, int offset_y, int w, int h, SDL_WindowFlags flags) {
    (void) parent; (void) offset_x; (void) offset_y;
    return SDL_CreateWindow(NULL, w, h, flags);
}
bool SDL_SetWindowFullscreenMode(SDL_Window *window, const SDL_DisplayMode *mode) {
    (void) window; (void) mode;
    return true;
}
const SDL_DisplayMode *SDL_GetWindowFullscreenMode(SDL_Window *window) {
    (void) window;
    return NULL;
}
bool SDL_GetWindowSafeArea(SDL_Window *window, SDL_Rect *rect) {
    if (!rect) return false;
    rect->x = 0;
    rect->y = 0;
    int w = 0, h = 0;
    SDL_GetWindowSize(window, &w, &h);
    rect->w = w;
    rect->h = h;
    return true;
}
bool SDL_UpdateWindowSurface(SDL_Window *window) { (void) window; return false; }
bool SDL_UpdateWindowSurfaceRects(SDL_Window *window, const SDL_Rect *rects, int numrects) {
    (void) window; (void) rects; (void) numrects;
    return false;
}
bool SDL_FlashWindow(SDL_Window *window, SDL_FlashOperation operation) {
    (void) window; (void) operation;
    return true;
}
bool SDL_ScreenKeyboardShown(SDL_Window *window) { (void) window; return false; }

/* The LWJGL SDL main bindings request these literal symbol names */
bool SDL_SDL_RegisterApp(const char *name, Uint32 style, void *hInst) {
    return SDL_RegisterApp(name, style, hInst);
}
void SDL_SDL_UnregisterApp(void) { SDL_UnregisterApp(); }

/* Desktop-only hooks: the bindings still dlsym them on every platform */
void SDL_SetWindowsMessageHook(void *callback, void *userdata) {
    (void) callback; (void) userdata;
}
void SDL_SetX11EventHook(SDL_X11EventHook callback, void *userdata) {
    (void) callback; (void) userdata;
}

int SDL_GetDirect3D9AdapterIndex(SDL_DisplayID displayID) {
    (void) displayID;
    return -1;
}

bool SDL_GetDXGIOutputInfo(SDL_DisplayID displayID, int *adapterIndex, int *outputIndex) {
    (void) displayID;
    if (adapterIndex) *adapterIndex = -1;
    if (outputIndex) *outputIndex = -1;
    return false;
}

/* Haptics: no force-feedback devices on the platform; empty enumeration */
SDL_HapticID *SDL_GetHaptics(int *count) {
    if (count) *count = 0;
    return NULL;
}
const char *SDL_GetHapticNameForID(SDL_HapticID instance_id) { (void) instance_id; return NULL; }
SDL_Haptic *SDL_OpenHaptic(SDL_HapticID instance_id) { (void) instance_id; return NULL; }
SDL_Haptic *SDL_GetHapticFromID(SDL_HapticID instance_id) { (void) instance_id; return NULL; }
SDL_HapticID SDL_GetHapticID(SDL_Haptic *haptic) { (void) haptic; return 0; }
const char *SDL_GetHapticName(SDL_Haptic *haptic) { (void) haptic; return NULL; }
bool SDL_IsMouseHaptic(void) { return false; }
SDL_Haptic *SDL_OpenHapticFromMouse(void) { return NULL; }
bool SDL_IsJoystickHaptic(SDL_Joystick *joystick) { (void) joystick; return false; }
SDL_Haptic *SDL_OpenHapticFromJoystick(SDL_Joystick *joystick) { (void) joystick; return NULL; }
void SDL_CloseHaptic(SDL_Haptic *haptic) { (void) haptic; }
int SDL_GetMaxHapticEffects(SDL_Haptic *haptic) { (void) haptic; return 0; }
int SDL_GetMaxHapticEffectsPlaying(SDL_Haptic *haptic) { (void) haptic; return 0; }
Uint32 SDL_GetHapticFeatures(SDL_Haptic *haptic) { (void) haptic; return 0; }
int SDL_GetNumHapticAxes(SDL_Haptic *haptic) { (void) haptic; return 0; }
bool SDL_HapticEffectSupported(SDL_Haptic *haptic, const SDL_HapticEffect *effect) {
    (void) haptic; (void) effect;
    return false;
}
SDL_HapticEffectID SDL_CreateHapticEffect(SDL_Haptic *haptic, const SDL_HapticEffect *effect) {
    (void) haptic; (void) effect;
    return 0;
}
bool SDL_UpdateHapticEffect(SDL_Haptic *haptic, SDL_HapticEffectID effect, const SDL_HapticEffect *data) {
    (void) haptic; (void) effect; (void) data;
    return false;
}
bool SDL_RunHapticEffect(SDL_Haptic *haptic, SDL_HapticEffectID effect, Uint32 iterations) {
    (void) haptic; (void) effect; (void) iterations;
    return false;
}
bool SDL_StopHapticEffect(SDL_Haptic *haptic, SDL_HapticEffectID effect) {
    (void) haptic; (void) effect;
    return false;
}
void SDL_DestroyHapticEffect(SDL_Haptic *haptic, SDL_HapticEffectID effect) {
    (void) haptic; (void) effect;
}
bool SDL_GetHapticEffectStatus(SDL_Haptic *haptic, SDL_HapticEffectID effect) {
    (void) haptic; (void) effect;
    return false;
}
bool SDL_SetHapticGain(SDL_Haptic *haptic, int gain) {
    (void) haptic; (void) gain;
    return false;
}
bool SDL_SetHapticAutocenter(SDL_Haptic *haptic, int autocenter) {
    (void) haptic; (void) autocenter;
    return false;
}
bool SDL_PauseHaptic(SDL_Haptic *haptic) { (void) haptic; return false; }
bool SDL_ResumeHaptic(SDL_Haptic *haptic) { (void) haptic; return false; }
bool SDL_StopHapticEffects(SDL_Haptic *haptic) { (void) haptic; return false; }
bool SDL_HapticRumbleSupported(SDL_Haptic *haptic) { (void) haptic; return false; }
bool SDL_InitHapticRumble(SDL_Haptic *haptic) { (void) haptic; return false; }
bool SDL_PlayHapticRumble(SDL_Haptic *haptic, float strength, Uint32 length) {
    (void) haptic; (void) strength; (void) length;
    return false;
}
bool SDL_StopHapticRumble(SDL_Haptic *haptic) { (void) haptic; return false; }

SDL_PenDeviceType SDL_GetPenDeviceType(SDL_PenID instance_id) {
    (void) instance_id;
    return SDL_PEN_DEVICE_TYPE_INVALID;
}
bool SDL_SendJoystickVirtualSensorData(SDL_Joystick *joystick, SDL_SensorType type, Uint64 sensor_timestamp, const float *data, int num_values) {
    (void) joystick; (void) type; (void) sensor_timestamp; (void) data; (void) num_values;
    return false;
}

void SDL_DestroyPalette(SDL_Palette *palette) {
    if (!palette) return;
    SDL_free(palette->colors);
    SDL_free(palette);
}
