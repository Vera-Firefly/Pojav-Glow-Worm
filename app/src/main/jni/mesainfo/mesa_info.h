//
// Created by Vera-Firefly on 29/06/2025
//

#ifndef MESA_INFO_H
#define MESA_INFO_H

#ifdef __cplusplus
extern "C" {
#endif

void getMesaInfoFromPath(const char* lib_path);
void getMesaInfoFromHandle(void* dl_handle);

#ifdef __cplusplus
}
#endif

#endif // MESA_INFO_H