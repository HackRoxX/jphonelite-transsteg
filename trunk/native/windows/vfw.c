#define WIN32_MEAN_AND_LEAN
#include <windows.h>
#include <vfw.h>

#ifdef __GNUC__
  #define __int64 long long
#endif

#include "javaforce_jni_WinAPI.h"

#ifdef __GNUC__
  #define DLLEXPORT
  #define SHARED __attribute__((section ("shared"), shared))
#else
  #define DLLEXPORT __declspec(dllexport)
  #define SHARED
#endif

//Define "shared/exported" data
DLLEXPORT int cx SHARED = 0;
DLLEXPORT int cy SHARED = 0;
DLLEXPORT char *pixels24 SHARED = (char*)NULL;
DLLEXPORT char *pixels32 SHARED = (char*)NULL;
DLLEXPORT HWND hWnd SHARED = NULL;

//non-shared data
HANDLE lock = NULL;

static void lock_on() {
  WaitForSingleObject(lock, INFINITE);
}

static void lock_off() {
  ReleaseMutex(lock);
}

//adjust size for borders/titlebar
static void AdjustSize(int *x, int *y) {
  if (y) *y += GetSystemMetrics(SM_CYCAPTION);  //titlebar
  if (x) *x += GetSystemMetrics(SM_CXFIXEDFRAME);  //borders
  if (y) *y += GetSystemMetrics(SM_CYFIXEDFRAME);
}

static LRESULT CALLBACK fpProc(HWND hWnd, LPVIDEOHDR lpVHdr) {
  //copy data to buffer
  lock_on();
  memcpy(pixels24, lpVHdr->lpData, cx * cy * 3);
  lock_off();
  return TRUE;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_WinAPI_vfw_1open
  (JNIEnv *env, jclass cls, jint hwndParent, jint xpos, jint ypos, jint winxsize, jint winysize, jint camxsize, jint camysize)
{
  int idx = 0, fps=10;  //TODO:add to args list
  LPBITMAPINFO lpbi;
  DWORD dwSize;
  char msg[128];
  int wx, wy;

  hWnd = capCreateCaptureWindow (
    TEXT("Camera"),              // window name if pop-up
    WS_CHILD | WS_VISIBLE,                  // window style
    xpos, ypos, winxsize, winysize,    // window position and dimensions
    (HWND) hwndParent,           // window Parent
    0);                          // child ID?
  if (hWnd == NULL) return FALSE;
  if (!capDriverConnect(hWnd, idx)) {
    printf("capDriverConnect() Failed : %d (hWnd=%x)\r\n", GetLastError(), hWnd);
    return FALSE;
  }
  //GetFormat to determine size
  dwSize = capGetVideoFormatSize(hWnd);  //usually 40 bytes
  lpbi = (LPBITMAPINFO)malloc(dwSize);
  memset(lpbi, 0, dwSize);
  lpbi->bmiHeader.biSize = dwSize;  //40
//  capDlgVideoFormat(hWnd);  //test
  capGetVideoFormat(hWnd, lpbi, dwSize);
  if ((camxsize != -1) && (camysize != -1)) {
    lpbi->bmiHeader.biWidth = camxsize;
    lpbi->bmiHeader.biHeight = camysize;
  }
  cx = lpbi->bmiHeader.biWidth;
  cy = lpbi->bmiHeader.biHeight;
  lpbi->bmiHeader.biCompression = BI_RGB;
  lpbi->bmiHeader.biSizeImage = cx * cy * 3;
  lpbi->bmiHeader.biBitCount = 24;
  if (!capSetVideoFormat(hWnd, lpbi, dwSize)) {
    free(lpbi);
    Java_javaforce_jni_WinAPI_vfw_1close(NULL, NULL);
    printf("VFW:Error:capSetVideoFormat() Failed : %d\r\n", GetLastError());
    return FALSE;
  }
  free(lpbi);
  wx = cx;
  wy = cy;
  AdjustSize(&wx,&wy);
  SetWindowPos(hWnd,NULL,0,0,wx,wy,SWP_NOMOVE | SWP_SHOWWINDOW);
  pixels24 = (char*)malloc(cx * cy * 3);
  pixels32 = (char*)malloc(cx * cy * 4);
  if (lock == NULL) lock = CreateMutexA(NULL, FALSE, NULL);
  SetFocus(hWnd);
  capSetCallbackOnFrame(hWnd, &fpProc);
  capPreviewRate(hWnd, 1000 / fps);
  capPreview(hWnd, TRUE);
  return TRUE;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_WinAPI_vfw_1close
  (JNIEnv *env, jclass cls)
{
  if (hWnd == NULL) return 0;
  capPreview(hWnd, FALSE);
  capDriverDisconnect(hWnd);
  CloseWindow(hWnd);
  hWnd = NULL;
  if (pixels24 != NULL) {
    free(pixels24);
    pixels24 = NULL;
  }
  if (pixels32 != NULL) {
    free(pixels32);
    pixels32 = NULL;
  }
  return 1;
}

JNIEXPORT jintArray JNICALL Java_javaforce_jni_WinAPI_vfw_1get_1info
  (JNIEnv *env, jclass cls)
{
  int size[2];
  jintArray ia;
  size[0] = cx;
  size[1] = cy;
  ia = (*env)->NewIntArray(env, 2);
  (*env)->SetIntArrayRegion(env, ia, 0, 2, (jint*)size);
  return ia;
}

JNIEXPORT jintArray JNICALL Java_javaforce_jni_WinAPI_vfw_1get_1frame
  (JNIEnv *env, jclass cls)
{
  int px, a, b;
  jintArray ia = (*env)->NewIntArray(env, cx * cy);
  unsigned char *s8 = (unsigned char*)pixels24;
  unsigned char *d8 = (unsigned char*)pixels32;

  lock_on();
  //convert 24bit to 32bit and flip upside down (I mean, right side up, err... proper side up)
  s8 += cx * cy * 3;
  for(a=0;a<cy;a++) {
    s8 -= cx * 3;
    for(b=0;b<cx;b++) {
      *(d8++) = *(s8++);                //R
      *(d8++) = *(s8++);                //G
      *(d8++) = *(s8++);                //B
      *(d8++) = 0xff;                   //a (opaque)
    }
    s8 -= cx * 3;
  }
  lock_off();
  (*env)->SetIntArrayRegion(env, ia, 0, cx * cy, (jint*)pixels32);
  return ia;
}

static int IsQueueEmpty() {
  MSG msg;

  if (PeekMessage(&msg, NULL, 0, 0, PM_NOREMOVE)) return FALSE;
  return TRUE;
}


JNIEXPORT void JNICALL Java_javaforce_jni_WinAPI_vfw_1process
  (JNIEnv *env, jclass cls)
{
  MSG msg;
  int cnt = 0;

  while (!IsQueueEmpty()) {
    GetMessage(&msg, NULL, 0, 0);
    DispatchMessage(&msg);    //dispatches message
    cnt++;
    if (cnt == 16) break;
  }
}
