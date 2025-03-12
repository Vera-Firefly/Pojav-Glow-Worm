//
// Created by Vera-Firefly on 28.01.2025.
//
#ifdef BR_LOADER

void* load_symbol(void* handle, const char* symbol_name);
void* OSMGetProcAddress(void* handle, const char* symbol_name);
void* GLGetProcAddress(void* handle, const char* symbol_name);

#endif //BR_LOADER