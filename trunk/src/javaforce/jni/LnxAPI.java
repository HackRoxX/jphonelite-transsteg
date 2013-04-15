package javaforce.jni;

/**
 * Provides access to native libraries for Linux.
 */
public class LnxAPI {
//General

  /**
   * Contains init failure exception if init() fails.
   */
  public static Exception initException;

  /**
   * Initialize JNI library. Must be called before any other JNI method.
   */
  public static boolean init() {
    if (!new java.io.File("/usr/lib/libjfjni.so").exists()) {
      return false;
    }
    try {
      System.loadLibrary("jfjni");
      return true;
    } catch (Exception e) {
      initException = e;
      return false;
    }
  }
//arrays

  public native static short[] byteArray2shortArray(byte in[]);

  public native static byte[] shortArray2byteArray(short in[]);

  public native static int[] byteArray2intArray(byte in[]);

  public native static byte[] intArray2byteArray(int in[]);
//Linux Kernel

  /**
   * Authenticates user/pass again PAM
   */
  public native static boolean authUser(String user, String pass);  //authenticate against PAM service
//X11

  /**
   * Returns X11 id of window with title specified.
   */
  public native static int x11_get_id(String winTitle);  //returns 0 on failure

  public native static boolean x11_set_desktop(int id);  //sets window to _NET_WM_WINDOW_TYPE_DESKTOP

  public native static boolean x11_set_dock(int id);  //sets window to _NET_WM_WINDOW_TYPE_DOCK

  public native static boolean x11_set_strut(int id, int panelHeight, int x, int y, int width, int height);  //sets _NET_WM_STRUT_PARTIAL for id

  public native static boolean x11_set_workarea(int id, int x, int y, int width, int height);  //sets _NET_WORKAREA for main display

  public native static int x11_keysym_to_keycode(char sym);  //returns keycode for use with x11_send_event

  public native static boolean x11_send_event(int keycode, boolean down);  //send keycode to active window
  public static final int X11_SHIFT = 0x100;

  public native static boolean x11_toggle_numlock();

  public native static boolean x11_tray_main(Class cb_class, TrayListener cb_object, int pid, int screen_width);  //blocks until stop() is called

  public native static boolean x11_tray_stop();  //stops tray_main()
//Gstreamer
  private static final int gst_count = 32;
  private static boolean gst_in_use[] = new boolean[gst_count];
  private static final Object gst_lock = new Object();

  /**
   * Allocates a pipeline for Gstreamer use - returns -1 if none available (32
   * max)
   */
  public static int gst_alloc_id() {
    synchronized (gst_lock) {
      for (int a = 0; a < gst_count; a++) {
        if (gst_in_use[a] == false) {
          gst_in_use[a] = true;
          return a;
        }
      }
    }
    return -1;
  }

  public static synchronized void gst_free_id(int id) {
    if ((id < 0) || (id >= gst_count)) {
      return;
    }
    synchronized (gst_lock) {
      gst_in_use[id] = false;
      gst_clear_id(id);
    }
  }

  private native static void gst_clear_id(int id);

  /**
   * Must call once to init GStreamer
   */
  public native static boolean gst_init();  //call once per process

  /**
   * Assigns a callback to pipeline
   */
  public native static boolean gst_init_id(int id, Class cb_class, GSTListener cb_object);

  /**
   * Starts playing a file:<br>
   *
   * @param id : id allocated from gst_alloc_id()<br>
   * @param uri : url (file:///path/to/file or http://server/path/to/file)<br>
   * @param window : X11 id (optional) (0=no window - one will be created if
   * needed)<br>
   * @param start_playing : start playing right away<br>
   */
  public native static boolean gst_play(int id, String uri, int window, boolean start_playing);

  public native static boolean gst_stop(int id);

  public native static boolean gst_pause(int id);

  public native static boolean gst_resume(int id);

  /**
   * Resets position to start.
   */
  public native static boolean gst_reset(int id);

  /**
   * Seeks absolute in seconds.
   */
  public native static boolean gst_seek(int id, long pos);

  /**
   * Returns current position in nano-seconds.
   */
  public native static long gst_get_pos(int id);

  /**
   * Returns length of media in nano-seconds.
   */
  public native static long gst_get_length(int id);

  /**
   * Returns state of pipeline.
   */
  public native static int gst_get_state(int id);

  /**
   * Must call once per process - blocks until gst_main_loop_quit() is called.
   * Usually called from it's own thread.
   */
  public native static boolean gst_main_loop_run();  //call once per process : blocks until gst_main_loop_quit() is called

  /**
   * Causes gst_main_loop_run() to stop and return.
   */
  public native static boolean gst_main_loop_quit();  //forces gst_main_loop_run() to return
//these are for reading from media files (decoder)

  /**
   * Open a file and decodes into JPEG/raw-int-audio format. You must call
   * gst_get_state() until it returns GST_STATE_PLAYING or PAUSED before
   * starting.
   */
  public native static boolean gst_read_file(int id, String file);

  /**
   * Open a file and change video/audio format while reading.
   */
  public native static boolean gst_read_file_reformat(int id, String file, int width, int height, int framerate_n, int framerate_d, int channels, int bits, int rate);

  /**
   * Reads one audio packet in raw-int format
   */
  public native static short[] gst_read_audio_shortArray(int id);  //raw-int format
  public native static byte[] gst_read_audio_byteArray(int id);  //raw-int format

