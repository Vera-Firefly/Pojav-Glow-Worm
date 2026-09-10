/*
 * GLFW <-> SDL3 keycode translation tables.
 *
 * The launcher input pipeline (CallbackBridge -> libpojavexec event ring) is
 * built around GLFW keycodes; the SDL3 compatibility layer converts them to
 * SDL scancodes and SDL keycodes when filling SDL_Event memory.
 */
#include <string.h>
#include "sdl3_keymap.h"

typedef struct {
    unsigned short glfwKey;
    unsigned short scancode;
    unsigned int keycode;
} sdl3_key_entry;

#define E(glfw, scan, key) { glfw, scan, key }

/* GLFW keycode value constants (kept numeric so the table has no GLFW dependency) */
#define G_SPACE         32
#define G_APOSTROPHE    39
#define G_COMMA         44
#define G_MINUS         45
#define G_PERIOD        46
#define G_SLASH         47
#define G_0             48
#define G_9             57
#define G_SEMICOLON     59
#define G_EQUAL         61
#define G_A             65
#define G_Z             90
#define G_LEFT_BRACKET  91
#define G_BACKSLASH     92
#define G_RIGHT_BRACKET 93
#define G_GRAVE         96
#define G_WORLD_1       161
#define G_WORLD_2       162
#define G_ESCAPE        256
#define G_ENTER         257
#define G_TAB           258
#define G_BACKSPACE     259
#define G_INSERT        260
#define G_DELETE        261
#define G_RIGHT         262
#define G_LEFT          263
#define G_DOWN          264
#define G_UP            265
#define G_PAGE_UP       266
#define G_PAGE_DOWN     267
#define G_HOME          268
#define G_END           269
#define G_CAPS_LOCK     280
#define G_SCROLL_LOCK   281
#define G_NUM_LOCK      282
#define G_PRINT_SCREEN  283
#define G_PAUSE         284
#define G_F1            290
#define G_F12           301
#define G_F13           302
#define G_F24           313
#define G_KP_0          320
#define G_KP_9          329
#define G_KP_DECIMAL    330
#define G_KP_DIVIDE     331
#define G_KP_MULTIPLY   332
#define G_KP_SUBTRACT   333
#define G_KP_ADD        334
#define G_KP_ENTER      335
#define G_KP_EQUAL      336
#define G_LEFT_SHIFT    340
#define G_LEFT_CONTROL  341
#define G_LEFT_ALT      342
#define G_LEFT_SUPER    343
#define G_RIGHT_SHIFT   344
#define G_RIGHT_CONTROL 345
#define G_RIGHT_ALT     346
#define G_RIGHT_SUPER   347
#define G_MENU          348

