//
// Created by eel on 2016-06-27.
//
#include <android/log.h>

JNIEXPORT jstringJNICALL Java_com_eulerspace_eel_udprx_MainActivity_getHello(JNIEnv *env)
{
    //LOGE("log string from ndk.");
    return (*env)->NewStringUTF(env, "Hello From JNI!");
}
