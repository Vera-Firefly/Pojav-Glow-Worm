/*
 * Keycode mapping tables between the launcher's GLFW-semantic key events and
 * the SDL3 key model consumed by Minecraft 26.3+.
 */
#pragma once

#include <SDL3/SDL_keycode.h>

#ifdef __cplusplus
extern "C" {
#endif

/* GLFW keycode -> SDL_Scancode, 0 when unmapped */
int sdl3_glfw_to_scancode(int glfwKey);

/* GLFW keycode -> SDL keycode, 0 when unmapped */
int sdl3_glfw_to_keycode(int glfwKey);

/* SDL keycode -> SDL_Scancode (best effort reverse lookup) */
int sdl3_keycode_to_scancode(int keycode);

/* SDL_Scancode -> SDL keycode (SDL_SCANCODE_TO_KEYCODE fallback) */
int sdl3_scancode_to_keycode(int scancode);

/* Human readable names for the controls menu */
const char *sdl3_scancode_name(int scancode);
const char *sdl3_keycode_name(int keycode);

/* GLFW_MOD_* bitmask -> SDL_KMOD_* bitmask */
Uint16 sdl3_glfwmods_to_sdl(int glfwMods);

#ifdef __cplusplus
}
#endif