static const sdl3_key_entry keyTable[] = {
    E(G_SPACE,        SDL_SCANCODE_SPACE,         SDLK_SPACE),
    E(G_APOSTROPHE,   SDL_SCANCODE_APOSTROPHE,    SDLK_APOSTROPHE),
    E(G_COMMA,        SDL_SCANCODE_COMMA,         SDLK_COMMA),
    E(G_MINUS,        SDL_SCANCODE_MINUS,         SDLK_MINUS),
    E(G_PERIOD,       SDL_SCANCODE_PERIOD,        SDLK_PERIOD),
    E(G_SLASH,        SDL_SCANCODE_SLASH,         SDLK_SLASH),
    E(G_0 + 0,        SDL_SCANCODE_0,             SDLK_0),
    E(G_0 + 1,        SDL_SCANCODE_1,             SDLK_1),
    E(G_0 + 2,        SDL_SCANCODE_2,             SDLK_2),
    E(G_0 + 3,        SDL_SCANCODE_3,             SDLK_3),
    E(G_0 + 4,        SDL_SCANCODE_4,             SDLK_4),
    E(G_0 + 5,        SDL_SCANCODE_5,             SDLK_5),
    E(G_0 + 6,        SDL_SCANCODE_6,             SDLK_6),
    E(G_0 + 7,        SDL_SCANCODE_7,             SDLK_7),
    E(G_0 + 8,        SDL_SCANCODE_8,             SDLK_8),
    E(G_0 + 9,        SDL_SCANCODE_9,             SDLK_9),
    E(G_SEMICOLON,    SDL_SCANCODE_SEMICOLON,     SDLK_SEMICOLON),
    E(G_EQUAL,        SDL_SCANCODE_EQUALS,        SDLK_EQUALS),
    E(G_A + 0,        SDL_SCANCODE_A,             SDLK_A),
    E(G_A + 1,        SDL_SCANCODE_B,             SDLK_B),
    E(G_A + 2,        SDL_SCANCODE_C,             SDLK_C),
    E(G_A + 3,        SDL_SCANCODE_D,             SDLK_D),
    E(G_A + 4,        SDL_SCANCODE_E,             SDLK_E),
    E(G_A + 5,        SDL_SCANCODE_F,             SDLK_F),
    E(G_A + 6,        SDL_SCANCODE_G,             SDLK_G),
    E(G_A + 7,        SDL_SCANCODE_H,             SDLK_H),
    E(G_A + 8,        SDL_SCANCODE_I,             SDLK_I),
    E(G_A + 9,        SDL_SCANCODE_J,             SDLK_J),
    E(G_A + 10,       SDL_SCANCODE_K,             SDLK_K),
    E(G_A + 11,       SDL_SCANCODE_L,             SDLK_L),
    E(G_A + 12,       SDL_SCANCODE_M,             SDLK_M),
    E(G_A + 13,       SDL_SCANCODE_N,             SDLK_N),
    E(G_A + 14,       SDL_SCANCODE_O,             SDLK_O),
    E(G_A + 15,       SDL_SCANCODE_P,             SDLK_P),
    E(G_A + 16,       SDL_SCANCODE_Q,             SDLK_Q),
    E(G_A + 17,       SDL_SCANCODE_R,             SDLK_R),
    E(G_A + 18,       SDL_SCANCODE_S,             SDLK_S),
    E(G_A + 19,       SDL_SCANCODE_T,             SDLK_T),
    E(G_A + 20,       SDL_SCANCODE_U,             SDLK_U),
    E(G_A + 21,       SDL_SCANCODE_V,             SDLK_V),
    E(G_A + 22,       SDL_SCANCODE_W,             SDLK_W),
    E(G_A + 23,       SDL_SCANCODE_X,             SDLK_X),
    E(G_A + 24,       SDL_SCANCODE_Y,             SDLK_Y),
    E(G_A + 25,       SDL_SCANCODE_Z,             SDLK_Z),
    E(G_LEFT_BRACKET, SDL_SCANCODE_LEFTBRACKET,   SDLK_LEFTBRACKET),
    E(G_BACKSLASH,    SDL_SCANCODE_BACKSLASH,     SDLK_BACKSLASH),
    E(G_RIGHT_BRACKET,SDL_SCANCODE_RIGHTBRACKET,  SDLK_RIGHTBRACKET),
    E(G_GRAVE,        SDL_SCANCODE_GRAVE,         SDLK_GRAVE),
    E(G_WORLD_1,      SDL_SCANCODE_NONUSBACKSLASH, SDL_SCANCODE_TO_KEYCODE(SDL_SCANCODE_NONUSBACKSLASH)),
    E(G_WORLD_2,      SDL_SCANCODE_NONUSHASH,     SDL_SCANCODE_TO_KEYCODE(SDL_SCANCODE_NONUSHASH)),
    E(G_ESCAPE,       SDL_SCANCODE_ESCAPE,        SDLK_ESCAPE),
    E(G_ENTER,        SDL_SCANCODE_RETURN,        SDLK_RETURN),
    E(G_TAB,          SDL_SCANCODE_TAB,           SDLK_TAB),
    E(G_BACKSPACE,    SDL_SCANCODE_BACKSPACE,     SDLK_BACKSPACE),
    E(G_INSERT,       SDL_SCANCODE_INSERT,        SDLK_INSERT),
    E(G_DELETE,       SDL_SCANCODE_DELETE,        SDLK_DELETE),
    E(G_RIGHT,        SDL_SCANCODE_RIGHT,         SDLK_RIGHT),
    E(G_LEFT,         SDL_SCANCODE_LEFT,          SDLK_LEFT),
    E(G_DOWN,         SDL_SCANCODE_DOWN,          SDLK_DOWN),
    E(G_UP,           SDL_SCANCODE_UP,            SDLK_UP),
    E(G_PAGE_UP,      SDL_SCANCODE_PAGEUP,        SDLK_PAGEUP),
    E(G_PAGE_DOWN,    SDL_SCANCODE_PAGEDOWN,      SDLK_PAGEDOWN),
    E(G_HOME,         SDL_SCANCODE_HOME,          SDLK_HOME),
    E(G_END,          SDL_SCANCODE_END,           SDLK_END),
    E(G_CAPS_LOCK,    SDL_SCANCODE_CAPSLOCK,      SDLK_CAPSLOCK),
    E(G_SCROLL_LOCK,  SDL_SCANCODE_SCROLLLOCK,    SDLK_SCROLLLOCK),
    E(G_NUM_LOCK,     SDL_SCANCODE_NUMLOCKCLEAR,  SDLK_NUMLOCKCLEAR),
    E(G_PRINT_SCREEN, SDL_SCANCODE_PRINTSCREEN,   SDLK_PRINTSCREEN),
    E(G_PAUSE,        SDL_SCANCODE_PAUSE,         SDLK_PAUSE),
    E(G_F1 + 0,       SDL_SCANCODE_F1,            SDLK_F1),
    E(G_F1 + 1,       SDL_SCANCODE_F2,            SDLK_F2),
    E(G_F1 + 2,       SDL_SCANCODE_F3,            SDLK_F3),
    E(G_F1 + 3,       SDL_SCANCODE_F4,            SDLK_F4),
    E(G_F1 + 4,       SDL_SCANCODE_F5,            SDLK_F5),
    E(G_F1 + 5,       SDL_SCANCODE_F6,            SDLK_F6),
    E(G_F1 + 6,       SDL_SCANCODE_F7,            SDLK_F7),
    E(G_F1 + 7,       SDL_SCANCODE_F8,            SDLK_F8),
    E(G_F1 + 8,       SDL_SCANCODE_F9,            SDLK_F9),
    E(G_F1 + 9,       SDL_SCANCODE_F10,           SDLK_F10),
    E(G_F1 + 10,      SDL_SCANCODE_F11,           SDLK_F11),
    E(G_F1 + 11,      SDL_SCANCODE_F12,           SDLK_F12),
    E(G_F13 + 0,      SDL_SCANCODE_F13,           SDLK_F13),
    E(G_F13 + 1,      SDL_SCANCODE_F14,           SDLK_F14),
    E(G_F13 + 2,      SDL_SCANCODE_F15,           SDLK_F15),
    E(G_F13 + 3,      SDL_SCANCODE_F16,           SDLK_F16),
    E(G_F13 + 4,      SDL_SCANCODE_F17,           SDLK_F17),
    E(G_F13 + 5,      SDL_SCANCODE_F18,           SDLK_F18),
    E(G_F13 + 6,      SDL_SCANCODE_F19,           SDLK_F19),
    E(G_F13 + 7,      SDL_SCANCODE_F20,           SDLK_F20),
    E(G_F13 + 8,      SDL_SCANCODE_F21,           SDLK_F21),
    E(G_F13 + 9,      SDL_SCANCODE_F22,           SDLK_F22),
    E(G_F13 + 10,     SDL_SCANCODE_F23,           SDLK_F23),
    E(G_F13 + 11,     SDL_SCANCODE_F24,           SDLK_F24),
    E(G_KP_0 + 0,     SDL_SCANCODE_KP_0,          SDLK_KP_0),
    E(G_KP_0 + 1,     SDL_SCANCODE_KP_1,          SDLK_KP_1),
    E(G_KP_0 + 2,     SDL_SCANCODE_KP_2,          SDLK_KP_2),
    E(G_KP_0 + 3,     SDL_SCANCODE_KP_3,          SDLK_KP_3),
    E(G_KP_0 + 4,     SDL_SCANCODE_KP_4,          SDLK_KP_4),
    E(G_KP_0 + 5,     SDL_SCANCODE_KP_5,          SDLK_KP_5),
    E(G_KP_0 + 6,     SDL_SCANCODE_KP_6,          SDLK_KP_6),
    E(G_KP_0 + 7,     SDL_SCANCODE_KP_7,          SDLK_KP_7),
    E(G_KP_0 + 8,     SDL_SCANCODE_KP_8,          SDLK_KP_8),
    E(G_KP_0 + 9,     SDL_SCANCODE_KP_9,          SDLK_KP_9),
    E(G_KP_DECIMAL,   SDL_SCANCODE_KP_PERIOD,     SDLK_KP_PERIOD),
    E(G_KP_DIVIDE,    SDL_SCANCODE_KP_DIVIDE,     SDLK_KP_DIVIDE),
    E(G_KP_MULTIPLY,  SDL_SCANCODE_KP_MULTIPLY,   SDLK_KP_MULTIPLY),
    E(G_KP_SUBTRACT,  SDL_SCANCODE_KP_MINUS,      SDLK_KP_MINUS),
    E(G_KP_ADD,       SDL_SCANCODE_KP_PLUS,       SDLK_KP_PLUS),
    E(G_KP_ENTER,     SDL_SCANCODE_KP_ENTER,      SDLK_KP_ENTER),
    E(G_KP_EQUAL,     SDL_SCANCODE_KP_EQUALS,     SDLK_KP_EQUALS),
    E(G_LEFT_SHIFT,   SDL_SCANCODE_LSHIFT,        SDLK_LSHIFT),
    E(G_LEFT_CONTROL, SDL_SCANCODE_LCTRL,         SDLK_LCTRL),
    E(G_LEFT_ALT,     SDL_SCANCODE_LALT,          SDLK_LALT),
    E(G_LEFT_SUPER,   SDL_SCANCODE_LGUI,          SDLK_LGUI),
    E(G_RIGHT_SHIFT,  SDL_SCANCODE_RSHIFT,        SDLK_RSHIFT),
    E(G_RIGHT_CONTROL,SDL_SCANCODE_RCTRL,         SDLK_RCTRL),
    E(G_RIGHT_ALT,    SDL_SCANCODE_RALT,          SDLK_RALT),
    E(G_RIGHT_SUPER,  SDL_SCANCODE_RGUI,          SDLK_RGUI),
    E(G_MENU,         SDL_SCANCODE_MENU,          SDLK_MENU),
};
#define KEY_TABLE_SIZE (sizeof(keyTable) / sizeof(keyTable[0]))

