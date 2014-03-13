#ifndef DM_TREE_ANDROID_HEADER
#define DM_TREE_ANDROID_HEADER

#include <android_runtime/AndroidRuntime.h>

int registerDMTreeNatives(JNIEnv*);

short wbxml2xml(unsigned char *bufIn, int bufInLen, unsigned char *bufOut, int * bufOutLen);

#endif