  /**
   * Reads one video frame in JPEG format
   */
  public native static byte[] gst_read_video(int id);  //JPEG Format

  /**
   * Returns video details. Must be called AFTER first call to gst_read_video()
   */
  public native static int[] gst_read_video_info(int id);  //return x/y/fps of video

  /**
   * Returns audio details. Must be called AFTER first call to gst_read_audio()
   */
  public native static int[] gst_read_audio_info(int id);  //return chs/bits/freq of audio

  /**
   * Creates a new media file in the specified container/codecs.
   */
  public native static boolean gst_write_file(int id, String file, String container, String acodec, String vcodec);

  /**
   * Writes one packet of audio in raw-int audio format
   */
  public native static boolean gst_write_audio(int id, byte buf[], int channels, int freq, int bits);  //raw-int format
  public native static boolean gst_write_audio(int id, short buf[], int channels, int freq, int bits);  //raw-int format

  /**
   * Writes one video frame in JPEG format
   */
  public native static boolean gst_write_video(int id, byte buf[], int width, int height, int framerate_n, int framerate_d);  //JPEG format

  /**
   * Transcodes (converts) filein to fileout with container/codecs specified.
   */
  public native static boolean gst_transcoder(int id, String filein, String fileout, String container, String acodec, String vcodec, boolean start_coding);
//todo : add transcoder filters (videoscale, audioresample, etc.)
//mix two files into one
//  public native static boolean gst_mixer(int id, String filein1, String filein2, String fileout, String container, String acodec, String vcodec);
  /**
   * a few codecs<br> NOTE:You can add params:<br> ie: GST_CODEC_VIDEO_THEORA +
   * ",width=(int)320,height=(int)240,framerate=(fraction)30000/1001"<br> ie:
   * GST_CODEC_AUDIO_VORBIS +
   * ",width=(int)16,depth=(int)16,channels=(int)2,rate=(int)44100,signed=(boolean)true,endianness=(int)1234"<br>
   * You can use any codec that GStreamer supports.<br>
   */
  public static final String GST_CONTAINER_OGG = "application/ogg";
  public static final String GST_CODEC_AUDIO_VORBIS = "audio/x-vorbis";
  public static final String GST_CODEC_VIDEO_THEORA = "video/x-theora";
  /**
   * GSTListener.event() msg : End of stream
   */
  public static final int GST_EOS = 1;
  /**
   * GSTListener.event() msg : Error
   */
  public static final int GST_ERROR = 2;
  public static final int GST_STATE_VOID_PENDING = 0;
  public static final int GST_STATE_NULL = 1;
  public static final int GST_STATE_READY = 2;
  public static final int GST_STATE_PAUSED = 3;
  public static final int GST_STATE_PLAYING = 4;

//sound API (via GStreamer for now - may use ALSA directly in the future)
  public static int snd_alloc_id() {
    return gst_alloc_id();
  }

  public static void snd_free_id(int id) {
    gst_free_id(id);
  }

  public static boolean snd_init_id(int id, Class cb_class, GSTListener cb_obj) {
    return gst_init_id(id, cb_class, cb_obj);
  }

  public native static boolean snd_play(int id, String device);

  public native static boolean snd_play_write(int id, short samples[], int chs, int rate, int bits);
  public native static boolean snd_play_write(int id, byte samples[], int chs, int rate, int bits);
//  public native static int snd_play_status(int id);

  public native static boolean snd_record(int id, int rate, int chs, int bits, String device);

  public static short[] snd_record_read_shortArray(int id) {
    return gst_read_audio_shortArray(id);
  }

  public static byte[] snd_record_read_byteArray(int id) {
    return gst_read_audio_byteArray(id);
  }

  public static void snd_stop(int id) {
    gst_stop(id);
  }
//RTP (H.264) stuff

  public native static boolean gst_rtp_video_encoder(int id);

  public native static boolean gst_rtp_video_encoder_write(int id, byte image[], int x, int y);

  public native static byte[] gst_rtp_video_encoder_read(int id);

  public native static boolean gst_rtp_video_decoder(int id);

  public native static boolean gst_rtp_video_decoder_write(int id, byte rtp[]);

  public native static byte[] gst_rtp_video_decoder_read(int id);
//Video capture

  /**
   * Captures from camera. Image will be scaled to x/y. device should be null.
   */
  public native static boolean gst_video_capture(int id, int x, int y, String device);

  public native static byte[] gst_video_capture_read(int id);

  public static int[] gst_video_capture_info(int id) {
    return gst_read_video_info(id);
  }  //return x/y/fps of video
//jpeg encoder (OpenJDK is missing one)

  public native static byte[] jpeg_encoder(int px[], int x, int y, int compressLevel);  //level = 1-100 (recommend 90)

  public native static int[] jpeg_decoder(byte jpeg[], int xy[]);  //xy will receive : xy[0] = x, xy[1] = y;

//inotify stuff
  public native static int inotify_init();  //returns fd
  public native static void inotify_uninit(int fd);  //calls close()
  public native static int inotify_add_watch(int fd, String path);  //return wd
  public native static void inotify_rm_watch(int fd, int wd);
  public native static String[] inotify_read(int fd);  //blocks so call from a dedicated thread
}
