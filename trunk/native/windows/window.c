#define WIN32_MEAN_AND_LEAN
#include <windows.h>

#ifdef __GNUC__
  #define __int64 long long
#endif

#include "javaforce_jni_WinAPI.h"

JNIEXPORT jint JNICALL Java_javaforce_jni_WinAPI_get_1window
  (JNIEnv *env, jclass cls, jstring jtitle)
{
  char *ctitle = (char*)(*env)->GetStringUTFChars(env, jtitle, NULL);
  int ret = (int)FindWindow(NULL, ctitle);
  (*env)->ReleaseStringUTFChars(env, jtitle, ctitle);
  return ret;
}

JNIEXPORT void JNICALL Java_javaforce_jni_WinAPI_gotoxy
  (JNIEnv *env, jclass cls, jint x, jint y)
{
  HANDLE h;
  COORD c = {x - 1, y - 1};

  h = GetStdHandle(STD_OUTPUT_HANDLE);

  SetConsoleCursorPosition(h, c);
}

JNIEXPORT void JNICALL Java_javaforce_jni_WinAPI_clrscr
  (JNIEnv *env, jclass cls)
{
  HANDLE h;
  int siz;
  COORD c = {0, 0};
  CONSOLE_SCREEN_BUFFER_INFO bi;

  h = GetStdHandle(STD_OUTPUT_HANDLE);

  GetConsoleScreenBufferInfo(h, &bi);

  siz = (bi.dwSize.X) * (bi.dwSize.Y);

  FillConsoleOutputCharacter(h, ' ', siz, c, (unsigned long*)&siz);

  Java_javaforce_jni_WinAPI_gotoxy(env, cls, 1, 1);
}
