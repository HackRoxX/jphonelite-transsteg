/*
 * Settings.java
 *
 * Created on Mar 22, 2010, 6:03 PM
 *
 * @author pquiring
 *
 */

import java.io.*;
import java.util.*;
import javax.swing.*;

import javaforce.*;
import javaforce.voip.*;

/** Keeps current settings and provides methods to load/save settings to an XML file. */

public class Settings {
  public static Settings current;
  public static class Line {
    public int same;  //0-5 (-1=disabled) (ignored for lines[0])
    public String user, auth, pass, host;
    public boolean disableVideo;
    public Line() {
      same = 0;
      user = new String();
      auth = new String();
      pass = new String();
      host = new String();
      disableVideo = false;
    }
  }
  public int WindowXPos = 0;
  public int WindowYPos = 0;
  public Line lines[];
  public String sipcontacts[] = new String[0];  //NOTE : This member has changed from 'contacts' for the new format : "display" <sip:number@server;flgs1>;flgs2
  public String callLog[] = new String[0];
  public String dndCodeOn, dndCodeOff;
  public boolean swVolForce = false;
  public boolean resample441k = false;
  public boolean checkVersion = true;
  public String audioInput = "<default>", audioOutput = "<default>";
  public boolean disableLogging = false;
  public boolean hideWhenMinimized = true;
  public boolean alwaysOnTop = false;
  public int interval = 50;  //VNC interval
  public int mode = 2;  //VNC mode (1=DX 2=GDI)
  public boolean keepAudioOpen = true;
  public String downloadPath = JF.getUserPath() + "/Downloads";
  public boolean smallerFont = false;  //some JVMs have different fonts
  public String videoDevice = "<default>";
  public String videoResolution = "<default>";
  public String videoPosition = "<default>";
  public int videoFPS = 5;
  public boolean usePublish = false;
  public String ringtone = JF.getUserPath() + "/ringtone.wav";
  public boolean speakerMode = false;
  public int speakerThreshold = 1000;  //0-32k
  public int speakerDelay = 250;  //ms
  public boolean disableEnhanced = false;
//  public boolean useJavaSound = false;  //use flash instead [Applet mode only]  [obsolete setting]
  public int soundType = SOUND_WINDOWS;
  public int videoType = VIDEO_NONE;
  public int videoWindowsAPI = VIDEO_WINDOWS_DIRECTSHOW;
//  public boolean videoFlipHorizontal = true;
  public boolean disableG729a;  //obsolete (see audioCodecs)
  public String audioCodecs = "18,0";
  public boolean reinvite = true;  //reinvite when returned multiple codecs
  public int sampleRate = 8000;  //8000, 32000, 44100
  public int interpolation = I_LINEAR;

  //static = do not save these settings
  public static boolean aa;
  public static boolean ac;
  public static boolean isApplet = false;
  public static boolean isLinux = false;
  public static boolean isWindows = false;
  public static boolean isJavaScript = false;

  //sound types
//  public final static int SOUND_NONE = 0;  //does not compute?
  public final static int SOUND_LINUX = 1;
  public final static int SOUND_JAVA = 2;
//  public final static int SOUND_FLASH = 3;
  public final static int SOUND_WINDOWS = 4;

  public final static int VIDEO_NONE = 0;
  public final static int VIDEO_LINUX = 1;
//  public final static int VIDEO_JAVA = 2;  //JMF - no longer supported
//  public final static int VIDEO_FLASH = 3;  //Flash - no longer supported
  public final static int VIDEO_WINDOWS = 4;

  public final static int VIDEO_WINDOWS_DIRECTSHOW = 0;  //via inputVideo library
  public final static int VIDEO_WINDOWS_VFW = 1;

  public final static int I_LINEAR = 0;  //linear interpolation (sounds bad)
  public final static int I_FILTER_CAP = 1;  //capacitor type filter ???

  public void init() {
    lines = new Line[6];
    for(int a=0;a<6;a++) lines[a] = new Line();
    dndCodeOn = "*78";  //asterisk feature code to activate DND
    dndCodeOff = "*79";  //asterisk feature code to deactivate DND
    audioCodecs = "18,0";  //g729a & g711u
    reinvite = true;
  }

