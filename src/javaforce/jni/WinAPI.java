package javaforce.jni;

/**
 * Provides Native API for Windows
 *
 * Created : Oct 9, 2012
 *
 * @author pquiring
 *
 */

import java.io.*;

import javaforce.*;

public class WinAPI {

  /**
   * Contains init failure exception if init() fails.
   */
  public static Exception initException;

  public static boolean init() {
    String cpu = System.getProperty("sun.arch.data.model"); //NOTE : getenv("PROCESSOR_ARCHITECTURE") doesn't tell you which JVM you are running in
    if (cpu == null) cpu = "32";
    if (cpu.equals("32")) return init("jfwin32.dll");
    if (cpu.equals("64")) return init("jfwin64.dll");
    return false;
  }

  /**
   * Initialize Windows library. Must be called before any other JNI method.
   */
  private static boolean init(String dll) {
    //extract DLL to %USERPROFILE% and load it
    String path = System.getenv("USERPROFILE");
    if ((path == null) || (path.length() == 0)) {
      return false;
    }
    try {
      FileOutputStream fos = new FileOutputStream(path + "\\" + dll);
      InputStream is = WinAPI.class.getClassLoader().getResourceAsStream(dll);
      JF.copyAll(is, fos);
      fos.close();
      is.close();
    } catch (Exception f) {
      JFLog.log(f);
      //try to load anyways - could be a sharing violation
    }
    if (!new File(path + "\\" + dll).exists()) {
      JFLog.log(dll + " not found - Windows Native API not available");
      return false;
    }
    try {
      System.load(path + "\\" + dll);
    } catch (Exception e) {
      initException = e;
      return false;
    }
    return true;
  }

//arrays

  public native static short[] byteArray2shortArray(byte in[]);

  public native static byte[] shortArray2byteArray(short in[]);

  public native static int[] byteArray2intArray(byte in[]);

  public native static byte[] intArray2byteArray(int in[]);

//windows API
  public native static int get_window(String title);

  public native static void clrscr();

  public native static void gotoxy(int x, int y);

//sound API

  private static final int snd_count = 32;
  private static boolean snd_in_use[] = new boolean[snd_count];
  private static final Object snd_lock = new Object();

  public static int snd_alloc_id() {
    synchronized (snd_lock) {
      for (int a = 0; a < snd_count; a++) {
        if (snd_in_use[a] == false) {
          snd_in_use[a] = true;
          return a;
        }
      }
    }
    return -1;
  }

  public static synchronized void snd_free_id(int id) {
    if ((id < 0) || (id >= snd_count)) {
      return;
    }
    synchronized (snd_lock) {
      snd_in_use[id] = false;
      snd_clear_id(id);
    }
  }

  private static native void snd_clear_id(int id);

  public static native boolean snd_play(int id, int chs, int rate, int bits, int bufsiz);

  public static native boolean snd_play_write(int id, short samples[]);

  public static native boolean snd_play_stop(int id);

  public static native int snd_play_buffers_full(int id);  //returns # of buffers in queue to be played

  public static native boolean snd_record(int id, int chs, int rate, int bits, int bufsiz);

  public static native boolean snd_record_read(int id, short samples[]);

  public static native boolean snd_record_stop(int id);

//vfw (not working yet)

  public static native boolean vfw_open(int hwndParent, int xpos, int ypos, int winxsize, int winysize, int camxsize, int camysize);

  public static native boolean vfw_close();

  public static native int[] vfw_get_info();  //return:[0]=x [1]=y

  public static native int[] vfw_get_frame();

  public static native void vfw_process();  //must keep calling

//videoInput

  public static native int vi_num_devices();

  public static native boolean vi_open(int id, int x, int y);

  public static native boolean vi_close(int id);

  public static native int[] vi_get_info(int id);  //x,y,size

  public static native int[] vi_get_frame(int id);

}
