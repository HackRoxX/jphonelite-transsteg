//array conversions (platform-endian)

#ifdef __GNUC__
  #define __int64 long long
#else
  #define NULL 0
#endif

#include "javaforce_jni_WinAPI.h"

JNIEXPORT jshortArray JNICALL Java_javaforce_jni_WinAPI_byteArray2shortArray
  (JNIEnv *env, jclass cls, jbyteArray ba)
{
  int size;
  void *data;
  jshortArray sa;

  if (ba == NULL) return NULL;
  size = (*env)->GetArrayLength(env, ba);
  size >>= 1;
  if (size == 0) return NULL;
  data = (*env)->GetByteArrayElements(env, ba, NULL);

  sa = (*env)->NewShortArray(env, size);
  (*env)->SetShortArrayRegion(env, sa, 0, size, data);

  (*env)->ReleaseByteArrayElements(env, ba, data, JNI_ABORT);
  return sa;
}

JNIEXPORT jintArray JNICALL Java_javaforce_jni_WinAPI_byteArray2intArray
  (JNIEnv *env, jclass cls, jbyteArray ba)
{
  int size;
  void *data;
  jintArray ia;

  if (ba == NULL) return NULL;
  size = (*env)->GetArrayLength(env, ba);
  size >>= 2;
  if (size == 0) return NULL;
  data = (*env)->GetByteArrayElements(env, ba, NULL);

  ia = (*env)->NewShortArray(env, size);
  (*env)->SetShortArrayRegion(env, ia, 0, size, data);

  (*env)->ReleaseByteArrayElements(env, ba, data, JNI_ABORT);
  return ia;
}

JNIEXPORT jbyteArray JNICALL Java_javaforce_jni_WinAPI_shortArray2byteArray
  (JNIEnv *env, jclass cls, jshortArray sa)
{
  int size;
  void *data;
  jbyteArray ba;

  if (sa == NULL) return NULL;
  size = (*env)->GetArrayLength(env, sa);
  size <<= 1;
  if (size == 0) return NULL;
  data = (*env)->GetShortArrayElements(env, sa, NULL);

  ba = (*env)->NewByteArray(env, size);
  (*env)->SetByteArrayRegion(env, ba, 0, size, data);

  (*env)->ReleaseShortArrayElements(env, sa, data, JNI_ABORT);
  return ba;
}

JNIEXPORT jbyteArray JNICALL Java_javaforce_jni_WinAPI_intArray2byteArray
  (JNIEnv *env, jclass cls, jintArray ia)
{
  int size;
  void *data;
  jbyteArray ba;

  if (ia == NULL) return NULL;
  size = (*env)->GetArrayLength(env, ia);
  size <<= 2;
  if (size == 0) return NULL;
  data = (*env)->GetIntArrayElements(env, ia, NULL);

  ba = (*env)->NewByteArray(env, size);
  (*env)->SetByteArrayRegion(env, ba, 0, size, data);

  (*env)->ReleaseIntArrayElements(env, ia, data, JNI_ABORT);
  return ba;
}
