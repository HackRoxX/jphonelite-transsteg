#define WIN32_MEAN_AND_LEAN
#include <windows.h>

#ifdef __GNUC__
  #define __int64 long long
#endif

#include "javaforce_jni_WinAPI.h"

#define NUM_BUFFERS 16  //NOTE:2 is NOT enough - it will under/over buffer
#define NUM_IDS 32  //snd_count

static HWAVEOUT waveOut[NUM_IDS];
static HWAVEIN waveIn[NUM_IDS];
static WAVEHDR buffers[NUM_IDS][NUM_BUFFERS];
static int bufsiz[NUM_IDS];

JNIEXPORT void JNICALL Java_javaforce_jni_WinAPI_snd_1clear_1id
  (JNIEnv *env, jclass cls, jint id)
{
  WAVEHDR *wh;
  int a;
  for (a = 0;a < NUM_BUFFERS;a++) {
    wh = &buffers[id][a];
    free(wh->lpData);
    wh->lpData = NULL;
  }
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_WinAPI_snd_1play
  (JNIEnv *env, jclass cls, jint id, jint chs, jint freq, jint bits, jint _bufsiz)
{
  int bytes, ret, a;
  WAVEFORMATEX wfex;
  WAVEHDR *wh;

  bufsiz[id] = _bufsiz;
  switch (bits) {
    case 8: bytes = 1; break;
    case 16: bytes = 2; break;
    default: return 0;
  }

  //prepare WAVEFORMATEX
  memset(&wfex, 0, sizeof(wfex));
  wfex.wFormatTag = WAVE_FORMAT_PCM;
  wfex.nChannels = chs;
  wfex.nSamplesPerSec = freq;
  wfex.nAvgBytesPerSec = freq * bytes;
  wfex.nBlockAlign = bytes;
  wfex.wBitsPerSample = bits;
  wfex.cbSize = 0; //no extra info

  //open device for output

  ret = waveOutOpen((LPHWAVEOUT) & waveOut[id], (unsigned int)WAVE_MAPPER,
                    &wfex, (DWORD_PTR)NULL, 0, WAVE_ALLOWSYNC);

  if (ret != MMSYSERR_NOERROR) {
    printf("err:windows:waveOutOpen() failed\r\n");
    return 0;
  }

  //create buffers
  for (a = 0;a < NUM_BUFFERS;a++) {
    wh = &buffers[id][a];
    wh->lpData = (char*)malloc(bufsiz[id]);
    wh->dwBufferLength = bufsiz[id];
    wh->dwBytesRecorded = 0;
    wh->dwUser = 0;
    wh->dwFlags = 0;
    wh->dwLoops = 0;
    wh->lpNext = (WAVEHDR *) NULL;  //reserved
    wh->reserved = 0;   //reserved
    ret = waveOutPrepareHeader((HWAVEOUT)waveOut[id], wh, sizeof(WAVEHDR));
    if (ret != MMSYSERR_NOERROR) {
      printf("err:windows:waveOutPrepareHeader() failed\r\n");
      return 0;
    }
  }

  return 1;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_WinAPI_snd_1play_1write
  (JNIEnv *env, jclass cls, jint id, jshortArray sa)
{
  int buflen;
  jshort *sams;
  WAVEHDR *wh;
  int a, done = 0, ret;

  //insert a buffer
  //search for avail buffer and copy next buffer in
  for (a = 0;a < NUM_BUFFERS;a++) {
    wh = &buffers[id][a];
    if ((wh->dwFlags & WHDR_INQUEUE) == 0) {
      buflen = (*env)->GetArrayLength(env, sa) * 2;  //in bytes
      sams = (*env)->GetShortArrayElements(env, sa, NULL);
      memcpy((void*)wh->lpData, (void*)sams, buflen);
      waveOutWrite(waveOut[id], wh, sizeof(WAVEHDR));
      (*env)->ReleaseShortArrayElements(env, sa, sams, JNI_ABORT);
      return 1;
    }
  }
  return 0;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_WinAPI_snd_1play_1stop
  (JNIEnv *env, jclass cls, jint id)
{
  int ret;
  ret = waveOutReset(waveOut[id]);
  if (ret != MMSYSERR_NOERROR) return 0;
  return 1;
}

JNIEXPORT jint JNICALL Java_javaforce_jni_WinAPI_snd_1play_1buffers_1full
  (JNIEnv *env, jclass cls, jint id)
{
  WAVEHDR *wh;
  int a;
  int cnt = 0;

  for (a = 0;a < NUM_BUFFERS;a++) {
    wh = &buffers[id][a];
    if ((wh->dwFlags) & WHDR_INQUEUE) cnt++;
  }
  return cnt;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_WinAPI_snd_1record
  (JNIEnv *env, jclass cls, jint id, jint chs, jint freq, jint bits, jint _bufsiz)
{
  int bytes, ret, a;
  WAVEFORMATEX wfex;
  WAVEHDR *wh;

  bufsiz[id] = _bufsiz;
  switch (bits) {
    case 8: bytes = 1; break;
    case 16: bytes = 2; break;
    default: return 0;
  }

  //prepare WAVEFORMATEX
  memset(&wfex, 0, sizeof(wfex));
  wfex.wFormatTag = WAVE_FORMAT_PCM;
  wfex.nChannels = chs;
  wfex.nSamplesPerSec = freq;
  wfex.nAvgBytesPerSec = freq * bytes;
  wfex.nBlockAlign = bytes;
  wfex.wBitsPerSample = bits;
  wfex.cbSize = 0; //no extra info

  //open device for output

  ret = waveInOpen((LPHWAVEIN) & waveIn[id], (unsigned int)WAVE_MAPPER,
                    &wfex, (DWORD_PTR)NULL, 0, 0);

  if (ret != MMSYSERR_NOERROR) {
    printf("err:windows:waveInOpen() failed\r\n");
    return 0;
  }

  //create buffers
  for (a = 0;a < NUM_BUFFERS;a++) {
    wh = &buffers[id][a];
    wh->lpData = (char*)malloc(bufsiz[id]);
    wh->dwBufferLength = bufsiz[id];
    wh->dwBytesRecorded = 0;
    wh->dwUser = 0;
    wh->dwFlags = 0;
    wh->dwLoops = 0;
    wh->lpNext = (WAVEHDR *) NULL;  //reserved
    wh->reserved = 0;   //reserved

    ret = waveInPrepareHeader((HWAVEIN)waveIn[id], wh, sizeof(WAVEHDR));
    if (ret != MMSYSERR_NOERROR) {
      printf("err:windows:waveInPrepareHeader() failed\r\n");
      return 0;
    }
    ret = waveInAddBuffer((HWAVEIN)waveIn[id], &buffers[id][a], sizeof(WAVEHDR));
    if (ret != MMSYSERR_NOERROR) {
      return 0;
    }
  }

  //start recording
  ret = waveInStart((HWAVEIN)waveIn[id]);
  if (ret != MMSYSERR_NOERROR) {
    return 0;
  }

  return 1;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_WinAPI_snd_1record_1read
  (JNIEnv *env, jclass cls, jint id, jshortArray sa)
{
  WAVEHDR *wh;
  int a;
  jshort *sams;

  for (a = 0;a < NUM_BUFFERS;a++) {
    wh = &buffers[id][a];
    if (wh->dwFlags & WHDR_DONE) {
      sams = (*env)->GetShortArrayElements(env, sa, NULL);
      memcpy((void*)sams, (void*)wh->lpData, bufsiz[id]);
      (*env)->ReleaseShortArrayElements(env, sa, sams, 0);
      //add buffer back in
      waveInAddBuffer((HWAVEIN)waveIn[id], &buffers[id][a], sizeof(WAVEHDR));
      return 1;
    }
  }
  return 0;  //no buffers ready
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_WinAPI_snd_1record_1stop
  (JNIEnv *env, jclass cls, jint id)
{
  int ret = waveInStop(waveIn[id]);
  if (ret != MMSYSERR_NOERROR) return 0;
  return 1;
}
