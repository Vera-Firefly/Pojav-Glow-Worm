//
// Created by Vera-Firefly on 29/06/2025
//

#include "mesa_info.h"
#include <fstream>
#include <vector>
#include <regex>
#include <cstring>
#include <dlfcn.h>
#include <unistd.h>
#include <fcntl.h>
#include <link.h>
#include <sys/mman.h>

#define LOG_TAG "MesaInfo"
#ifdef LOG_INFO
    #define LOGI(...) printf("[INFO] [%s] ", LOG_TAG); printf(__VA_ARGS__); printf("\n")
    #define LOGE(...) printf("[ERROR] [%s] ", LOG_TAG); printf(__VA_ARGS__); printf("\n")
    #define LOGW(...) printf("[WARN] [%s] ", LOG_TAG); printf(__VA_ARGS__); printf("\n")
#else
    #include <android/log.h>
    #define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
    #define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
    #define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#endif

#if defined(__ANDROID__)
#ifndef RTLD_DI_LINKMAP
#define RTLD_DI_LINKMAP 2
#endif

static int
android_dlinfo(void* handle, int request, void* p)
{
    if (request != RTLD_DI_LINKMAP)
    {
        LOGE("Unsupported dlinfo request: %d", request);
        return -1;
    }

    Dl_info info;
    if (dladdr(handle, &info) == 0)
    {
        LOGE("dladdr failed for handle %p", handle);
        return -1;
    }

    static struct link_map map;
    map.l_name = const_cast<char*>(info.dli_fname);
    map.l_addr = reinterpret_cast<ElfW(Addr)>(info.dli_fbase);

    *reinterpret_cast<struct link_map**>(p) = &map;
    return 0;
}

#define dlinfo android_dlinfo
#endif

static std::string
find_library_path(void* handle)
{
    Dl_info info;
    if (dladdr(handle, &info) == 0)
    {
        LOGE("dladdr failed");
        return "";
    }

    FILE* maps = fopen("/proc/self/maps", "r");
    if (!maps)
    {
        LOGE("Cannot open /proc/self/maps");
        return "";
    }

    char line[512];
    std::string result;
    while (fgets(line, sizeof(line), maps))
    {
        if (strstr(line, info.dli_fname) && strstr(line, "r--"))
        {
            const char* path = strchr(line, '/');
            if (path)
            {
                result = path;
                result.erase(result.find_last_not_of("\n\r") + 1);
                break;
            }
        }
    }
    fclose(maps);
    return result;
}

static const char*
memsearch(const char* haystack, size_t hlen,
            const char* needle, size_t nlen)
{
    if (nlen == 0 || hlen < nlen) return nullptr;
    
    const char* end = haystack + hlen - nlen;
    for (const char* p = haystack; p <= end; ++p)
    {
        if (memcmp(p, needle, nlen) == 0) 
            return p;
    }
    return nullptr;
}

static std::string
extract_version_impl(const char* data, size_t size)
{
    const std::regex version_regex(
        R"(Mesa\s+\d+\.\d+\.\d+[^\s]*\s+\(git-[0-9a-f]+\))",
        std::regex_constants::icase
    );

    const char* cursor = data;
    const char* const end = data + size;

    while (cursor < end)
    {
        const char* base = memsearch(cursor, end - cursor, "Mesa", 4);
        if (!base) break;
        
        const char* start = (base - 128 < data) ? data : base - 128;
        const char* stop = (base + 256 > end) ? end : base + 256;
        const size_t seg_len = stop - start;
        
        std::string segment(start, seg_len);
        std::smatch match;
        
        if (std::regex_search(segment, match, version_regex))
            return match[0].str();

        cursor = base + 1;
    }
    return "";
}

class SafeFile
{
public:
    SafeFile(const char* path) : fd(-1)
    {
        fd = open(path, O_RDONLY);
        if (fd < 0)
        {
            LOGE("Failed to open %s: %s", path, strerror(errno));
        }
    }

    ~SafeFile()
    {
        if (fd >= 0) close(fd);
    }

    operator bool() const { return fd >= 0; }

    bool read(void* buffer, size_t size)
    {
        if (fd < 0) return false;

        size_t total = 0;
        while (total < size)
        {
            ssize_t n = ::read(fd, static_cast<char*>(buffer) + total, size - total);
            if (n <= 0)
            {
                if (n == 0) break;
                LOGE("Read error: %s", strerror(errno));
                return false;
            }
            total += n;
        }
        return total == size;
    }

    off_t size()
    {
        if (fd < 0) return -1;
        return lseek(fd, 0, SEEK_END);
    }

    bool seek_to_start()
    {
        if (fd < 0) return false;
        return lseek(fd, 0, SEEK_SET) == 0;
    }

private:
    int fd;
};

void getMesaInfoFromPath(const char* lib_path)
{
    if (!lib_path || lib_path[0] == '\0')
    {
        LOGW("Empty library path provided");
        return;
    }

    try
    {
        SafeFile file(lib_path);
        if (!file) return;

        const off_t size = file.size();
        if (size <= 0)
        {
            LOGE("Invalid file size: %ld", static_cast<long>(size));
            return;
        }

        if (!file.seek_to_start())
        {
            LOGE("Failed to seek to start");
            return;
        }

        std::vector<char> buffer(size);
        if (!file.read(buffer.data(), size))
        {
            LOGE("Failed to read entire file");
            return;
        }

        std::string version = extract_version_impl(buffer.data(), size);
        if (!version.empty())
        {
            LOGI("Detected info: %s", version.c_str());
        }
        else
        {
            LOGI("No Mesa info found in: %s", lib_path);
        }
    }
    catch (const std::exception& e)
    {
        LOGE("C++ exception: %s", e.what());
    }
    catch (...)
    {
        LOGE("Unknown C++ exception");
    }
}

void getMesaInfoFromHandle(void* dl_handle)
{
    if (!dl_handle)
    {
        LOGW("Null handle provided");
        return;
    }

    struct link_map* map = nullptr;
    if (dlinfo(dl_handle, RTLD_DI_LINKMAP, &map) == 0
       && map && map->l_name && map->l_name[0] != '\0')
    {
        LOGI("Using standard link_map method");
        getMesaInfoFromPath(map->l_name);
        return;
    }

    std::string lib_path = find_library_path(dl_handle);
    if (!lib_path.empty())
    {
        LOGI("Using /proc/self/maps method");
        getMesaInfoFromPath(lib_path.c_str());
        return;
    }

    LOGW("All extraction methods failed");
}