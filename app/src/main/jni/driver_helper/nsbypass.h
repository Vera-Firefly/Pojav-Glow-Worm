#ifndef LINKER_NSBYPASS_H
#define LINKER_NSBYPASS_H

#include <stdbool.h>

bool linker_ns_load(const char* lib_search_path);
void* linker_ns_dlopen(const char* name, int flag);
void* linker_ns_dlopen_unique(const char* tmpdir, const char* name, int flag);
void* loadTurnipVulkanForPath(const char* native_dir, const char* cache_dir);

#endif //LINKER_NSBYPASS_H
