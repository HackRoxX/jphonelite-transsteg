#include <sys/inotify.h>

#include "javaforce_jni_LnxAPI.h"

/** inotify */

// Created Jan 24, 2013 (for jDrive)

JNIEXPORT jint JNICALL Java_javaforce_jni_LnxAPI_inotify_1init
  (JNIEnv *env, jclass cls)
{
  return inotify_init();
}

JNIEXPORT void JNICALL Java_javaforce_jni_LnxAPI_inotigy_1uninit
  (JNIEnv *env, jclass cls, jint fd)
{
  close(fd);
}

#define MASK IN_CLOSE_WRITE | IN_CREATE | IN_DELETE | IN_MOVED_FROM | IN_MOVED_TO

JNIEXPORT jint JNICALL Java_javaforce_jni_LnxAPI_inotify_1add_1watch
  (JNIEnv *env, jclass cls, jint fd, jstring path)
{
  char *cpath = (char*)(*env)->GetStringUTFChars(env, path, NULL);
  int wd = inotify_add_watch(fd, cpath, MASK);
  (*env)->ReleaseStringUTFChars(env, path, cpath);
  return wd;
}

JNIEXPORT void JNICALL Java_javaforce_jni_LnxAPI_inotify_1rm_1watch
  (JNIEnv *env, jclass cls, jint fd, jint wd)
{
  inotify_rm_watch(fd, wd);
}

struct stringarray {
  int length;
  char **strs;
}

static void addString(struct stringarray sa, char *str) {
  sa.length++;
  sa.strs = realloc(sa.strs, sa.length * sizeof(char*));
  sa.strs[sa.length-1] = str;
}

#define EVENT_SIZE ( sizeof (struct inotify_event) )
#define EVENT_BUF_LEN ( 4 * ( EVENT_SIZE + MAX_PATH ) )

JNIEXPORT jobjectArray JNICALL Java_javaforce_jni_LnxAPI_inotify_1read
  (JNIEnv *env, jclass cls, jint fd)
{
  char buffer[EVENT_BUF_LEN];
  struct stringarray sa;
  int a, i = 0;
  struct inotify_event *event;
  int length = read(fd, buffer, EVENT_BUF_LEN);
  char str[MAX_PATH + 16];
  jobjectArray ret;

  sa.length = 0;
  sa.strs = NULL;
  while ( i < length ) {
    event = (struct inotify_event * ) &buffer[ i ];
    i += EVENT_SIZE + event->len;
    if ( event->len == 0) continue;
    if ( event->mask & IN_CREATE ) {
      if ( event->mask & IN_ISDIR ) {
        sprintf(str, "DIR_CREATE:%s", event->name);
        addString(sa, str);
      }
      else {
        sprintf(str, "FILE_CREATE:%s", event->name);
        addString(sa, str);
      }
    }
    else if ( event->mask & IN_DELETE ) {
      if ( event->mask & IN_ISDIR ) {
        sprintf(str, "DIR_DELETE:%s", event->name);
        addString(sa, str);
      }
      else {
        sprintf(str, "FILE_DELETE:%s", event->name);
        addString(sa, str);
      }
    }
    else if ( event->mask & IN_CLOSE_WRITE ) {
      if ( event->mask & IN_ISDIR ) {
        sprintf(str, "DIR_WRITE:%s", event->name);
        addString(sa, str);
      }
      else {
        sprintf(str, "FILE_WRITE:%s", event->name);
        addString(sa, str);
      }
    }
    else if ( event->mask & IN_MOVED_FROM ) {
      if ( event->mask & IN_ISDIR ) {
        sprintf(str, "DIR_MOVED_FROM:%s", event->name);
        addString(sa, str);
      }
      else {
        sprintf(str, "FILE_MOVED_FROM:%s", event->name);
        addString(sa, str);
      }
    }
    else if ( event->mask & IN_MOVED_TO ) {
      if ( event->mask & IN_ISDIR ) {
        sprintf(str, "DIR_MOVED_TO:%s", event->name);
        addString(sa, str);
      }
      else {
        sprintf(str, "FILE_MOVED_TO:%s", event->name);
        addString(sa, str);
      }
    }
  }
  if (sa.length == 0) {
    return NULL;
  }
  //now convert arraystring to jobjectArray
  ret = (jobjectArray)(*env)->NewObjectArray(env, sa.length,(*env)->FindClass(env, "java/lang/String"),(*env)->NewStringUTF(env, ""));
  for(a=0;a<sa.length;a++) {
    (*env)->SetObjectArrayElement(env, ret, i, (*env)->NewStringUTF(env, sa.strs[a]));
    free(sa.strs[a]);
  }
  free(sa.strs);
  return ret;
}