  public static void loadSettings() {
    String fn = JF.getUserPath() + "/.jphone.xml";
    try {
      current = new Settings();
      XML xml = new XML();
      xml.read(new FileInputStream(fn));
      xml.writeClass(current);

      //force settings
      current.videoWindowsAPI = Settings.VIDEO_WINDOWS_DIRECTSHOW;  //disable VFW (not working good)
      current.sampleRate = 8000;  //other rates not working yet (experimental)
      current.interpolation = Settings.I_LINEAR;  //not working yet

      JFLog.log("loadSettings ok");
    } catch (Exception e) {
      //set defaults
      JFLog.log(e);
      current = new Settings();
      current.init();
    }
  }
  public static void saveSettings() {
    String fn = JF.getUserPath() + "/.jphone.xml";
    try {
      XML xml = new XML();
      xml.readClass("settings", current);
      xml.write(new FileOutputStream(fn));
    } catch (Exception e) {
      JF.showError("Error", "Save failed : " + e);
    }
  }
  private static int maxLog = 25;
  public static void addCallLog(String number) {
    int len = current.callLog.length;
    for(int a=0;a<len;a++) {
      if (current.callLog[a].equals(number)) {
        //move to top of list
        for(int b=a;b > 0;b--) current.callLog[b] = current.callLog[b-1];
        current.callLog[0] = number;
        saveSettings();
        return;
      }
    }
    if (len == maxLog) len = maxLog-1;
    String newcallLog[] = new String[len + 1];
    for(int a=0;a < len;a++) newcallLog[a+1] = current.callLog[a];
    newcallLog[0] = number;
    current.callLog = newcallLog;
    saveSettings();
  }
  public static void setContact(String name, String contact) {
    int len = current.sipcontacts.length;
    int idx;
    String fields[];
    for(int a=0;a<len;a++) {
      fields = SIP.split(current.sipcontacts[a]);
      if (fields[0].equals(name)) {
JFLog.log("setting contact : " + fields[0] + " to " + contact);
        current.sipcontacts[a] = contact;
        return;
      }
    }
JFLog.log("adding contact : " + contact);
    String newContacts[] = new String[len+1];
    for(int a=0;a<len;a++) {newContacts[a] = current.sipcontacts[a];}
    newContacts[len] = contact;
    current.sipcontacts = newContacts;
  }
  public static void delContact(String name) {
    int len = current.sipcontacts.length;
    if (len == 0) return;
    int idx;
    String fields[];
    for(int a=0;a<len;a++) {
      fields = SIP.split(current.sipcontacts[a]);
      if (fields[0].equalsIgnoreCase(name)) {
        int pos = 0;
        String newlist[] = new String[len-1];
        for(int b=0;b<len;b++) {
          if (b==a) continue;
          newlist[pos++] = current.sipcontacts[b];
        }
        current.sipcontacts = newlist;
        return;
      }
    }
  }
  /** Encodes a password with some simple steps. */
  public static String encodePassword(String password) {
    char ca[] = password.toCharArray();
    int sl = ca.length;
    if (sl == 0) return "";
    char tmp;
    for(int p=0;p<sl/2;p++) {
      tmp = ca[p];
      ca[p] = ca[sl-p-1];
      ca[sl-p-1] = tmp;
    }
    StringBuffer out = new StringBuffer();
    for(int p=0;p<sl;p++) {
      ca[p] ^= 0xaa;
      if (ca[p] < 0x10) out.append("0");
      out.append(Integer.toString(ca[p], 16));
    }
//System.out.println("e1=" + out.toString());
    Random r = new Random();
    char key = (char)(r.nextInt(0xef) + 0x10);
    char outkey = key;
    ca = out.toString().toCharArray();
    sl = ca.length;
    for(int p=0;p<sl;p++) {
      ca[p] ^= key;
      key ^= ca[p];
    }
    out = new StringBuffer();
    for(int a=0;a<4;a++) {
      out.append(Integer.toString(r.nextInt(0xef) + 0x10, 16));
    }
    out.append(Integer.toString(outkey, 16));
    for(int p=0;p<sl;p++) {
      if (ca[p] < 0x10) out.append("0");
      out.append(Integer.toString(ca[p], 16));
    }
    for(int a=0;a<4;a++) {
      out.append(Integer.toString(r.nextInt(0xef) + 0x10, 16));
    }
    return out.toString();
  }
  public static String encodePassword(char password[]) {
    return encodePassword(new String(password));
  }
  /** Decodes a password. */
  public static String decodePassword(String crypto) {
    int sl = crypto.length();
    if (sl < 10) return null;
    char key = (char)(int)Integer.valueOf(crypto.substring(8,10), 16);
    char newkey;
    crypto = crypto.substring(10, sl - 8);
    int cl = (sl - 18) / 2;
    char ca[] = new char[cl];
    for(int p=0;p<cl;p++) {
      ca[p] = (char)(int)Integer.valueOf(crypto.substring(p*2, p*2+2), 16);
      newkey = (char)(key ^ ca[p]);
      ca[p] ^= key;
      key = newkey;
    }
    crypto = new String(ca);
//System.out.println("d1=" + crypto);
    cl = crypto.length() / 2;
    ca = new char[cl];
    for(int p=0;p<cl;p++) {
      ca[p] = (char)(int)Integer.valueOf(crypto.substring(p*2, p*2+2), 16);
    }
    for(int p=0;p<cl;p++) {
      ca[p] ^= 0xaa;
    }
    char tmp;
    for(int p=0;p<cl/2;p++) {
      tmp = ca[p];
      ca[p] = ca[cl-p-1];
      ca[cl-p-1] = tmp;
    }
    return new String(ca);
  }
  public static void test() {
    String tst = "testPassword";
    String e = encodePassword(tst);
    System.out.println("e=" + e);
    String d = decodePassword(e);
    System.out.println("d=" + d);
  }
  public static String getPassword(String pass) {
    if (pass.startsWith("crypto(") && pass.endsWith(")")) {
      if (pass.charAt(8) != ',') return "";  //bad function
      if (pass.charAt(7) != '1') return "";  //unknown crypto type
      try {
        return decodePassword(pass.substring(9, pass.length() - 1));
      } catch (Exception e) {}
    } else {
      return pass;
    }
    return "";
  }
  public boolean hasCodec(Codec codec) {
    String codecs[] = current.audioCodecs.split(",");
    if (codecs == null) return false;
    for(int a=0;a<codecs.length;a++) {
      if (JF.atoi(codecs[a]) == codec.id) return true;
    }
    return false;
  }
  public int[] getCodecs() {
    String codecs[] = current.audioCodecs.split(",");
    if (codecs == null) return new int[0];
    int ret[] = new int[codecs.length];
    for(int a=0;a<codecs.length;a++) {
      ret[a] = JF.atoi(codecs[a]);
    }
    return ret;
  }
  private static final int CX = 320;
  private static final int CY = 240;
  public int[] getVideoResolution() {
    String res = videoResolution;
    if ((res == null) || (res.equals("<default>"))) return new int[] {CX,CY};
    int idx = res.indexOf("x");
    if (idx == -1) return new int[] {CX,CY};
    return new int[] {Integer.valueOf(res.substring(0, idx)), Integer.valueOf(res.substring(idx+1))};
  }
}
