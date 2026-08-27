#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <stdint.h>
#include <stdlib.h>
#include <vulkan/vulkan.h>

#include "driver_helper/nsbypass.h"

#define LOG_TAG "VulkanCheck"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOAD_VK_FUNC(name) PFN_##name p##name = (PFN_##name) dlsym(vulkanHandle, #name)

JNIEXPORT jobject JNICALL
Java_net_kdt_pojavlaunch_firefly_utils_VulkanChecker_nativeCheckVulkan(
        JNIEnv *env,
        jclass clazz,
        jstring jNativeDir,
        jstring jCacheDir
) {
    (void) clazz;

    const char *nativeDir = jNativeDir ? (*env)->GetStringUTFChars(env, jNativeDir, NULL) : NULL;
    const char *cacheDir = jCacheDir ? (*env)->GetStringUTFChars(env, jCacheDir, NULL) : NULL;
    void *vulkanHandle = nativeDir && cacheDir
            ? loadTurnipVulkanForPath(nativeDir, cacheDir)
            : NULL;

    if (nativeDir) (*env)->ReleaseStringUTFChars(env, jNativeDir, nativeDir);
    if (cacheDir) (*env)->ReleaseStringUTFChars(env, jCacheDir, cacheDir);

    if (!vulkanHandle)
        vulkanHandle = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!vulkanHandle) {
        LOGE("Unable to load a Vulkan loader");
        return NULL;
    }

    LOAD_VK_FUNC(vkEnumerateInstanceVersion);
    LOAD_VK_FUNC(vkCreateInstance);
    LOAD_VK_FUNC(vkDestroyInstance);
    LOAD_VK_FUNC(vkEnumeratePhysicalDevices);
    LOAD_VK_FUNC(vkGetPhysicalDeviceFeatures);
    LOAD_VK_FUNC(vkEnumerateDeviceExtensionProperties);
    LOAD_VK_FUNC(vkGetPhysicalDeviceFeatures2);
    LOAD_VK_FUNC(vkGetPhysicalDeviceProperties);
    LOAD_VK_FUNC(vkGetPhysicalDeviceProperties2);

    if (!pvkGetPhysicalDeviceFeatures2)
        pvkGetPhysicalDeviceFeatures2 = (PFN_vkGetPhysicalDeviceFeatures2) dlsym(vulkanHandle, "vkGetPhysicalDeviceFeatures2KHR");
    if (!pvkGetPhysicalDeviceProperties2)
        pvkGetPhysicalDeviceProperties2 = (PFN_vkGetPhysicalDeviceProperties2) dlsym(vulkanHandle, "vkGetPhysicalDeviceProperties2KHR");

    if (!pvkCreateInstance || !pvkDestroyInstance || !pvkEnumeratePhysicalDevices ||
        !pvkEnumerateDeviceExtensionProperties || !pvkGetPhysicalDeviceProperties) {
        LOGE("The Vulkan loader is missing required entry points");
        dlclose(vulkanHandle);
        return NULL;
    }

    uint32_t instanceVersion = VK_API_VERSION_1_0;
    if (pvkEnumerateInstanceVersion)
        pvkEnumerateInstanceVersion(&instanceVersion);

    VkApplicationInfo applicationInfo = {
        .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .pApplicationName = "Pojav Glow-Worm",
        .applicationVersion = VK_MAKE_VERSION(1, 0, 0),
        .apiVersion = instanceVersion
    };
    VkInstanceCreateInfo createInfo = {
        .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
        .pApplicationInfo = &applicationInfo
    };
    VkInstance instance = VK_NULL_HANDLE;
    if (pvkCreateInstance(&createInfo, NULL, &instance) != VK_SUCCESS) {
        LOGE("vkCreateInstance failed");
        dlclose(vulkanHandle);
        return NULL;
    }

    uint32_t deviceCount = 0;
    if (pvkEnumeratePhysicalDevices(instance, &deviceCount, NULL) != VK_SUCCESS || deviceCount == 0) {
        LOGE("No Vulkan physical device is available");
        pvkDestroyInstance(instance, NULL);
        dlclose(vulkanHandle);
        return NULL;
    }
    VkPhysicalDevice *devices = malloc(sizeof(VkPhysicalDevice) * deviceCount);
    if (!devices || pvkEnumeratePhysicalDevices(instance, &deviceCount, devices) != VK_SUCCESS) {
        free(devices);
        pvkDestroyInstance(instance, NULL);
        dlclose(vulkanHandle);
        return NULL;
    }
    VkPhysicalDevice device = devices[0];
    free(devices);

    uint32_t deviceVersion;
    if (pvkGetPhysicalDeviceProperties2) {
        VkPhysicalDeviceProperties2 properties = {
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2
        };
        pvkGetPhysicalDeviceProperties2(device, &properties);
        deviceVersion = properties.properties.apiVersion;
    } else {
        VkPhysicalDeviceProperties properties;
        pvkGetPhysicalDeviceProperties(device, &properties);
        deviceVersion = properties.apiVersion;
    }

    uint32_t extensionCount = 0;
    if (pvkEnumerateDeviceExtensionProperties(device, NULL, &extensionCount, NULL) != VK_SUCCESS) {
        pvkDestroyInstance(instance, NULL);
        dlclose(vulkanHandle);
        return NULL;
    }
    VkExtensionProperties *extensions = malloc(sizeof(VkExtensionProperties) * extensionCount);
    if (!extensions || pvkEnumerateDeviceExtensionProperties(device, NULL, &extensionCount, extensions) != VK_SUCCESS) {
        free(extensions);
        pvkDestroyInstance(instance, NULL);
        dlclose(vulkanHandle);
        return NULL;
    }

    jclass listClass = (*env)->FindClass(env, "java/util/ArrayList");
    jmethodID listInit = (*env)->GetMethodID(env, listClass, "<init>", "()V");
    jmethodID listAdd = (*env)->GetMethodID(env, listClass, "add", "(Ljava/lang/Object;)Z");
    jobject extensionList = (*env)->NewObject(env, listClass, listInit);
    for (uint32_t i = 0; i < extensionCount; i++) {
        jstring name = (*env)->NewStringUTF(env, extensions[i].extensionName);
        (*env)->CallBooleanMethod(env, extensionList, listAdd, name);
        (*env)->DeleteLocalRef(env, name);
    }
    free(extensions);

    VkBool32 multiDrawIndirect = VK_FALSE;
    VkBool32 fillModeNonSolid = VK_FALSE;
    VkBool32 samplerAnisotropy = VK_FALSE;
    VkBool32 shaderDrawParameters = VK_FALSE;
    VkBool32 timelineSemaphore = VK_FALSE;
    VkBool32 hostQueryReset = VK_FALSE;
    VkBool32 synchronization2 = VK_FALSE;
    VkBool32 dynamicRendering = VK_FALSE;
    VkBool32 vertexAttributeInstanceRateDivisor = VK_FALSE;

    if (pvkGetPhysicalDeviceFeatures2) {
        VkPhysicalDeviceVertexAttributeDivisorFeaturesEXT divisor = {
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VERTEX_ATTRIBUTE_DIVISOR_FEATURES_EXT
        };
        VkPhysicalDeviceDynamicRenderingFeaturesKHR dynamic = {
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES_KHR,
            .pNext = &divisor
        };
        VkPhysicalDeviceSynchronization2FeaturesKHR synchronization = {
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SYNCHRONIZATION_2_FEATURES_KHR,
            .pNext = &dynamic
        };
        VkPhysicalDeviceHostQueryResetFeatures hostQuery = {
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_HOST_QUERY_RESET_FEATURES,
            .pNext = &synchronization
        };
        VkPhysicalDeviceTimelineSemaphoreFeatures timeline = {
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TIMELINE_SEMAPHORE_FEATURES,
            .pNext = &hostQuery
        };
        VkPhysicalDeviceShaderDrawParametersFeatures shaderDraw = {
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_DRAW_PARAMETERS_FEATURES,
            .pNext = &timeline
        };
        VkPhysicalDeviceFeatures2 features = {
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2,
            .pNext = &shaderDraw
        };
        pvkGetPhysicalDeviceFeatures2(device, &features);
        multiDrawIndirect = features.features.multiDrawIndirect;
        fillModeNonSolid = features.features.fillModeNonSolid;
        samplerAnisotropy = features.features.samplerAnisotropy;
        shaderDrawParameters = shaderDraw.shaderDrawParameters;
        timelineSemaphore = timeline.timelineSemaphore;
        hostQueryReset = hostQuery.hostQueryReset;
        synchronization2 = synchronization.synchronization2;
        dynamicRendering = dynamic.dynamicRendering;
        vertexAttributeInstanceRateDivisor = divisor.vertexAttributeInstanceRateDivisor;
    } else if (pvkGetPhysicalDeviceFeatures) {
        VkPhysicalDeviceFeatures features;
        pvkGetPhysicalDeviceFeatures(device, &features);
        multiDrawIndirect = features.multiDrawIndirect;
        fillModeNonSolid = features.fillModeNonSolid;
        samplerAnisotropy = features.samplerAnisotropy;
    }

    jclass mapClass = (*env)->FindClass(env, "java/util/HashMap");
    jmethodID mapInit = (*env)->GetMethodID(env, mapClass, "<init>", "()V");
    jmethodID mapPut = (*env)->GetMethodID(env, mapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    jobject featureMap = (*env)->NewObject(env, mapClass, mapInit);
    jclass booleanClass = (*env)->FindClass(env, "java/lang/Boolean");
    jmethodID valueOf = (*env)->GetStaticMethodID(env, booleanClass, "valueOf", "(Z)Ljava/lang/Boolean;");

#define PUT_FEATURE(key, value) do { \
    jstring featureKey = (*env)->NewStringUTF(env, key); \
    jobject featureValue = (*env)->CallStaticObjectMethod(env, booleanClass, valueOf, (jboolean) value); \
    (*env)->CallObjectMethod(env, featureMap, mapPut, featureKey, featureValue); \
    (*env)->DeleteLocalRef(env, featureKey); \
    (*env)->DeleteLocalRef(env, featureValue); \
} while (0)
    PUT_FEATURE("multiDrawIndirect", multiDrawIndirect);
    PUT_FEATURE("fillModeNonSolid", fillModeNonSolid);
    PUT_FEATURE("samplerAnisotropy", samplerAnisotropy);
    PUT_FEATURE("shaderDrawParameters", shaderDrawParameters);
    PUT_FEATURE("timelineSemaphore", timelineSemaphore);
    PUT_FEATURE("hostQueryReset", hostQueryReset);
    PUT_FEATURE("synchronization2", synchronization2);
    PUT_FEATURE("dynamicRendering", dynamicRendering);
    PUT_FEATURE("vertexAttributeInstanceRateDivisor", vertexAttributeInstanceRateDivisor);
#undef PUT_FEATURE

    jclass capabilitiesClass = (*env)->FindClass(env, "net/kdt/pojavlaunch/firefly/utils/VulkanCapabilities");
    jmethodID constructor = (*env)->GetMethodID(env, capabilitiesClass, "<init>", "(IIILjava/util/List;Ljava/util/Map;)V");
    jobject result = (*env)->NewObject(env, capabilitiesClass, constructor,
            (jint) VK_API_VERSION_MAJOR(deviceVersion),
            (jint) VK_API_VERSION_MINOR(deviceVersion),
            (jint) VK_API_VERSION_PATCH(deviceVersion),
            extensionList, featureMap);

    (*env)->DeleteLocalRef(env, listClass);
    (*env)->DeleteLocalRef(env, mapClass);
    (*env)->DeleteLocalRef(env, booleanClass);
    (*env)->DeleteLocalRef(env, capabilitiesClass);
    pvkDestroyInstance(instance, NULL);
    dlclose(vulkanHandle);
    LOGI("Vulkan check completed");
    return result;
}