static const sdl3_key_entry *find_by_glfw(int glfwKey) {
    unsigned int key = (unsigned int) glfwKey;
    for (size_t i = 0; i < KEY_TABLE_SIZE; i++) {
        if (keyTable[i].glfwKey == key) return &keyTable[i];
    }
    return NULL;
}

int sdl3_glfw_to_scancode(int glfwKey) {
    const sdl3_key_entry *e = find_by_glfw(glfwKey);
    return e ? (int) e->scancode : 0;
}

int sdl3_glfw_to_keycode(int glfwKey) {
    const sdl3_key_entry *e = find_by_glfw(glfwKey);
    return e ? (int) e->keycode : 0;
}

int sdl3_scancode_to_keycode(int scancode) {
    for (size_t i = 0; i < KEY_TABLE_SIZE; i++) {
        if (keyTable[i].scancode == scancode) return (int) keyTable[i].keycode;
    }
    return scancode > 0 ? SDL_SCANCODE_TO_KEYCODE(scancode) : 0;
}

int sdl3_keycode_to_scancode(int keycode) {
    if (keycode > 0 && (keycode & SDLK_SCANCODE_MASK)) {
        return keycode & ~SDLK_SCANCODE_MASK;
    }
    for (size_t i = 0; i < KEY_TABLE_SIZE; i++) {
        if ((int) keyTable[i].keycode == keycode) return (int) keyTable[i].scancode;
    }
    return 0;
}

