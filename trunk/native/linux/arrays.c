#include "javaforce_jni_LnxAPI.h"

/* Native Code array conversions (platform-dependant) */

// Created : Mar 24, 2012

JNIEXPORT jshortArray JNICALL Java_javaforce_jni_LnxAPI_byteArray2shortArray
  (JNIEnv *env, jclass cls, jbyteArray ba)
{
  if (ba == NULL) return NULL;
  int size = (*env)->GetArrayLength(env, ba);
  size >>= 1;
  if (size == 0) return NULL;
  void *data = (*env)->GetByteArrayElements(env, ba, NULL);

  jshortArray sa = (*env)->NewShortArray(env, size);
  (*env)->SetShortArrayRegion(env, sa, 0, size, data);

  (*env)->ReleaseByteArrayElements(env, ba, data, JNI_ABORT);
  return sa;
}

JNIEXPORT jintArray JNICALL Java_javaforce_jni_LnxAPI_byteArray2intArray
  (JNIEnv *env, jclass cls, jbyteArray ba)
{
  if (ba == NULL) return NULL;
  int size = (*env)->GetArrayLength(env, ba);
  size >>= 2;
  if (size == 0) return NULL;
  void *data = (*env)->GetByteArrayElements(env, ba, NULL);

  jintArray ia = (*env)->NewShortArray(env, size);
  (*env)->SetShortArrayRegion(env, ia, 0, size, data);

  (*env)->ReleaseByteArrayElements(env, ba, data, JNI_ABORT);
  return ia;
}

JNIEXPORT jbyteArray JNICALL Java_javaforce_jni_LnxAPI_shortArray2byteArray
  (JNIEnv *env, jclass cls, jshortArray sa)
{
  if (sa == NULL) return NULL;
  int size = (*env)->GetArrayLength(env, sa);
  size <<= 1;
  if (size == 0) return NULL;
  void *data = (*env)->GetShortArrayElements(env, sa, NULL);

  jbyteArray ba = (*env)->NewByteArray(env, size);
  (*env)->SetByteArrayRegion(env, ba, 0, size, data);

  (*env)->ReleaseShortArrayElements(env, sa, data, JNI_ABORT);
  return ba;
}

JNIEXPORT jbyteArray JNICALL Java_javaforce_jni_LnxAPI_intArray2byteArray
  (JNIEnv *env, jclass cls, jintArray ia)
{
  if (ia == NULL) return NULL;
  int size = (*env)->GetArrayLength(env, ia);
  size <<= 2;
  if (size == 0) return NULL;
  void *data = (*env)->GetIntArrayElements(env, ia, NULL);

  jbyteArray ba = (*env)->NewByteArray(env, size);
  (*env)->SetByteArrayRegion(env, ba, 0, size, data);

  (*env)->ReleaseIntArrayElements(env, ia, data, JNI_ABORT);
  return ba;
}
