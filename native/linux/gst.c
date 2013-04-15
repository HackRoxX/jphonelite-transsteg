#include <sys/types.h>
#include <X11/X.h>
#include <X11/Xlib.h>
#include <glib.h>
#include <gst/gst.h>
//#include <gst/interfaces/xoverlay.h>  //0.10 -> 1.0 : interfaces were removed
#include <gst/app/gstappsink.h>
#include <gst/app/gstappsrc.h>
#include <gst/video/video.h>
#include <gst/audio/audio.h>
#include <gst/pbutils/encoding-profile.h>
#include <stdbool.h>
#include <memory.h>
#include <stdio.h>
#include <unistd.h>

#include "javaforce_jni_LnxAPI.h"

/* Native Code for GStreamer
 *
 * For NetBeans editor:
 * Tools -> Options -> C/C++ Tab -> Code Assistance Tab -> C Compiler Tab -> Include Directories:
 *   /usr/include/gstreamer-0.10
 *   /usr/include/glib-2.0
 *   /usr/lib/jvm/java-6-openjdk-i386/include
 *   /usr/lib/i386-linux-gnu/glib-2.0/include
 *   /usr/include/libxml2
 */

// Created : Mar 24, 2012

#define instcnt 32

//doesn't work yet - might be faster though
//#define imageEncoder "pngenc"
//#define imageDecoder "pngdec"
//#define imageMimetype "image/png"

#define imageEncoder "jpegenc"
#define imageDecoder "jpegdec"
#define imageMimetype "image/jpeg"

struct insts {
  //instance vars
  GstElement *pipeline, *videosink, *appsink_audio, *appsink_video, *filesrc, *filesink
    , *decoder, *encoder, *appsrc_audio, *appsrc_video, *imgenc, *imgdec
    , *alsasrc, *alsasink, *audioresample, *audioconvert, *rtp, *v4l
    , *videoscale, *videorate;
  GstCaps *caps_audio, *caps_video;
  jmethodID method;
  jclass cls;
  jobject obj;
  int id, channels, bits, rate, width, height, framerate_n, framerate_d;
  JavaVM *vm;
};

struct insts inst[instcnt];

static GMainLoop *loop;

static gboolean bus_call(GstBus *bus, GstMessage *msg, void *user_data)
{
  struct insts *this = user_data;
//  printf("bus_call\n");
  if ((this->obj == NULL) || (this->cls == NULL)) return true;
  jstring tmp;
  JNIEnv *env = NULL;
  (*(this->vm))->AttachCurrentThread(this->vm, (void **) &env, NULL);
  switch (GST_MESSAGE_TYPE(msg)) {
    case GST_MESSAGE_EOS: {
//      printf("EOS\n");
      (*env)->CallNonvirtualVoidMethod(env, this->obj, this->cls, this->method, this->id, 1, NULL);
      break;
    }
    case GST_MESSAGE_ERROR: {
      GError *err;
//      printf("ERROR\n");
      gst_message_parse_error(msg, &err, NULL);
      tmp = (*env)->NewStringUTF(env, err->message);  //this will get freed by gc()
      (*env)->CallNonvirtualVoidMethod(env, this->obj, this->cls, this->method, this->id, 2, tmp);
      g_error_free(err);
      break;
    }
    default:
//      printf("UNKNOWN MSG:%x\n", GST_MESSAGE_TYPE(msg));
      break;
  }
  (*(this->vm))->DetachCurrentThread(this->vm);
  return true;
}

static void _gst_play(int id) {
  GstState state, pending;
  gst_element_get_state(GST_ELEMENT(inst[id].pipeline), &state, &pending, 0);
  if (state != GST_STATE_PLAYING) {
    gst_element_set_state(GST_ELEMENT(inst[id].pipeline), GST_STATE_PLAYING);
  }
}

static void _gst_wait_preroll(int id, int maxsec) {
  //wait for stream to finish pre-rolling
  int a, maxint = maxsec * 10;
  for(a=0;a<maxint;a++) {
    GstState state, pending;
    gst_element_get_state(GST_ELEMENT(inst[id].pipeline), &state, &pending, 0);
    if (state >= GST_STATE_PAUSED) break;
    usleep(100 * 1000);
  }
}