typedef struct {
    int scancode;
    const char *name;
} sdl3_key_name;

static const sdl3_key_name nameTable[] = {
    { SDL_SCANCODE_SPACE,            "Space" },
    { SDL_SCANCODE_APOSTROPHE,       "'" },
    { SDL_SCANCODE_COMMA,            "," },
    { SDL_SCANCODE_MINUS,            "-" },
    { SDL_SCANCODE_PERIOD,           "." },
    { SDL_SCANCODE_SLASH,            "/" },
    { SDL_SCANCODE_0,                "0" },
    { SDL_SCANCODE_1,                "1" },
    { SDL_SCANCODE_2,                "2" },
    { SDL_SCANCODE_3,                "3" },
    { SDL_SCANCODE_4,                "4" },
    { SDL_SCANCODE_5,                "5" },
    { SDL_SCANCODE_6,                "6" },
    { SDL_SCANCODE_7,                "7" },
    { SDL_SCANCODE_8,                "8" },
    { SDL_SCANCODE_9,                "9" },
    { SDL_SCANCODE_SEMICOLON,        ";" },
    { SDL_SCANCODE_EQUALS,           "=" },
    { SDL_SCANCODE_A,                "A" },
    { SDL_SCANCODE_B,                "B" },
    { SDL_SCANCODE_C,                "C" },
    { SDL_SCANCODE_D,                "D" },
    { SDL_SCANCODE_E,                "E" },
    { SDL_SCANCODE_F,                "F" },
    { SDL_SCANCODE_G,                "G" },
    { SDL_SCANCODE_H,                "H" },
    { SDL_SCANCODE_I,                "I" },
    { SDL_SCANCODE_J,                "J" },
    { SDL_SCANCODE_K,                "K" },
    { SDL_SCANCODE_L,                "L" },
    { SDL_SCANCODE_M,                "M" },
    { SDL_SCANCODE_N,                "N" },
    { SDL_SCANCODE_O,                "O" },
    { SDL_SCANCODE_P,                "P" },
    { SDL_SCANCODE_Q,                "Q" },
    { SDL_SCANCODE_R,                "R" },
    { SDL_SCANCODE_S,                "S" },
    { SDL_SCANCODE_T,                "T" },
    { SDL_SCANCODE_U,                "U" },
    { SDL_SCANCODE_V,                "V" },
    { SDL_SCANCODE_W,                "W" },
    { SDL_SCANCODE_X,                "X" },
    { SDL_SCANCODE_Y,                "Y" },
    { SDL_SCANCODE_Z,                "Z" },
    { SDL_SCANCODE_LEFTBRACKET,      "[" },
    { SDL_SCANCODE_BACKSLASH,        "\\" },
    { SDL_SCANCODE_RIGHTBRACKET,     "]" },
    { SDL_SCANCODE_GRAVE,            "`" },
    { SDL_SCANCODE_ESCAPE,           "Escape" },
    { SDL_SCANCODE_RETURN,           "Enter" },
    { SDL_SCANCODE_TAB,              "Tab" },
    { SDL_SCANCODE_BACKSPACE,        "Backspace" },
    { SDL_SCANCODE_INSERT,           "Insert" },
    { SDL_SCANCODE_DELETE,           "Delete" },
    { SDL_SCANCODE_RIGHT,            "Right" },
    { SDL_SCANCODE_LEFT,             "Left" },
    { SDL_SCANCODE_DOWN,             "Down" },
    { SDL_SCANCODE_UP,               "Up" },
    { SDL_SCANCODE_PAGEUP,           "Page Up" },
    { SDL_SCANCODE_PAGEDOWN,         "Page Down" },
    { SDL_SCANCODE_HOME,             "Home" },
    { SDL_SCANCODE_END,              "End" },
    { SDL_SCANCODE_CAPSLOCK,         "Caps Lock" },
    { SDL_SCANCODE_SCROLLLOCK,       "Scroll Lock" },
    { SDL_SCANCODE_NUMLOCKCLEAR,     "Num Lock" },
    { SDL_SCANCODE_PRINTSCREEN,      "Print Screen" },
    { SDL_SCANCODE_PAUSE,            "Pause" },
    { SDL_SCANCODE_F1,               "F1" },
    { SDL_SCANCODE_F2,               "F2" },
    { SDL_SCANCODE_F3,               "F3" },
    { SDL_SCANCODE_F4,               "F4" },
    { SDL_SCANCODE_F5,               "F5" },
    { SDL_SCANCODE_F6,               "F6" },
    { SDL_SCANCODE_F7,               "F7" },
    { SDL_SCANCODE_F8,               "F8" },
    { SDL_SCANCODE_F9,               "F9" },
    { SDL_SCANCODE_F10,              "F10" },
    { SDL_SCANCODE_F11,              "F11" },
    { SDL_SCANCODE_F12,              "F12" },
    { SDL_SCANCODE_F13,              "F13" },
    { SDL_SCANCODE_F14,              "F14" },
    { SDL_SCANCODE_F15,              "F15" },
    { SDL_SCANCODE_F16,              "F16" },
    { SDL_SCANCODE_F17,              "F17" },
    { SDL_SCANCODE_F18,              "F18" },
    { SDL_SCANCODE_F19,              "F19" },
    { SDL_SCANCODE_F20,              "F20" },
    { SDL_SCANCODE_F21,              "F21" },
    { SDL_SCANCODE_F22,              "F22" },
    { SDL_SCANCODE_F23,              "F23" },
    { SDL_SCANCODE_F24,              "F24" },
    { SDL_SCANCODE_KP_0,             "Keypad 0" },
    { SDL_SCANCODE_KP_1,             "Keypad 1" },
    { SDL_SCANCODE_KP_2,             "Keypad 2" },
    { SDL_SCANCODE_KP_3,             "Keypad 3" },
    { SDL_SCANCODE_KP_4,             "Keypad 4" },
    { SDL_SCANCODE_KP_5,             "Keypad 5" },
    { SDL_SCANCODE_KP_6,             "Keypad 6" },
    { SDL_SCANCODE_KP_7,             "Keypad 7" },
    { SDL_SCANCODE_KP_8,             "Keypad 8" },
    { SDL_SCANCODE_KP_9,             "Keypad 9" },
    { SDL_SCANCODE_KP_PERIOD,        "Keypad ." },
    { SDL_SCANCODE_KP_DIVIDE,        "Keypad /" },
    { SDL_SCANCODE_KP_MULTIPLY,      "Keypad *" },
    { SDL_SCANCODE_KP_MINUS,         "Keypad -" },
    { SDL_SCANCODE_KP_PLUS,          "Keypad +" },
    { SDL_SCANCODE_KP_ENTER,         "Keypad Enter" },
    { SDL_SCANCODE_KP_EQUALS,        "Keypad =" },
    { SDL_SCANCODE_LSHIFT,           "Left Shift" },
    { SDL_SCANCODE_LCTRL,            "Left Ctrl" },
    { SDL_SCANCODE_LALT,             "Left Alt" },
    { SDL_SCANCODE_LGUI,             "Left Meta" },
    { SDL_SCANCODE_RSHIFT,           "Right Shift" },
    { SDL_SCANCODE_RCTRL,            "Right Ctrl" },
    { SDL_SCANCODE_RALT,             "Right Alt" },
    { SDL_SCANCODE_RGUI,             "Right Meta" },
    { SDL_SCANCODE_MENU,             "Menu" },
};
#define NAME_TABLE_SIZE (sizeof(nameTable) / sizeof(nameTable[0]))

