/* Header for class DMServiceMain.cc */

#ifndef DM_SERVICE_HEADER__
#define DM_SERVICE_HEADER__

#include <jni.h>
#ifdef __cplusplus
extern "C" {
#endif

#include <cutils/log.h>

  #ifndef LOGD
  #define LOGD(args...) ALOGD(args)
  #endif

  #ifndef LOGE
  #define LOGE(args...) ALOGE(args)
  #endif

  #ifndef LOGI
  #define LOGI(args...) ALOGI(args)
  #endif

  #ifndef LOGV
  #define LOGV(args...) ALOGV(args)
  #endif

  #ifndef LOGW
  #define LOGW(args...) ALOGW(args)
  #endif

static const char *const javaDMEnginePackage="com/android/omadm/service/NativeDM";

jobject getNetConnector();
jobject getDmAlert(JNIEnv *env);

#ifdef __cplusplus
}
#endif
#endif