static void _gst_wait_null(int id, int maxsec) {
  //wait for stream to uninit
  int a, maxint = maxsec * 10;
  for(a=0;a<maxint;a++) {
    GstState state, pending;
    gst_element_get_state(GST_ELEMENT(inst[id].pipeline), &state, &pending, 0);
    if (state == GST_STATE_NULL) break;
    usleep(100 * 1000);
  }
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1init
  (JNIEnv *env , jclass cls)
{
  memset(&inst, 0, sizeof(struct insts) * instcnt);
  gst_init(NULL, NULL);
  loop = g_main_loop_new(NULL, FALSE);
  return TRUE;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1init_1id
  (JNIEnv *env, jclass cls, jint id, jclass cb_cls, jobject cb_obj)
{
  memset(&inst[id], 0, sizeof(struct insts));
  if (cb_cls != NULL) {
    inst[id].method = (*env)->GetMethodID(env, cb_cls, "event", "(IILjava/lang/String;)V");
  }
  (*env)->GetJavaVM(env, &(inst[id].vm));
  inst[id].obj = cb_obj;
  inst[id].id = id;
  inst[id].cls = cb_cls;
  return inst[id].method != NULL;
}

JNIEXPORT void JNICALL Java_javaforce_jni_LnxAPI_gst_1clear_1id
  (JNIEnv *env, jclass cls, jint id)
{
  if (inst[id].pipeline != NULL) {
    gst_element_set_state(GST_ELEMENT(inst[id].pipeline), GST_STATE_NULL);
    _gst_wait_null(id, 3);  //wait for gstreamer to stop (3 seconds max)
    gst_object_unref(inst[id].pipeline);
  }
  if (inst[id].caps_audio != NULL) gst_caps_unref(inst[id].caps_audio);
  if (inst[id].caps_video != NULL) gst_caps_unref(inst[id].caps_video);
  memset(&inst[id], 0, sizeof(struct insts));
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1play
  (JNIEnv *env , jclass cls, jint id, jstring _uri, jint window, jboolean start_playing)
{
  GstBus *bus;

  inst[id].pipeline = gst_element_factory_make("playbin", "my-player");
  if (inst[id].pipeline == NULL) return FALSE;

  if (window != 0) {
    inst[id].videosink = gst_element_factory_make ("xvimagesink", "my-videosink");
  }

  const char *uri = (*env)->GetStringUTFChars(env,_uri,NULL);
  g_object_set(G_OBJECT(inst[id].pipeline), "uri", uri, NULL);
  (*env)->ReleaseStringUTFChars(env, _uri, uri);

  bus = gst_pipeline_get_bus(GST_PIPELINE(inst[id].pipeline));
  gst_bus_add_watch(bus, bus_call, &inst[id]);
  gst_object_unref(bus);

  if (window != 0) {
    g_object_set (G_OBJECT (inst[id].videosink), "force-aspect-ratio", TRUE, NULL);

//this stuff was removed ? 0.10 only?
//    if (GST_IS_X_OVERLAY(inst[id].videosink))
//    {
//      printf("gst_x_overlay_set_xwindow_id:%u\n", window);
//      gst_x_overlay_set_xwindow_id ((GstXOverlay*)inst[id].videosink, window);
//    }
  }

  gst_element_set_state(GST_ELEMENT(inst[id].pipeline), (start_playing ? GST_STATE_PLAYING : GST_STATE_PAUSED));

  //wait for stream to finish pre-rolling (10 seconds max)
  _gst_wait_preroll(id, 10);

  return TRUE;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1stop
  (JNIEnv *env, jclass cls, jint id)
{
  if (inst[id].appsrc_audio != NULL) {
    gst_app_src_end_of_stream((GstAppSrc*)inst[id].appsrc_audio);
  }
  if (inst[id].appsrc_video != NULL) {
    gst_app_src_end_of_stream((GstAppSrc*)inst[id].appsrc_video);
  }
  gst_element_set_state(GST_ELEMENT(inst[id].pipeline), GST_STATE_NULL);
}

JNIEXPORT jint JNICALL Java_javaforce_jni_LnxAPI_gst_1get_1state
  (JNIEnv *env, jclass cls, jint id)
{
  GstState state, pending;
  gst_element_get_state(GST_ELEMENT(inst[id].pipeline), &state, &pending, 0);
  return state;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1main_1loop_1run
  (JNIEnv *env, jclass cls)
{
//  printf("loop=%x\n", loop);
  g_main_loop_run(loop);
//  printf("loop done");
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1main_1loop_1quit
  (JNIEnv *env, jclass cls)
{
  g_main_loop_quit(loop);
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1pause
  (JNIEnv *env, jclass cls, jint id)
{
  gst_element_set_state(GST_ELEMENT(inst[id].pipeline), GST_STATE_PAUSED);
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1resume
  (JNIEnv *env, jclass cls, jint id)
{
  gst_element_set_state(GST_ELEMENT(inst[id].pipeline), GST_STATE_PLAYING);
}

static GstSeekFlags seek_flags = GST_SEEK_FLAG_FLUSH | GST_SEEK_FLAG_KEY_UNIT;

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1reset
  (JNIEnv *env, jclass cls, jint id)
{
  gst_element_seek (inst[id].pipeline, 1.0,
    GST_FORMAT_TIME,
    seek_flags,
    GST_SEEK_TYPE_SET, 0,
    GST_SEEK_TYPE_NONE, GST_CLOCK_TIME_NONE);
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1seek
  (JNIEnv *env, jclass cls, jint id, jlong pos)
{
  gst_element_seek (inst[id].pipeline, 1.0,
    GST_FORMAT_TIME,
    seek_flags,
    GST_SEEK_TYPE_SET, pos,
    GST_SEEK_TYPE_NONE, GST_CLOCK_TIME_NONE);
}

JNIEXPORT jlong JNICALL Java_javaforce_jni_LnxAPI_gst_1get_1pos
  (JNIEnv *env, jclass cls, jint id)
{
  GstFormat format = GST_FORMAT_TIME;
  gint64 cur;
  gboolean result;

  result = gst_element_query_position (inst[id].pipeline, format, &cur);
  if (!result || format != GST_FORMAT_TIME) return GST_CLOCK_TIME_NONE;

  return cur;
}

JNIEXPORT jlong JNICALL Java_javaforce_jni_LnxAPI_gst_1get_1length
  (JNIEnv *env, jclass cls, jint id)
{
  GstFormat format = GST_FORMAT_TIME;
  gint64 cur;
  gboolean result;

  result = gst_element_query_duration (inst[id].pipeline, format, &cur);
  if (!result || format != GST_FORMAT_TIME) return GST_CLOCK_TIME_NONE;

  return cur;
}

static void
cb_newpad_decoder (GstElement *bin,
           GstPad     *pad,
           gpointer    data)
{
  GstCaps *caps;
  GstStructure *str;
  GstPad *audiopad, *videopad;
  struct insts *inst = (struct insts*)data;

  /* check media type */
  caps = gst_pad_query_caps(pad, NULL);
  str = gst_caps_get_structure(caps, 0);
  if (g_strrstr(gst_structure_get_name(str), "audio")) {
    /* only link once */
    printf("new pad:audio\n");
    if (inst->audioconvert == NULL) {
      inst->audioconvert = gst_element_factory_make("audioconvert", "my-audioconvert");
      inst->appsink_audio = gst_element_factory_make("appsink", "my-appsink-audio");
    }
    audiopad = gst_element_get_static_pad(inst->audioconvert, "sink");
    if (GST_PAD_IS_LINKED (audiopad)) {
      printf("pad already linked");
      gst_caps_unref(caps);
      g_object_unref(audiopad);
      return;
    }
    gst_caps_unref(caps);
    gst_bin_add_many(GST_BIN(inst->pipeline), inst->appsink_audio, inst->audioconvert, NULL);
    gst_pad_link(pad, audiopad);
    g_object_unref(audiopad);
    caps = gst_caps_new_simple("audio/x-raw"  /* -int */
      , "signed", G_TYPE_BOOLEAN, TRUE
      , "endianness", G_TYPE_INT, 1234
      , NULL);
    gst_element_link_filtered(inst->audioconvert, inst->appsink_audio, caps);
    gst_caps_unref(caps);
    gst_element_sync_state_with_parent(GST_ELEMENT(inst->audioconvert));
    gst_element_sync_state_with_parent(GST_ELEMENT(inst->appsink_audio));
    return;
  }
  if (g_strrstr(gst_structure_get_name(str), "video")) {
    /* only link once */
    printf("new pad:video\n");
    if (inst->imgenc == NULL) {
      inst->imgenc = gst_element_factory_make(imageEncoder , "my-imgenc");
      inst->appsink_video = gst_element_factory_make("appsink", "my-appsink-video");
    }
    videopad = gst_element_get_static_pad(inst->imgenc, "sink");
    if (GST_PAD_IS_LINKED (videopad)) {
      printf("pad already linked");
      gst_caps_unref(caps);
      g_object_unref(videopad);
      return;
    }
    gst_caps_unref(caps);
    gst_bin_add_many(GST_BIN(inst->pipeline), inst->imgenc, inst->appsink_video, NULL);
    gst_pad_link(pad, videopad);
    g_object_unref(videopad);
    gst_element_link(inst->imgenc, inst->appsink_video);
    gst_element_sync_state_with_parent(GST_ELEMENT(inst->imgenc));
    gst_element_sync_state_with_parent(GST_ELEMENT(inst->appsink_video));
    return;
  }
  printf("unknown pad type:%s\n", gst_structure_get_name(str));
  gst_caps_unref(caps);
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1read_1file
  (JNIEnv *env, jclass cls, jint id, jstring _file)
{
  GstBus *bus;

  inst[id].pipeline = gst_pipeline_new("my-pipeline");
  inst[id].filesrc = gst_element_factory_make("filesrc", "my-filesrc");
  inst[id].decoder = gst_element_factory_make("decodebin", "my-decoder");

  g_signal_connect(inst[id].decoder, "pad-added", G_CALLBACK(cb_newpad_decoder), &inst[id]);

  gst_bin_add_many(GST_BIN(inst[id].pipeline), inst[id].filesrc, inst[id].decoder, NULL);
  gst_element_link(inst[id].filesrc, inst[id].decoder);

  const char *file = (*env)->GetStringUTFChars(env,_file,NULL);
  if (file) g_object_set(G_OBJECT(inst[id].filesrc), "location", file, NULL);
  (*env)->ReleaseStringUTFChars(env, _file, file);

  bus = gst_pipeline_get_bus(GST_PIPELINE(inst[id].pipeline));
  gst_bus_add_watch(bus, bus_call, &inst[id]);
  gst_object_unref(bus);

  gst_element_set_state(GST_ELEMENT(inst[id].pipeline), GST_STATE_PLAYING);

  //wait for stream to finish pre-rolling (3 seconds max)
  _gst_wait_preroll(id, 3);

  return TRUE;
}

static void
cb_newpad_decoder_reformat (GstElement *bin,
           GstPad     *pad,
           gpointer    data)
{
  GstCaps *caps;
  GstStructure *str;
  GstPad *audiopad, *videopad;
  struct insts *inst = (struct insts*)data;

  /* check media type */
  caps = gst_pad_query_caps(pad, NULL);
  str = gst_caps_get_structure(caps, 0);
  if (g_strrstr(gst_structure_get_name(str), "audio")) {
    /* only link once */
    printf("new pad:audio\n");
    if (inst->audioconvert == NULL) {
      inst->audioconvert = gst_element_factory_make("audioconvert", "my-audioconvert");
      inst->audioresample = gst_element_factory_make("audioresample", "my-audioresample");
      inst->appsink_audio = gst_element_factory_make("appsink", "my-appsink-audio");
    }
    audiopad = gst_element_get_static_pad(inst->audioconvert, "sink");
    if (GST_PAD_IS_LINKED (audiopad)) {
      printf("pad already linked");
      gst_caps_unref(caps);
      g_object_unref(audiopad);
      return;
    }
    gst_caps_unref(caps);
    gst_bin_add_many(GST_BIN(inst->pipeline), inst->audioconvert, inst->audioresample, inst->appsink_audio, NULL);
    gst_pad_link(pad, audiopad);
    g_object_unref(audiopad);
    caps = gst_caps_new_simple("audio/x-raw" /* -int */
      , "signed", G_TYPE_BOOLEAN, TRUE
      , "endianness", G_TYPE_INT, 1234
      , "channels", G_TYPE_INT, inst->channels
      , "depth", G_TYPE_INT, inst->bits
      , "width", G_TYPE_INT, inst->bits
      , NULL);
    gst_element_link_filtered(inst->audioconvert, inst->audioresample, caps);
    gst_caps_unref(caps);

    caps = gst_caps_new_simple("audio/x-raw" /* -int */
      , "signed", G_TYPE_BOOLEAN, TRUE
      , "endianness", G_TYPE_INT, 1234
      , "rate", G_TYPE_INT, inst->rate
      , NULL);
    gst_element_link_filtered(inst->audioresample, inst->appsink_audio, caps);
    gst_caps_unref(caps);

    gst_element_sync_state_with_parent(GST_ELEMENT(inst->audioconvert));
    gst_element_sync_state_with_parent(GST_ELEMENT(inst->audioresample));
    gst_element_sync_state_with_parent(GST_ELEMENT(inst->appsink_audio));
    return;
  }
  if (g_strrstr(gst_structure_get_name(str), "video")) {
    /* only link once */
    printf("new pad:video\n");
    if (inst->videoscale == NULL) {
      inst->videoscale = gst_element_factory_make("videoscale", "my-videoscale");
      inst->videorate = gst_element_factory_make("videorate", "my-videorate");
      inst->imgenc = gst_element_factory_make(imageEncoder , "my-imgenc");
      inst->appsink_video = gst_element_factory_make("appsink", "my-appsink-video");
    }
    videopad = gst_element_get_static_pad(inst->videoscale, "sink");
    if (GST_PAD_IS_LINKED (videopad)) {
      printf("pad already linked");
      gst_caps_unref(caps);
      g_object_unref(videopad);
      return;
    }
    gst_caps_unref(caps);

    gst_bin_add_many(GST_BIN(inst->pipeline), inst->videoscale, inst->videorate, inst->imgenc, inst->appsink_video, NULL);
    gst_pad_link(pad, videopad);
    g_object_unref(videopad);

    caps = gst_caps_new_simple("video/x-raw"  /* -yuv */
      , "width", G_TYPE_INT, inst->width
      , "height", G_TYPE_INT, inst->height
      , NULL);
    gst_element_link_filtered(inst->videoscale, inst->videorate, caps);
    gst_caps_unref(caps);

    caps = gst_caps_new_simple("video/x-raw"  /* -yuv */
      , "framerate", GST_TYPE_FRACTION, inst->framerate_n, inst->framerate_d
      , NULL);
    gst_element_link_filtered(inst->videorate, inst->imgenc, caps);
    gst_caps_unref(caps);

    gst_element_link(inst->imgenc, inst->appsink_video);
    gst_element_sync_state_with_parent(GST_ELEMENT(inst->videoscale));
    gst_element_sync_state_with_parent(GST_ELEMENT(inst->videorate));
    gst_element_sync_state_with_parent(GST_ELEMENT(inst->imgenc));
    gst_element_sync_state_with_parent(GST_ELEMENT(inst->appsink_video));
    return;
  }
  printf("unknown pad type:%s\n", gst_structure_get_name(str));
  gst_caps_unref(caps);
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1read_1file_1reformat
  (JNIEnv *env, jclass cls, jint id, jstring _file
    , int width, int height, int framerate_n, int framerate_d
    , int channels, int bits, int rate)
{
  GstBus *bus;

  inst[id].pipeline = gst_pipeline_new("my-pipeline");
  inst[id].filesrc = gst_element_factory_make("filesrc", "my-filesrc");
  inst[id].decoder = gst_element_factory_make("decodebin", "my-decoder");

  inst[id].width = width;
  inst[id].height = height;
  inst[id].framerate_n = framerate_n;
  inst[id].framerate_d = framerate_d;

  inst[id].channels = channels;
  inst[id].bits = bits;
  inst[id].rate = rate;

  g_signal_connect(inst[id].decoder, "pad-added", G_CALLBACK(cb_newpad_decoder_reformat), &inst[id]);

  gst_bin_add_many(GST_BIN(inst[id].pipeline), inst[id].filesrc, inst[id].decoder, NULL);

  gst_element_link(inst[id].filesrc, inst[id].decoder);

  const char *file = (*env)->GetStringUTFChars(env,_file,NULL);
  if (file) g_object_set(G_OBJECT(inst[id].filesrc), "location", file, NULL);
  (*env)->ReleaseStringUTFChars(env, _file, file);

  bus = gst_pipeline_get_bus(GST_PIPELINE(inst[id].pipeline));
  gst_bus_add_watch(bus, bus_call, &inst[id]);
  gst_object_unref(bus);

  gst_element_set_state(GST_ELEMENT(inst[id].pipeline), GST_STATE_PLAYING);

  //wait for stream to finish pre-rolling (3 seconds max)
  _gst_wait_preroll(id, 3);

  return TRUE;
}

JNIEXPORT jshortArray JNICALL Java_javaforce_jni_LnxAPI_gst_1read_1audio_1shortArray
  (JNIEnv *env, jclass cls, jint id)
{
//  _gst_play(id);
  GstSample *sam = (GstSample *)gst_app_sink_pull_sample((GstAppSink*)inst[id].appsink_audio);
  GstBuffer *buf = gst_sample_get_buffer(sam);
  if (buf == NULL) return NULL;
  GstMapInfo info;
  gst_buffer_map(buf, &info, GST_MAP_READ);
  gsize bufsize = info.size;
  void *bufdata = (void*)info.data;
  if (inst[id].caps_audio == NULL) {
    inst[id].caps_audio = gst_app_sink_get_caps((GstAppSink*)inst[id].appsink_audio);
//    char *caps = gst_caps_to_string(inst[id].caps_audio);  //test
//    printf("audio.caps=%s\n", caps);
  }
  jshortArray sa = (*env)->NewShortArray(env, bufsize/2);
  (*env)->SetShortArrayRegion(env, sa, 0, bufsize/2, bufdata);
  gst_buffer_unmap(buf, &info);
//  gst_buffer_unref(buf);
  gst_sample_unref(sam);  //unref buffer too??
  return sa;
}

JNIEXPORT jbyteArray JNICALL Java_javaforce_jni_LnxAPI_gst_1read_1audio_1byteArray
  (JNIEnv *env, jclass cls, jint id)
{
//  _gst_play(id);
  GstSample *sam = (GstSample *)gst_app_sink_pull_sample((GstAppSink*)inst[id].appsink_audio);
  GstBuffer *buf = gst_sample_get_buffer(sam);
  if (buf == NULL) return NULL;
  GstMapInfo info;
  gst_buffer_map(buf, &info, GST_MAP_READ);
  gsize bufsize = info.size;
  void *bufdata = (void*)info.data;
  if (inst[id].caps_audio == NULL) {
    inst[id].caps_audio = gst_app_sink_get_caps((GstAppSink*)inst[id].appsink_audio);
//    char *caps = gst_caps_to_string(inst[id].caps_audio);  //test
//    printf("audio.caps=%s\n", caps);
  }
  jbyteArray ba = (*env)->NewByteArray(env, bufsize);
  (*env)->SetByteArrayRegion(env, ba, 0, bufsize, bufdata);
  gst_buffer_unmap(buf, &info);
//  gst_buffer_unref(buf);
  gst_sample_unref(sam);  //unref buffer too??
  return ba;
}

JNIEXPORT jbyteArray JNICALL Java_javaforce_jni_LnxAPI_gst_1read_1video
  (JNIEnv *env, jclass cls, jint id)
{
//  _gst_play(id);
  GstSample *sam = (GstSample *)gst_app_sink_pull_sample((GstAppSink*)inst[id].appsink_video);
  GstBuffer *buf = gst_sample_get_buffer(sam);
  if (buf == NULL) return NULL;
  if (inst[id].caps_video == NULL) {
    inst[id].caps_video = gst_app_sink_get_caps((GstAppSink*)inst[id].appsink_video);
  }
  GstMapInfo info;
  gst_buffer_map(buf, &info, GST_MAP_READ);
  gsize bufsize = info.size;
  void *bufdata = (void*)info.data;
  jbyteArray ba = (*env)->NewByteArray(env, bufsize);
  (*env)->SetByteArrayRegion(env, ba, 0, bufsize, bufdata);
  gst_buffer_unmap(buf, &info);
//  gst_buffer_unref(buf);
  gst_sample_unref(sam);  //unref buffer too??
  return ba;
}

//returns chs/freq/bits1/bits2
JNIEXPORT jintArray JNICALL Java_javaforce_jni_LnxAPI_gst_1read_1audio_1info
  (JNIEnv *env, jclass cls, jint id)
{
  if (inst[id].caps_audio == NULL) return NULL;
//  char *caps = gst_caps_to_string(inst[id].caps_audio);
//  printf("acaps=%s\n", caps);
  int chs,freq,bits1,bits2;
  GstAudioInfo info;
  memset(&info, 0, sizeof(info));
  if (!gst_audio_info_from_caps(&info, inst[id].caps_audio)) {
    return NULL;
  }
  chs = GST_AUDIO_INFO_CHANNELS(&info);
  freq = GST_AUDIO_INFO_RATE(&info);
  bits1 = GST_AUDIO_INFO_DEPTH(&info);
  bits2 = GST_AUDIO_INFO_WIDTH(&info);

  int metadata[4];
  metadata[0] = chs;
  metadata[1] = freq;
  metadata[2] = bits1;
  metadata[3] = bits2;
  jintArray ia = (*env)->NewIntArray(env, 4);
  (*env)->SetIntArrayRegion(env, ia, 0, 4, metadata);
  return ia;
}

//returns x/y/fps_n/fps_d/fourcc
JNIEXPORT jintArray JNICALL Java_javaforce_jni_LnxAPI_gst_1read_1video_1info
  (JNIEnv *env, jclass cls, jint id)
{
  if (inst[id].caps_video == NULL) return NULL;
  int x,y,fps_n,fps_d,fourcc;
  GstVideoInfo info;
  gst_video_info_from_caps(&info, inst[id].caps_video);  //need to free this??? 1.0 ???
//  fourcc = gst_video_format_to_fourcc(format);
  int metadata[5];
  metadata[0] = info.width;
  metadata[1] = info.height;
  metadata[2] = info.fps_n;
  metadata[3] = info.fps_d;
  metadata[4] = 0;  //fourcc;
  jintArray ia = (*env)->NewIntArray(env, 5);
  (*env)->SetIntArrayRegion(env, ia, 0, 5, metadata);
  return ia;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1write_1file
  (JNIEnv *env, jclass cls, jint id, jstring _file, jstring _container, jstring _acodec, jstring _vcodec)
{
  GstBus *bus;

  inst[id].pipeline = gst_pipeline_new("my-pipeline");
  inst[id].filesink = gst_element_factory_make("filesink", "my-filesink");
  inst[id].encoder = gst_element_factory_make("encodebin", "my-encoder");
  inst[id].appsrc_audio = gst_element_factory_make("appsrc", "my-appsrc-audio");
  inst[id].appsrc_video = gst_element_factory_make("appsrc", "my-appsrc-video");
  inst[id].imgdec = gst_element_factory_make(imageDecoder, "my-imgdec");

  gst_bin_add_many(GST_BIN(inst[id].pipeline), inst[id].filesink, inst[id].encoder
    , inst[id].appsrc_audio, inst[id].appsrc_video, inst[id].imgdec, NULL);

  //create EncoderProfiles and assign to encoder
  GstEncodingContainerProfile *cprof = NULL;
  GstEncodingAudioProfile *aprof = NULL;
  GstEncodingVideoProfile *vprof = NULL;
  GstCaps *caps;

  if (_container != NULL) {
    const char *container = (*env)->GetStringUTFChars(env,_container,NULL);
    caps = gst_caps_from_string(container);
    cprof = gst_encoding_container_profile_new(NULL, NULL, caps, NULL);
    gst_caps_unref(caps);
    (*env)->ReleaseStringUTFChars(env, _container, container);
  }

  if (_acodec != NULL) {
    const char *acodec = (*env)->GetStringUTFChars(env,_acodec,NULL);
    caps = gst_caps_from_string(acodec);
    aprof = (GstEncodingAudioProfile*) gst_encoding_audio_profile_new(caps, NULL, NULL, 0);
    if (cprof != NULL) gst_encoding_container_profile_add_profile(cprof, (GstEncodingProfile*)aprof);
    gst_caps_unref(caps);
    (*env)->ReleaseStringUTFChars(env, _acodec, acodec);
  }

  if (_vcodec != NULL) {
    const char *vcodec = (*env)->GetStringUTFChars(env,_vcodec,NULL);
    caps = gst_caps_from_string(vcodec);
    vprof = (GstEncodingVideoProfile*) gst_encoding_video_profile_new(caps, NULL, NULL, 0);
    if (cprof != NULL) gst_encoding_container_profile_add_profile(cprof, (GstEncodingProfile*)vprof);
    gst_caps_unref(caps);
    (*env)->ReleaseStringUTFChars(env, _vcodec, vcodec);
  }

  if (cprof != NULL) {
    g_object_set (G_OBJECT (inst[id].encoder), "profile", cprof, NULL);
  } else if (aprof != NULL) {
    g_object_set (G_OBJECT (inst[id].encoder), "profile", aprof, NULL);
  } else if (vprof != NULL) {
    g_object_set (G_OBJECT (inst[id].encoder), "profile", vprof, NULL);
  }

//  if (cprof != NULL) gst_encoding_profile_unref(cprof);
//  if (aprof != NULL) gst_encoding_profile_unref(aprof);
//  if (vprof != NULL) gst_encoding_profile_unref(vprof);

  gst_element_link(inst[id].encoder, inst[id].filesink);
  gst_element_link(inst[id].appsrc_video, inst[id].imgdec);

  //link src pads to encoder pads (NOTE:encoder doesn't expose pads until a profile is set)

  GstPad *audiopad_src = gst_element_get_static_pad(inst[id].appsrc_audio, "src");
  GstPad *audiopad_sink = gst_element_get_request_pad(inst[id].encoder, "audio_%u");
//  printf("pads=%x,%x\n", audiopad_src, audiopad_sink);
  gst_pad_link(audiopad_src, audiopad_sink);
  gst_object_unref(audiopad_src);
  gst_object_unref(audiopad_sink);

  GstPad *videopad_src = gst_element_get_static_pad(inst[id].imgdec, "src");
  GstPad *videopad_sink = gst_element_get_request_pad(inst[id].encoder, "video_%u");
//  printf("pads=%x,%x\n", videopad_src, videopad_sink);
  gst_pad_link(videopad_src, videopad_sink);
  gst_object_unref(videopad_src);
  gst_object_unref(videopad_sink);

  const char *file = (*env)->GetStringUTFChars(env,_file,NULL);
  if (file) g_object_set(G_OBJECT(inst[id].filesink), "location", file, NULL);
  (*env)->ReleaseStringUTFChars(env, _file, file);

  bus = gst_pipeline_get_bus(GST_PIPELINE(inst[id].pipeline));
  gst_bus_add_watch(bus, bus_call, &inst[id]);
  gst_object_unref(bus);

  gst_element_set_state(GST_ELEMENT(inst[id].pipeline), GST_STATE_PLAYING);

  return TRUE;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1write_1audio__I_3SIII
  (JNIEnv *env, jclass cls, jint id, jshortArray sa, int channels, int rate, int bits)
{
  int size = (*env)->GetArrayLength(env, sa) * 2;
  void *data = (*env)->GetShortArrayElements(env, sa, NULL);
  void *bufdata = g_malloc(size);
  memcpy(bufdata, data, size);
  GstBuffer *buf = gst_buffer_new_wrapped(bufdata, size);  //gst will g_free()
  GstCaps *caps = gst_caps_new_simple("audio/x-raw"  /* -int */
    , "rate", G_TYPE_INT, rate
    , "channels", G_TYPE_INT, channels
    , "signed", G_TYPE_BOOLEAN, TRUE
    , "endianness", G_TYPE_INT, 1234
    , "width", G_TYPE_INT, bits
    , "depth", G_TYPE_INT, bits
    , NULL);
  gst_app_src_set_caps((GstAppSrc*)inst[id].appsrc_audio, caps);
  _gst_play(id);
  gst_app_src_push_buffer((GstAppSrc*)inst[id].appsrc_audio, buf); //appsrc becomes owner of buffer
  (*env)->ReleaseShortArrayElements(env, sa, data, JNI_ABORT);
  gst_caps_unref(caps);
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1write_1audio__I_3BIII
  (JNIEnv *env, jclass cls, jint id, jbyteArray ba, int channels, int rate, int bits)
{
  int size = (*env)->GetArrayLength(env, ba);
  void *data = (*env)->GetByteArrayElements(env, ba, NULL);
  void *bufdata = g_malloc(size);
  memcpy(bufdata, data, size);
  GstBuffer *buf = gst_buffer_new_wrapped(bufdata, size);  //gst will g_free()
  GstCaps *caps = gst_caps_new_simple("audio/x-raw"  /* -int */
    , "rate", G_TYPE_INT, rate
    , "channels", G_TYPE_INT, channels
    , "signed", G_TYPE_BOOLEAN, TRUE
    , "endianness", G_TYPE_INT, 1234
    , "width", G_TYPE_INT, bits
    , "depth", G_TYPE_INT, bits
    , NULL);
  gst_app_src_set_caps((GstAppSrc*)inst[id].appsrc_audio, caps);
  _gst_play(id);
  gst_app_src_push_buffer((GstAppSrc*)inst[id].appsrc_audio, buf); //appsrc becomes owner of buffer
  (*env)->ReleaseByteArrayElements(env, ba, data, JNI_ABORT);
  gst_caps_unref(caps);
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1write_1video
  (JNIEnv *env, jclass cls, jint id, jbyteArray ba, jint width, jint height, jint framerate_n, jint framerate_d)
{
  int size = (*env)->GetArrayLength(env, ba);
  void *data = (*env)->GetByteArrayElements(env, ba, NULL);
  void *bufdata = g_malloc(size);
  memcpy(bufdata, data, size);
  GstBuffer *buf = gst_buffer_new_wrapped(bufdata, size);  //gst will g_free()
  GstCaps *caps = gst_caps_new_simple(imageMimetype
    , "width", G_TYPE_INT, width
    , "height", G_TYPE_INT, height
    , "framerate", GST_TYPE_FRACTION, framerate_n, framerate_d
    , "pixel-aspect-ratio", GST_TYPE_FRACTION, 1, 1
    , NULL);
//  char *gcaps = gst_caps_to_string(caps);
//  printf("gen.vcaps=%s\n", gcaps);
  gst_app_src_set_caps((GstAppSrc*)inst[id].appsrc_video, caps);
  gst_app_src_push_buffer((GstAppSrc*)inst[id].appsrc_video, buf);  //appsrc becomes owner of buffer
  (*env)->ReleaseByteArrayElements(env, ba, data, JNI_ABORT);
  gst_caps_unref(caps);
}

static void
cb_newpad_transcoder (GstElement *bin,
           GstPad     *pad,
           gpointer    data)
{
  GstCaps *caps;
  GstStructure *str;
  GstPad *audiopad, *videopad;
  struct insts *inst = (struct insts*)data;

  /* check media type */
  caps = gst_pad_query_caps(pad, NULL);
  str = gst_caps_get_structure(caps, 0);
  if (g_strrstr(gst_structure_get_name(str), "audio")) {
    /* only link once */
    audiopad = gst_element_get_request_pad(inst->encoder, "audio_%u");
    if (GST_PAD_IS_LINKED (audiopad)) {
      printf("pad already linked");
      g_object_unref(audiopad);
      return;
    }
    gst_caps_unref(caps);
    gst_pad_link(pad, audiopad);
    return;
  }
  if (g_strrstr(gst_structure_get_name(str), "video")) {
    /* only link once */
    videopad = gst_element_get_request_pad(inst->encoder, "video_%u");
    if (GST_PAD_IS_LINKED (videopad)) {
      printf("pad already linked");
      g_object_unref(videopad);
      return;
    }
    gst_caps_unref(caps);
    gst_pad_link(pad, videopad);
    return;
  }
  printf("unknown pad type:%s\n", gst_structure_get_name(str));
  gst_caps_unref(caps);
}

/*
static void do_test() {
  GstCaps *caps = gst_caps_new_simple("video/mpeg"
    , "systemstream", G_TYPE_BOOLEAN, TRUE
    , NULL);
  char *str = gst_caps_to_string(caps);
  printf("video/mpeg.caps=%s\n", str);
}*/

//gst transcoder
JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1transcoder
  (JNIEnv *env, jclass cls, jint id, jstring _filein, jstring _fileout, jstring _container, jstring _acodec, jstring _vcodec, jboolean start_coding)
{
  GstBus *bus;

//  do_test();

  inst[id].pipeline = gst_pipeline_new("my-pipeline");
  inst[id].filesrc = gst_element_factory_make("filesrc", "my-filesrc");
  inst[id].filesink = gst_element_factory_make("filesink", "my-filesink");
  inst[id].decoder = gst_element_factory_make("decodebin", "my-decoder");
  inst[id].encoder = gst_element_factory_make("encodebin", "my-encoder");

  g_signal_connect(inst[id].decoder, "pad-added", G_CALLBACK(cb_newpad_transcoder), &inst[id]);

  gst_bin_add_many(GST_BIN(inst[id].pipeline), inst[id].filesrc, inst[id].decoder
    , inst[id].filesink, inst[id].encoder
    , NULL);

  //create EncoderProfiles and assign to encoder
  GstEncodingContainerProfile *cprof = NULL;
  GstEncodingAudioProfile *aprof = NULL;
  GstEncodingVideoProfile *vprof = NULL;
  GstCaps *caps;

  if (_container != NULL) {
    const char *container = (*env)->GetStringUTFChars(env,_container,NULL);
    caps = gst_caps_from_string(container);
    cprof = gst_encoding_container_profile_new(NULL, NULL, caps, NULL);
    gst_caps_unref(caps);
    (*env)->ReleaseStringUTFChars(env, _container, container);
  }

  if (_acodec != NULL) {
    const char *acodec = (*env)->GetStringUTFChars(env,_acodec,NULL);
    caps = gst_caps_from_string(acodec);
    aprof = (GstEncodingAudioProfile*) gst_encoding_audio_profile_new(caps, NULL, NULL, 0);
    if (cprof != NULL) gst_encoding_container_profile_add_profile(cprof, (GstEncodingProfile*)aprof);
    gst_caps_unref(caps);
    (*env)->ReleaseStringUTFChars(env, _acodec, acodec);
  }

  if (_vcodec != NULL) {
    const char *vcodec = (*env)->GetStringUTFChars(env,_vcodec,NULL);
    caps = gst_caps_from_string(vcodec);
    vprof = (GstEncodingVideoProfile*) gst_encoding_video_profile_new(caps, NULL, NULL, 0);
    if (cprof != NULL) gst_encoding_container_profile_add_profile(cprof, (GstEncodingProfile*)vprof);
    gst_caps_unref(caps);
    (*env)->ReleaseStringUTFChars(env, _vcodec, vcodec);
  }

  if (cprof != NULL) {
    g_object_set (G_OBJECT (inst[id].encoder), "profile", cprof, NULL);
  } else if (aprof != NULL) {
    g_object_set (G_OBJECT (inst[id].encoder), "profile", aprof, NULL);
  } else if (vprof != NULL) {
    g_object_set (G_OBJECT (inst[id].encoder), "profile", vprof, NULL);
  }

//  if (cprof != NULL) gst_encoding_profile_unref(cprof);
//  if (aprof != NULL) gst_encoding_profile_unref(aprof);
//  if (vprof != NULL) gst_encoding_profile_unref(vprof);

  gst_element_link(inst[id].filesrc, inst[id].decoder);
  gst_element_link(inst[id].encoder, inst[id].filesink);

  const char *filein = (*env)->GetStringUTFChars(env,_filein,NULL);
  if (filein) g_object_set(G_OBJECT(inst[id].filesrc), "location", filein, NULL);
  (*env)->ReleaseStringUTFChars(env, _filein, filein);

  const char *fileout = (*env)->GetStringUTFChars(env,_fileout,NULL);
  if (fileout) g_object_set(G_OBJECT(inst[id].filesink), "location", fileout, NULL);
  (*env)->ReleaseStringUTFChars(env, _fileout, fileout);

  bus = gst_pipeline_get_bus(GST_PIPELINE(inst[id].pipeline));
  gst_bus_add_watch(bus, bus_call, &inst[id]);
  gst_object_unref(bus);

  if (start_coding) {
    gst_element_set_state(GST_ELEMENT(inst[id].pipeline), GST_STATE_PLAYING);
    //wait for stream to finish pre-rolling (3 seconds max)
    _gst_wait_preroll(id, 3);
  }

  return TRUE;
}

//sound API (GStreamer)

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_snd_1play
  (JNIEnv *env, jclass cls, jint id, jstring _device)
{
  GstBus *bus;

  inst[id].pipeline = gst_pipeline_new("my-pipeline");
  inst[id].appsrc_audio = gst_element_factory_make("appsrc", "my-appsrc");
  inst[id].alsasink = gst_element_factory_make("alsasink", "my-alsasink");

  gst_bin_add_many(GST_BIN(inst[id].pipeline), inst[id].appsrc_audio, inst[id].alsasink, NULL);

  gst_element_link(inst[id].appsrc_audio, inst[id].alsasink);

  bus = gst_pipeline_get_bus(GST_PIPELINE(inst[id].pipeline));
  gst_bus_add_watch(bus, bus_call, &inst[id]);
  gst_object_unref(bus);

  return TRUE;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_snd_1play_1write__I_3SIII
  (JNIEnv *env, jclass cls, jint id, jshortArray sa, int channels, int rate, int bits)
{
  int size = (*env)->GetArrayLength(env, sa) * 2;
  void *data = (*env)->GetShortArrayElements(env, sa, NULL);
  void *bufdata = g_malloc(size);
  memcpy(bufdata, data, size);
  GstBuffer *buf = gst_buffer_new_wrapped(bufdata, size);  //gst will g_free()
  (*env)->ReleaseShortArrayElements(env, sa, data, JNI_ABORT);
  GstCaps *caps = gst_caps_new_simple("audio/x-raw"  /* -int */
    , "rate", G_TYPE_INT, rate
    , "channels", G_TYPE_INT, channels
    , "signed", G_TYPE_BOOLEAN, TRUE
    , "endianness", G_TYPE_INT, 1234
    , "width", G_TYPE_INT, bits
    , "depth", G_TYPE_INT, bits
    , NULL);
  gst_app_src_set_caps((GstAppSrc*)inst[id].appsrc_audio, caps);
  gst_app_src_push_buffer((GstAppSrc*)inst[id].appsrc_audio, buf); //appsrc becomes owner of buffer
  gst_caps_unref(caps);
//start if not playing
  _gst_play(id);
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_snd_1play_1write__I_3BIII
  (JNIEnv *env, jclass cls, jint id, jbyteArray ba, int channels, int rate, int bits)
{
  int size = (*env)->GetArrayLength(env, ba);
  void *data = (*env)->GetByteArrayElements(env, ba, NULL);
  void *bufdata = g_malloc(size);
  memcpy(bufdata, data, size);
  GstBuffer *buf = gst_buffer_new_wrapped(bufdata, size);  //gst will g_free()
  (*env)->ReleaseByteArrayElements(env, ba, data, JNI_ABORT);
  GstCaps *caps = gst_caps_new_simple("audio/x-raw"  /* -int */
    , "rate", G_TYPE_INT, rate
    , "channels", G_TYPE_INT, channels
    , "signed", G_TYPE_BOOLEAN, TRUE
    , "endianness", G_TYPE_INT, 1234
    , "width", G_TYPE_INT, bits
    , "depth", G_TYPE_INT, bits
    , NULL);
  gst_app_src_set_caps((GstAppSrc*)inst[id].appsrc_audio, caps);
  gst_app_src_push_buffer((GstAppSrc*)inst[id].appsrc_audio, buf); //appsrc becomes owner of buffer
  gst_caps_unref(caps);
//start if not playing
  _gst_play(id);
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_snd_1record
  (JNIEnv *env, jclass cls, jint id, jint chs, jint rate, jint bits, jstring _device)
{
  GstBus *bus;

  inst[id].pipeline = gst_pipeline_new("my-pipeline");
  inst[id].alsasrc = gst_element_factory_make("alsasrc", "my-alsasrc");
  inst[id].audioresample = gst_element_factory_make("audioresample", "my-resampler");
  inst[id].appsink_audio = gst_element_factory_make("appsink", "my-appsink");

  gst_bin_add_many(GST_BIN(inst[id].pipeline), inst[id].appsink_audio
    , inst[id].alsasrc, inst[id].audioresample, NULL);

  GstCaps *caps = gst_caps_new_simple("audio/x-raw"  /* -int */
    , "rate", G_TYPE_INT, rate
    , "channels", G_TYPE_INT, chs
    , "signed", G_TYPE_BOOLEAN, TRUE
    , "endianness", G_TYPE_INT, 1234
    , "width", G_TYPE_INT, bits
    , "depth", G_TYPE_INT, bits
    , NULL);

  gst_element_link(inst[id].alsasrc, inst[id].audioresample);
  gst_element_link_filtered(inst[id].audioresample, inst[id].appsink_audio, caps);

  gst_caps_unref(caps);

  bus = gst_pipeline_get_bus(GST_PIPELINE(inst[id].pipeline));
  gst_bus_add_watch(bus, bus_call, &inst[id]);
  gst_object_unref(bus);

  gst_element_set_state(GST_ELEMENT(inst[id].pipeline), GST_STATE_PLAYING);

  return TRUE;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1rtp_1video_1encoder
  (JNIEnv *env, jclass cls, jint id)
{
  GstBus *bus;

  inst[id].pipeline = gst_pipeline_new("my-pipeline");
  inst[id].appsrc_video = gst_element_factory_make("appsrc", "my-appsrc");
  inst[id].rtp = gst_element_factory_make("rtpjpegpay", "my-rtppay");
  if (inst[id].rtp == NULL) return FALSE;
  inst[id].appsink_video = gst_element_factory_make("appsink", "my-appsink");

  gst_bin_add_many(GST_BIN(inst[id].pipeline), inst[id].appsrc_video
    , inst[id].rtp, inst[id].appsink_video, NULL);

  gst_element_link(inst[id].appsrc_video, inst[id].rtp);
  gst_element_link(inst[id].rtp, inst[id].appsink_video);

  bus = gst_pipeline_get_bus(GST_PIPELINE(inst[id].pipeline));
  gst_bus_add_watch(bus, bus_call, &inst[id]);
  gst_object_unref(bus);

  gst_element_set_state(GST_ELEMENT(inst[id].pipeline), GST_STATE_PLAYING);

  return TRUE;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1rtp_1video_1encoder_1write
  (JNIEnv *env, jclass cls, jint id, jbyteArray ba, jint x, jint y)
{
  int size = (*env)->GetArrayLength(env, ba);
  void *data = (*env)->GetByteArrayElements(env, ba, NULL);
  void *bufdata = g_malloc(size);
  memcpy(bufdata, data, size);
  GstBuffer *buf = gst_buffer_new_wrapped(bufdata, size);  //gst will g_free()
  GstCaps *caps = gst_caps_new_simple("image/jpeg"
    , "width", G_TYPE_INT, x
    , "height", G_TYPE_INT, y
    , NULL);
  gst_app_src_set_caps((GstAppSrc*)inst[id].appsrc_video, caps);
  _gst_play(id);
  gst_app_src_push_buffer((GstAppSrc*)inst[id].appsrc_video, buf); //appsrc becomes owner of buffer
  (*env)->ReleaseByteArrayElements(env, ba, data, JNI_ABORT);
  gst_caps_unref(caps);
}

JNIEXPORT jbyteArray JNICALL Java_javaforce_jni_LnxAPI_gst_1rtp_1video_1encoder_1read
  (JNIEnv *env, jclass cls, jint id)
{
  GstSample *sam = (GstSample *)gst_app_sink_pull_sample((GstAppSink*)inst[id].appsink_video);
  GstBuffer *buf = gst_sample_get_buffer(sam);
  if (buf == NULL) return NULL;
  if (inst[id].caps_video == NULL) {
    inst[id].caps_video = gst_app_sink_get_caps((GstAppSink*)inst[id].appsink_video);
  }
//    char *caps = gst_caps_to_string(inst[id].caps_video);  //test
//    printf("video.caps=%s\n", caps);
  /*
application/x-rtp, media=(string)video, clock-rate=(int)90000, encoding-name=(string)JPEG, payload=(int)96, ssrc=(uint)344233721
6, clock-base=(uint)96091344, seqnum-base=(uint)48410
   */
  GstMapInfo info;
  gst_buffer_map(buf, &info, GST_MAP_READ);
  gsize bufsize = info.size;
  void *bufdata = (void*)info.data;
  jbyteArray ba = (*env)->NewByteArray(env, bufsize);
  (*env)->SetByteArrayRegion(env, ba, 0, bufsize, bufdata);
  gst_buffer_unmap(buf, &info);
//  gst_buffer_unref(buf);
  gst_sample_unref(sam);  //unref buffer too??
  return ba;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1rtp_1video_1decoder
  (JNIEnv *env, jclass cls, jint id)
{
  GstBus *bus;

  inst[id].pipeline = gst_pipeline_new("my-pipeline");
  inst[id].appsrc_video = gst_element_factory_make("appsrc", "my-appsrc-test");
  inst[id].rtp = gst_element_factory_make("rtpjpegdepay", "my-rtpdepay-test");
  if (inst[id].rtp == NULL) return FALSE;
  inst[id].appsink_video = gst_element_factory_make("appsink", "my-appsink-test");

  gst_bin_add_many(GST_BIN(inst[id].pipeline), inst[id].appsrc_video
    , inst[id].rtp, inst[id].appsink_video, NULL);

  gst_element_link(inst[id].appsrc_video, inst[id].rtp);
  gst_element_link(inst[id].rtp, inst[id].appsink_video);

  bus = gst_pipeline_get_bus(GST_PIPELINE(inst[id].pipeline));
  gst_bus_add_watch(bus, bus_call, &inst[id]);
  gst_object_unref(bus);

  gst_element_set_state(GST_ELEMENT(inst[id].pipeline), GST_STATE_PLAYING);

  return TRUE;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1rtp_1video_1decoder_1write
  (JNIEnv *env, jclass cls, jint id, jbyteArray ba)
{
  int size = (*env)->GetArrayLength(env, ba);
  void *data = (*env)->GetByteArrayElements(env, ba, NULL);
  void *bufdata = g_malloc(size);
  memcpy(bufdata, data, size);
  GstBuffer *buf = gst_buffer_new_wrapped(bufdata, size);  //gst will g_free()
  GstCaps *caps = gst_caps_new_simple("application/x-rtp"
    , "media", G_TYPE_STRING, "video"
    , "encoding-name", G_TYPE_STRING, "JPEG"
    , NULL);
  gst_app_src_set_caps((GstAppSrc*)inst[id].appsrc_video, caps);
  _gst_play(id);
  gst_app_src_push_buffer((GstAppSrc*)inst[id].appsrc_video, buf); //appsrc becomes owner of buffer
  (*env)->ReleaseByteArrayElements(env, ba, data, JNI_ABORT);
  gst_caps_unref(caps);
}

JNIEXPORT jbyteArray JNICALL Java_javaforce_jni_LnxAPI_gst_1rtp_1video_1decoder_1read
  (JNIEnv *env, jclass cls, jint id)
{
  GstSample *sam = (GstSample *)gst_app_sink_pull_sample((GstAppSink*)inst[id].appsink_video);
  GstBuffer *buf = gst_sample_get_buffer(sam);
  if (buf == NULL) return NULL;
  if (inst[id].caps_video == NULL) {
    inst[id].caps_video = gst_app_sink_get_caps((GstAppSink*)inst[id].appsink_video);
  }
  GstMapInfo info;
  gst_buffer_map(buf, &info, GST_MAP_READ);
  gsize bufsize = info.size;
  void *bufdata = (void*)info.data;
  jbyteArray ba = (*env)->NewByteArray(env, bufsize);
  (*env)->SetByteArrayRegion(env, ba, 0, bufsize, bufdata);
  gst_buffer_unmap(buf, &info);
//  gst_buffer_unref(buf);
  gst_sample_unref(sam);  //unref buffer too??
  return ba;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_gst_1video_1capture
  (JNIEnv *env, jclass cls, jint id, jint x, jint y, jstring _device)
{
  GstBus *bus;

  inst[id].pipeline = gst_pipeline_new("my-pipeline");
  inst[id].v4l = gst_element_factory_make("v4l2src", "my-videocap");
  if (inst[id].v4l == NULL) return FALSE;
  inst[id].videoscale = gst_element_factory_make("videoscale", "my-videoscale");
  if (inst[id].videoscale == NULL) return FALSE;
  inst[id].imgenc = gst_element_factory_make(imageEncoder, "my-imgenc");
  inst[id].appsink_video = gst_element_factory_make("appsink", "my-appsink");

  gst_bin_add_many(GST_BIN(inst[id].pipeline), inst[id].videoscale
    , inst[id].v4l, inst[id].imgenc, inst[id].appsink_video, NULL);

  GstCaps *caps = gst_caps_new_simple("video/x-raw"  /* -yuv */
    , "width", G_TYPE_INT, x
    , "height", G_TYPE_INT, y
    , NULL);

  gst_element_link(inst[id].v4l, inst[id].videoscale);
  gst_element_link_filtered(inst[id].videoscale, inst[id].imgenc, caps);
  gst_element_link(inst[id].imgenc, inst[id].appsink_video);

  bus = gst_pipeline_get_bus(GST_PIPELINE(inst[id].pipeline));
  gst_bus_add_watch(bus, bus_call, &inst[id]);
  gst_object_unref(bus);

  gst_element_set_state(GST_ELEMENT(inst[id].pipeline), GST_STATE_PLAYING);

  return TRUE;
}

JNIEXPORT jbyteArray JNICALL Java_javaforce_jni_LnxAPI_gst_1video_1capture_1read
  (JNIEnv *env, jclass cls, jint id)
{
  GstSample *sam = (GstSample *)gst_app_sink_pull_sample((GstAppSink*)inst[id].appsink_video);
  GstBuffer *buf = gst_sample_get_buffer(sam);
  if (buf == NULL) return NULL;
  if (inst[id].caps_video == NULL) {
    inst[id].caps_video = gst_app_sink_get_caps((GstAppSink*)inst[id].appsink_video);
  }
  GstMapInfo info;
  gst_buffer_map(buf, &info, GST_MAP_READ);
  gsize bufsize = info.size;
  void *bufdata = (void*)info.data;
  jbyteArray ba = (*env)->NewByteArray(env, bufsize);
  (*env)->SetByteArrayRegion(env, ba, 0, bufsize, bufdata);
  gst_buffer_unmap(buf, &info);
//  gst_buffer_unref(buf);
  gst_sample_unref(sam);  //unref buffer too??
  return ba;
}