const char *sdl3_scancode_name(int scancode) {
    for (size_t i = 0; i < NAME_TABLE_SIZE; i++) {
        if (nameTable[i].scancode == scancode) return nameTable[i].name;
    }
    return "";
}

const char *sdl3_keycode_name(int keycode) {
    if (keycode > 0 && !(keycode & SDLK_SCANCODE_MASK)) {
        /* Printable keycodes use the lowercase unicode letter; show upper case */
        static char single[2];
        single[0] = (char) keycode;
        single[1] = '\0';
        return single;
    }
    return sdl3_scancode_name(sdl3_keycode_to_scancode(keycode));
}

Uint16 sdl3_glfwmods_to_sdl(int glfwMods) {
    Uint16 mods = 0;
    if (glfwMods & 0x1) mods |= SDL_KMOD_LSHIFT;   /* GLFW_MOD_SHIFT */
    if (glfwMods & 0x2) mods |= SDL_KMOD_LCTRL;    /* GLFW_MOD_CONTROL */
    if (glfwMods & 0x4) mods |= SDL_KMOD_LALT;     /* GLFW_MOD_ALT */
    if (glfwMods & 0x8) mods |= SDL_KMOD_LGUI;     /* GLFW_MOD_SUPER */
    if (glfwMods & 0x10) mods |= SDL_KMOD_CAPS;    /* GLFW_MOD_CAPS_LOCK */
    if (glfwMods & 0x20) mods |= SDL_KMOD_NUM;     /* GLFW_MOD_NUM_LOCK */
    return mods;
}
