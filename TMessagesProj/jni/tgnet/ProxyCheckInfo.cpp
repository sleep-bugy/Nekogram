/*
 * This is the source code of tgnet library v. 1.1
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2015-2018.
 */

#include "ProxyCheckInfo.h"
#include "ConnectionsManager.h"
#include "FileLog.h"

ProxyCheckInfo::~ProxyCheckInfo() {
#ifdef ANDROID
    if (ptr1 != nullptr) {
        DEBUG_DELREF("tgnet (2) request ptr1");
        JNIEnv *env = nullptr;
        bool attached = false;
        jint result = javaVm->GetEnv((void **) &env, JNI_VERSION_1_6);
        if (result == JNI_EDETACHED) {
            JavaVMAttachArgs args = {JNI_VERSION_1_6, nullptr, nullptr};
            if (javaVm->AttachCurrentThread(&env, &args) == JNI_OK) {
                attached = true;
            } else {
                env = nullptr;
            }
        } else if (result != JNI_OK) {
            env = nullptr;
        }
        if (env != nullptr) {
            env->DeleteGlobalRef(ptr1);
        }
        if (attached) {
            javaVm->DetachCurrentThread();
        }
        ptr1 = nullptr;
    }
#endif
}
