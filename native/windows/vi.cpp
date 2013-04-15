#ifdef __GNUC__
  #define __int64 long long
#endif

#include "javaforce_jni_WinAPI.h"
#include "videoInput.h"

class videoInput *vi = NULL;
int x = -1,y = -1;

JNIEXPORT jint JNICALL Java_javaforce_jni_WinAPI_vi_1num_1devices
  (JNIEnv *env, jclass cls)
{
  if (vi == NULL) vi = new videoInput();
  return vi->listDevices();
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_WinAPI_vi_1open
  (JNIEnv *env, jclass cls, jint id, jint x, jint y)
{
  if (vi == NULL) vi = new videoInput();
  return vi->setupDevice(id, x, y);
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_WinAPI_vi_1close
  (JNIEnv *env, jclass cls, jint id)
{
  if (vi == NULL) return FALSE;
  vi->stopDevice(id);
  return TRUE;
}

JNIEXPORT jintArray JNICALL Java_javaforce_jni_WinAPI_vi_1get_1info
  (JNIEnv *env, jclass cls, jint id)
{
  if (vi == NULL) return NULL;
  int size[2];
  jintArray ia;
  x = size[0] = vi->getWidth(id);
  y = size[1] = vi->getHeight(id);
  printf("videoInput:bufsiz = %d\r\n", vi->getSize(id));
  ia = env->NewIntArray(2);
  env->SetIntArrayRegion(ia, 0, 2, (jint*)size);
  return ia;
}

JNIEXPORT jintArray JNICALL Java_javaforce_jni_WinAPI_vi_1get_1frame
  (JNIEnv *env, jclass cls, jint id)
{
  if (vi == NULL) return NULL;
  if (x == -1) return NULL;
  if (!vi->isFrameNew(id)) return NULL;
  int px, a;
  jintArray ia = env->NewIntArray(x * y);
  unsigned char *pixels24 = (unsigned char*)malloc(x * y * 3);
  unsigned char *s8 = (unsigned char*)pixels24;
  unsigned char *pixels32 = (unsigned char*)malloc(x * y * 4);
  unsigned char *d8 = (unsigned char*)pixels32;

  vi->getPixels(id, pixels24, false, true);

  //convert 24bit to 32bit
  for(a=0;a<x * y;a++) {
    *(d8++) = *(s8++);                //R
    *(d8++) = *(s8++);                //G
    *(d8++) = *(s8++);                //B
    *(d8++) = 0xff;                   //a (opaque)
  }
  env->SetIntArrayRegion(ia, 0, x * y, (jint*)pixels32);
  free(pixels24);
  free(pixels32);
  return ia;
}
