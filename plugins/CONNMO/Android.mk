LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

#LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_SRC_FILES := src/com/android/sdm/plugins/connmo/ConnmoBackupService.java \
                   src/com/android/sdm/plugins/connmo/ConnmoConstants.java \
                   src/com/android/sdm/plugins/connmo/ConnmoPlugin.java \
                   src/com/android/sdm/plugins/connmo/ConnmoReceiver.java \
                   src/com/android/sdm/plugins/connmo/ConnmoService.java

LOCAL_STATIC_JAVA_LIBRARIES := com.android.omadm.plugin com.android.omadm.service.api

LOCAL_JAVA_LIBRARIES := telephony-common
LOCAL_PACKAGE_NAME := ConnMO
LOCAL_CERTIFICATE := platform

LOCAL_PRIVILEGED_MODULE := true

include $(BUILD_PACKAGE)
