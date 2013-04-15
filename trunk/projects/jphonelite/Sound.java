import javax.sound.sampled.*;
import java.io.*;
import java.util.*;

import javaforce.*;
import javaforce.jni.*;
import javaforce.voip.*;

/** Handles all aspects of sound processing (recording, playback, ringing sounds, conference mixing, etc.) */

public class Sound implements GSTListener {
  //sound data
  private SourceDataLine sdl;
  private TargetDataLine tdl;
  private FloatControl sdlvol, tdlvol;
  private AudioFormat af;
  private short silence[] = new short[160];
  private short mixed[] = new short[160];
  private short recording[] = new short[160];
  private short indata[] = new short[160];
  private short outdata[] = new short[160];
  private short dataDown32k[] = new short[640];
  private short dataDown44_1k[] = new short[882];
  private short ringing[] = new short[160];
  private short callWaiting[] = new short[160];
  private byte outjava[];
  private byte injava[];
  private Timer timer;
  private Player player;
  private PhoneLine lines[];
  private int line = -1;
  private boolean inRinging = false, outRinging = false;
  private MeterController mc;
  private boolean swVolPlay, swVolRec;
  private int volPlay = 100, volRec = 100;
  private boolean mute = false;
  private DTMF dtmf = new DTMF();
  private int writeCnt = 0;
  private boolean playing = false;
  private Wav wav;
  private int speakerDelay = 0;
  private SamplesBuffer linuxBuffer;
  private SamplesBuffer windowsBuffer;
  private int timestamp = 0;  //sound timestamp
  private static boolean useOnPlaySamples = false;  //bad idea : hard to buffer (skips a lot) and very high latency
  private LinuxReader linuxReader;
  private Timer windowsReader;
  private short windowsSamples[];
  private int profileIndex;
  private int sampleRate, sampleRate50, sampleRate50x2;

  /** Init sound system.  Sound needs access to the lines and the MeterController to send audio levels back to the panel. */

  public boolean init(PhoneLine lines[], MeterController mc) {
    this.lines = lines;
    this.mc = mc;
    sampleRate = Settings.current.sampleRate;
    sampleRate50 = sampleRate / 50;
    sampleRate50x2 = sampleRate50 * 2;
    if (Settings.current.soundType == Settings.SOUND_LINUX) {
      if (!Settings.isLinux) {
        JFLog.log("=== Linux Native API NOT Available, Switching to Java Sound ===");
        Settings.current.soundType = Settings.SOUND_JAVA;  //Configured to use Linux but not available - use Java Sound
      }
    }
    if (Settings.current.soundType == Settings.SOUND_WINDOWS) {
      if (!Settings.isWindows) {
        JFLog.log("=== Windows Native API NOT Available, Switching to Java Sound ===");
        Settings.current.soundType = Settings.SOUND_JAVA;  //Configured to use Windows but not available - use Java Sound
      }
    }
    wav = new Wav();
    wav.load(Settings.current.ringtone);
    switch (Settings.current.soundType) {
      case Settings.SOUND_LINUX:
        if (!init_linux()) return false;
        break;
      case Settings.SOUND_JAVA:
        if (!init_java()) return false;
        break;
      case Settings.SOUND_WINDOWS:
        if (!init_windows()) return false;
        break;
    }
    if (Settings.current.keepAudioOpen) {
      for(int a=0;a<2;a++) write(silence);  //prime output
    }
    player = new Player();
    player.start();
    timer = new Timer();
    timer.scheduleAtFixedRate(new TimerTask() {
      public void run() {
        process();
      }
    }, 0, 20);
    return true;
  }

  int snd_id_play = -1, snd_id_record = -1;

  private boolean init_linux() {
    JFLog.log("=== init_linux() ===");
    snd_id_play = LnxAPI.snd_alloc_id();
    LnxAPI.snd_init_id(snd_id_play, this.getClass(), this);
    snd_id_record = LnxAPI.snd_alloc_id();
    LnxAPI.snd_init_id(snd_id_record, this.getClass(), this);
    LnxAPI.snd_play(snd_id_play, null);  //TODO : null can be ALSA device name
    if (!Settings.current.keepAudioOpen) LnxAPI.gst_pause(snd_id_play);
    LnxAPI.snd_record(snd_id_record, 1, sampleRate, 16, null);  //TODO : null can be ALSA device name
    linuxBuffer = new SamplesBuffer(sampleRate);
    swVolPlay = true;
    swVolRec = true;
    linuxReader = new LinuxReader();
    linuxReader.start();
    return true;
  }

  private boolean init_java() {
    JFLog.log("=== init_java() ===");
    int idx;
    outjava = new byte[sampleRate50x2];
    injava = new byte[sampleRate50x2];
    try {
      //format = float sampleRate, int sampleSizeInBits, int channels, boolean signed, boolean bigEndian
      af = new AudioFormat((float)sampleRate, 16, 1, true, true);
      Mixer.Info mi[] = AudioSystem.getMixerInfo();
      idx = -1;
      for(int a=0;a<mi.length;a++) {
        if (mi[a].getName().equalsIgnoreCase(Settings.current.audioOutput)) {
          idx = a;
          break;
        }
      }
      if ( (Settings.current.audioOutput.equalsIgnoreCase("<default>")) || (idx == -1) ) {
        sdl = AudioSystem.getSourceDataLine(af);
      } else {
        sdl = AudioSystem.getSourceDataLine(af, mi[idx]);
      }
      if (sdl == null) throw new Exception("unable to get playback device");
      idx = -1;
      for(int a=0;a<mi.length;a++) {
        if (mi[a].getName().equalsIgnoreCase(Settings.current.audioInput)) {
          idx = a;
          break;
        }
      }
      if ( (Settings.current.audioInput.equalsIgnoreCase("<default>")) || (idx == -1) ) {
        tdl = AudioSystem.getTargetDataLine(af);
      } else {
        tdl = AudioSystem.getTargetDataLine(af, mi[idx]);
      }
      if (tdl == null) throw new Exception("unable to get recording device");
      sdl.open(af);
      if (Settings.current.keepAudioOpen) sdl.start();
      tdl.open(af);
      tdl.start();
    } catch (Exception e) {
      JFLog.log("err:sound init: " + e);
      return false;
    }
    swVolPlay = false;
    try {
      if (!Settings.current.swVolForce) {
        sdlvol = (FloatControl) sdl.getControl(FloatControl.Type.VOLUME);
        if (sdlvol == null)
          throw new Exception("unable to get playback volume control");
      } else {
        swVolPlay = true;
      }
    } catch (Exception e1) {
      try {
        sdlvol = (FloatControl) sdl.getControl(FloatControl.Type.MASTER_GAIN);
        if (sdlvol == null) throw new Exception("unable to get playback volume control");
      } catch (Exception w1) {
        JFLog.log("warning:sound:unable to use hardware playing volume:using software mixing");
        swVolPlay = true;
      }
    }
    swVolRec = false;
    try {
      if (!Settings.current.swVolForce) {
        tdlvol = (FloatControl) tdl.getControl(FloatControl.Type.VOLUME);
        if (tdlvol == null)
          throw new Exception("unable to get recording volume control");
      } else {
        swVolRec = true;
      }
    } catch (Exception e2) {
      try {
        tdlvol = (FloatControl) tdl.getControl(FloatControl.Type.MASTER_GAIN);
        if (tdlvol == null) throw new Exception("unable to get recording volume control");
      } catch (Exception w2) {
        JFLog.log("warning:sound:unable to use hardware recording volume:using software mixing");
        swVolRec = true;
      }
    }
/*    //test
      Control cs[] = tdl.getControls();
      JFLog.log("# controls = " + cs.length);
      for (int a = 0; a < cs.length; a++) {
        JFLog.log("control[" + a + "]=" + cs[a].getType());
      }
*/
    JFLog.log("note:java sound:init ok\r\nplayFormat=" + sdl.getFormat() + "\r\nrecFormat=" + tdl.getFormat());
    return true;
  }

  private boolean init_windows() {
    JFLog.log("=== init_windows() ===");
    snd_id_play = WinAPI.snd_alloc_id();
    snd_id_record = WinAPI.snd_alloc_id();
    JFLog.log("play=" + snd_id_play + ",rec=" + snd_id_record +",sampleRate=" + sampleRate);
    if (!WinAPI.snd_play(snd_id_play, 1, sampleRate, 16, sampleRate50x2)) {
      JFLog.log("err:windows play failed");
      return false;
    }
    if (!WinAPI.snd_record(snd_id_record, 1, sampleRate, 16, sampleRate50x2)) {
      JFLog.log("err:windows record failed");
      return false;
    }
    windowsBuffer = new SamplesBuffer(sampleRate);
    swVolPlay = true;
    swVolRec = true;
    switch (sampleRate) {
      case 8000: windowsSamples = new short[160]; break;
      case 32000: windowsSamples = new short[640]; break;
      case 44100: windowsSamples = new short[882]; break;
    }
    windowsReader = new Timer();
    windowsReader.scheduleAtFixedRate(new TimerTask() {
      public void run() {
        windowsReaderProcess();
      }
    }, 0, 20);
    return true;
  }

  /** Frees resources. */

  public void uninit() {
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
    switch (Settings.current.soundType) {
      case Settings.SOUND_LINUX:
        uninit_linux();
      case Settings.SOUND_JAVA:
        uninit_java();
      case Settings.SOUND_WINDOWS:
        uninit_windows();
    }
    if (player != null) {
      player.cancel();
      player = null;
    }
    if (record != null) {
      record.close();
      record = null;
    }
  }

  private void uninit_linux() {
    if (snd_id_play != -1) {
      LnxAPI.snd_stop(snd_id_play);
      LnxAPI.snd_free_id(snd_id_play);
      snd_id_play = -1;
    }
    if (snd_id_record != -1) {
      if (linuxReader != null) {
        linuxReader.stopReader();
        linuxReader = null;
      }
      LnxAPI.snd_stop(snd_id_record);
      LnxAPI.snd_free_id(snd_id_record);
      snd_id_record = -1;
    }
  }

  private void uninit_java() {
    if (sdl != null) {
      try {
        if (Settings.current.keepAudioOpen) {
          sdl.stop();
        } else {
          if (playing) {
            sdl.stop();
            playing = false;
            mc.setMeterPlay(0);
          }
        }
        sdl.close();
      } catch (Exception e) {
      }
      sdl = null;
    }
    if (tdl != null) {
      try {
        tdl.stop();
        tdl.close();
      } catch (Exception e) {
      }
      tdl = null;
    }
  }

  private void uninit_windows() {
    if (snd_id_play != -1) {
      WinAPI.snd_play_stop(snd_id_play);
      WinAPI.snd_free_id(snd_id_play);
      snd_id_play = -1;
    }
    if (snd_id_record != -1) {
      if (windowsReader != null) {
        windowsReader.cancel();
        windowsReader = null;
      }
      WinAPI.snd_record_stop(snd_id_record);
      WinAPI.snd_free_id(snd_id_record);
      snd_id_record = -1;
    }
  }

  /** Returns if software volume control on recording. */

  public boolean isSWVolRec() {
    return swVolRec;
  }

  /** Returns if software volume control on playing. */

  public boolean isSWVolPlay() {
    return swVolPlay;
  }

  /** Changes which line user wants to listen to. */

  public void selectLine(int line) {
    this.line = line;
  }

  /** Changes software/hardware playback volume level. */

  public void setVolPlay(int lvl) {
    volPlay = lvl;
    if (swVolPlay) return;
    float flvl = lvl/100.0f;
//    System.out.println("play flvl = " + flvl);
    if (sdlvol == null) return;
    sdlvol.setValue(flvl);
  }

  /** Changes software/hardware recording volume level. */

  public void setVolRec(int lvl) {
    volRec = lvl;
    if (swVolRec) return;
    float flvl = lvl/100.0f;
//    System.out.println("rec flvl = " + flvl);
    if (tdlvol == null) return;
    tdlvol.setValue(flvl);
  }

  /** Sets mute state. */

  public void setMute(boolean state) {
    mute = state;
  }

  /** Scales samples to a software volume control. */

  private void scaleBufferVolume(short buf[], int start, int len, int scale) {
    float fscale;
    if (scale == 0) {
      for (int a = 0; a < 160; a++) {
        buf[a] = 0;
      }
    } else {
      if (scale <= 75) {
        fscale = 1.0f - ((75-scale) * 0.014f);
        for (int a = 0; a < 160; a++) {
          buf[a] = (short) (buf[a] * fscale);
        }
      } else {
        fscale = 1.0f + ((scale-75) * 0.04f);
        float value;
        for (int a = 0; a < 160; a++) {
          value = buf[a] * fscale;
          if (value < Short.MIN_VALUE) buf[a] = Short.MIN_VALUE;
          else if (value > Short.MAX_VALUE) buf[a] = Short.MAX_VALUE;
          else buf[a] = (short)value;
        }
      }
    }
  }

  private short lastSampleUp = 0;

  /** Scales a buffer from 8000hz to 44100hz (linear interpolated) */

  private short[] scaleBufferFreqUp44_1kLinear(short inBuf[], short outBuf[]) {
    //8000 = 160 samples
    //44100 = 882 samples
    //ratio = 5.5125
    int outPos = 0, inPos = 0;
    float v1 = lastSampleUp, v2 = 0;
    int i;
    int im = 0;
    float d;
    for(int a=0;a<160;a++) {
      v2 = inBuf[inPos++];
      i = 5;
      im += 5125;
      if (im >= 10000) {
        im -= 10000;
        i++;
      }
      d = (v2 - v1) / i;
      for(int b=0;b<i;b++) {
        outBuf[outPos++] = (short)v1;
        v1 += d;
      }
      v1 = v2;
    }
    lastSampleUp = (short)v2;
    return outBuf;
  }

  /** Scales a buffer from 8000hz to 32000hz (linear interpolated) */

  private short[] scaleBufferFreqUp32kLinear(short inBuf[], short outBuf[]) {
    //8000 = 160 samples
    //32000 = 640 samples
    //ratio = 4.0
    int outPos = 0, inPos = 0;
    float v1 = lastSampleUp, v2 = 0;
    int i;
    int im = 0;
    float d;
    for(int a=0;a<160;a++) {
      v2 = inBuf[inPos++];
      d = (v2 - v1) / 4;
      for(int b=0;b<4;b++) {
        outBuf[outPos++] = (short)v1;
        v1 += d;
      }
      v1 = v2;
    }
    lastSampleUp = (short)v2;
    return outBuf;
  }

  /*
Filter Cap.
Causes the waveform to slope gradually just like a capacitor would in a real audio circuit.
(I knew my electronics degree was good for something)
  */

  /** Scales a buffer from 8000hz to 44100hz (filter cap interpolated) */

  private short[] scaleBufferFreqUp44_1kFilterCap(short inBuf[], short outBuf[]) {
    //8000 = 160 samples
    //44100 = 882 samples
    //ratio = 5.5125
    int outPos = 0, inPos = 0;
    float v1 = lastSampleUp, v2 = 0;
    int i;
    int im = 0;
    float d, c, x, y;  //delta per step, total change at this step, scale factor, scale factor per step
    for(int a=0;a<160;a++) {
      v2 = inBuf[inPos++];
      i = 5;
      im += 5125;
      if (im >= 10000) {
        im -= 10000;
        i++;
      }
      d = (v2 - v1) / i;
      c = d;
      y = 1.0f / i;
      x = y;
      for(int b=0;b<i;b++) {
        outBuf[outPos++] = (short)(v1 + (c * x));
        c += d;
        x += y;
      }
      v1 = v2;
    }
    lastSampleUp = (short)v2;
    return outBuf;
  }

  /** Scales a buffer from 8000hz to 32000hz (filter cap interpolated) */

  private short[] scaleBufferFreqUp32kFilterCap(short inBuf[], short outBuf[]) {
    //8000 = 160 samples
    //32000 = 640 samples
    //ratio = 4.0
    int outPos = 0, inPos = 0;
    float v1 = lastSampleUp, v2 = 0;
    int i;
    int im = 0;
    float d, c, x;  //delta per step, total change at this step, scale factor
    for(int a=0;a<160;a++) {
      v2 = inBuf[inPos++];
      d = (v2 - v1) / 4.0f;
      c = d;
      x = 0.25f;
      for(int b=0;b<4;b++) {
        outBuf[outPos++] = (short)(v1 + (c * x));
        c += d;
        x += 0.25f;
      }
      v1 = v2;
    }
    lastSampleUp = (short)v2;
    return outBuf;
  }

  /** Scales a buffer from 44100hz to 8000hz (non-interpolated) */

  private short[] scaleBufferFreqDown44_1k(short inBuf[], short outBuf[]) {
    //8000 = 160 samples
    //44100 = 882 samples
    //ratio = 5.5125
    int outPos = 0, inPos = 0;
    int im = 0;
    for(int a=0;a<160;a++) {
      outBuf[outPos++] = inBuf[inPos];
      inPos += 5;
      im += 5125;
      if (im >= 10000) {
        inPos++;
        im -= 10000;
      }
    }
    return outBuf;
  }

  /** Scales a buffer from 32000hz to 8000hz (non-interpolated) */

  private short[] scaleBufferFreqDown32k(short inBuf[], short outBuf[]) {
    //8000 = 160 samples
    //32000 = 640 samples
    //ratio = 4.0
    int outPos = 0, inPos = 0;
    int im = 0;
    for(int a=0;a<160;a++) {
      outBuf[outPos++] = inBuf[inPos];
      inPos += 4;
    }
    return outBuf;
  }

  //big endian (java sound)
  private byte[] short2byte(short in[], byte out[]) {
    for (int a = 0; a < sampleRate50; a++) {
      out[a * 2] = (byte) (in[a] >>> 8);
      out[a * 2 + 1] = (byte) (in[a] & 0xff);
    }
    return out;
  }

  //big endian (java sound)
  private void byte2short(byte buf8[], short buf16[]) {
    for (int a = 0; a < 160; a++) {
      buf16[a] = (short) ((((short) (buf8[a * 2])) << 8) + (((short) (buf8[a * 2 + 1])) & 0xff));
    }
  }

  /** Writes data to the audio system (output to speakers). */

  private void write(short buf[]) {
    if (player == null) return;
    if (swVolPlay) scaleBufferVolume(buf, 0, 160, volPlay);
    player.buffer.add(buf, 0, 160);
    synchronized(player.lock) {
      player.lock.notify();
    }
    int lvl = 0;
    for (int a = 0; a < 160; a++) {
      if (Math.abs(buf[a]) > lvl) lvl = Math.abs(buf[a]);
    }
    mc.setMeterPlay(lvl * 100 / 32768);
    if ((Settings.current.speakerMode) && (lvl >= Settings.current.speakerThreshold)) {
      if (speakerDelay <= 0) {
        mc.setSpeakerStatus(false);
      }
      speakerDelay = Settings.current.speakerDelay;
    }
  }

  private void write_linux(short buf[]) {
    LnxAPI.snd_play_write(snd_id_play, buf, 1, sampleRate, 16);
  }

  private void write_java(short buf[]) {
    if (sdl != null) {
      sdl.write(short2byte(buf, outjava), 0, sampleRate50x2);
    }
  }

  private void write_windows(short buf[]) {
    int cnt = WinAPI.snd_play_buffers_full(snd_id_play);
    if (cnt == 0) {
      //buffer underrun - reprime output
      JFLog.log("warn:windows audio output:buffer underrun");
      WinAPI.snd_play_write(snd_id_play, silence);
    }
    if (!WinAPI.snd_play_write(snd_id_play, buf)) {
      JFLog.log("warn:windows audio output:buffer overflow");
    }
  }

  /** Reads data from the audio system (input from mic). */

  private boolean read(short buf[]) {
    short dataDown[] = null;
    switch (sampleRate) {
      case 8000: dataDown = buf; break;
      case 32000: dataDown = dataDown32k; break;
      case 44100: dataDown = dataDown44_1k; break;
    }
    switch (Settings.current.soundType) {
      case Settings.SOUND_LINUX: if (!read_linux(dataDown)) return false; break;
      case Settings.SOUND_JAVA: if (!read_java(dataDown)) return false; break;
      case Settings.SOUND_WINDOWS: if (!read_windows(dataDown)) return false; break;
    }
    switch (sampleRate) {
      case 32000: scaleBufferFreqDown32k(dataDown, buf); break;
      case 44100: scaleBufferFreqDown44_1k(dataDown, buf); break;
    }
    if (swVolRec) scaleBufferVolume(buf, 0, 160, volRec);
    int lvl = 0;
    for (int a = 0; a < 160; a++) {
      if (Math.abs(buf[a]) > lvl) lvl = Math.abs(buf[a]);
    }
    mc.setMeterRec(lvl * 100 / 32768);
    if (speakerDelay > 0) {
      speakerDelay -= 20;
      System.arraycopy(silence, 0, buf, 0, 160);
      if (speakerDelay <= 0) {
        mc.setSpeakerStatus(true);
      }
    }
    return true;
  }

  private class LinuxReader extends Thread {
    private volatile boolean active = true;
    private volatile boolean done = false;
    public void run() {
      while (active) {
        short samples[] = LnxAPI.snd_record_read_shortArray(snd_id_record);  //BLOCKS until next buffer is full
        if (samples == null) {
          JFLog.log("warn:null recording from Linux sound");
          JF.sleep(10);
          continue;
        }
        addLinuxSoundBytes(samples, 0, samples.length);
      }
      done = true;
    }
    public void stopReader() {
      active = false;
      while (!done) { JF.sleep(20); }
    }
  }

  private boolean read_linux(short buf[]) {
    return linuxBuffer.get(buf, 0, sampleRate50);
  }

  private void addLinuxSoundBytes(short buf[], int pos, int len) {
    linuxBuffer.add(buf, pos, len);
  }

  private boolean read_java(short buf[]) {
    int ret;
    if (tdl == null) return true;
    if (tdl.available() < sampleRate50x2) return false;  //do not block (causes audio glitches)
    ret = tdl.read(injava, 0, sampleRate50x2);
    if (ret != sampleRate50x2) return false;
    byte2short(injava, buf);
    return true;
  }

  private void windowsReaderProcess() {
    if (!WinAPI.snd_record_read(snd_id_record, windowsSamples)) {
      JFLog.log("warn:windows input:buffer underrun");
      return;
    }
    addWindowsSoundBytes(windowsSamples, 0, windowsSamples.length);
  }

  private boolean read_windows(short buf[]) {
//    JFLog.log("get_windows:" + buf.length);
    boolean ret = windowsBuffer.get(buf, 0, sampleRate50);
    return ret;
  }

  private void addWindowsSoundBytes(short buf[], int pos, int len) {
//    JFLog.log("add_windows:" + len);
    windowsBuffer.add(buf, pos, len);
  }

  /** Flushes output buffers.  Should be called at start of a call. */

  public void flush() {
    switch (Settings.current.soundType) {
      case Settings.SOUND_LINUX:
        break;
      case Settings.SOUND_JAVA:
        try {
          sdl.flush();
        } catch (Exception e) {
          JFLog.log(e);
        }
        break;
      case Settings.SOUND_WINDOWS:
        break;
    }
  }

  /** Timer event that is triggered every 20ms.  Processes playback / recording. */

  public void process() {
    //20ms timer
    //do playback
    try {
      int cc = 0;  //conf count
      byte encoded[];
      if (!Settings.current.keepAudioOpen) {
        if (!playing) {
          for (int a = 0; a < 6; a++) {
            if ((lines[a].talking) || (lines[a].ringing)) {
              playing = true;
              if (sdl != null) sdl.start();
              if (snd_id_play != -1) LnxAPI.gst_resume(snd_id_play);
              //TODO : start Windows output
              write(silence);  //prime output
              break;
            }
          }
        } else {
          int pc = 0;  //playing count
          for (int a = 0; a < 6; a++) {
            if ((lines[a].talking) || (lines[a].ringing)) {
              pc++;
            }
          }
          if (pc == 0) {
            playing = false;
            mc.setMeterPlay(0);
            if (sdl != null) sdl.stop();
            if (snd_id_play != -1) LnxAPI.gst_resume(snd_id_play);
          }
        }
      }
      for (int a = 0; a < 6; a++) {
        if (lines[a].talking) {
          if ((lines[a].cnf) && (!lines[a].hld)) cc++;
        }
      }
      for (int a = 0; a < 6; a++) {
        if (lines[a].ringing) {
          if (!outRinging) {
            startRinging();
            outRinging = true;
          }
          break;
        }
        if (a == 5) {
          outRinging = false;
        }
      }
      for (int a = 0; a < 6; a++) {
        if (lines[a].incoming) {
          if (!inRinging) {
            if (wav.isLoaded()) {
              wav.reset();
            } else {
              startRinging();
            }
            inRinging = true;
          }
          break;
        }
        if (a == 5) {
          inRinging = false;
        }
      }
      if ((cc > 1) && (line != -1) && (lines[line].cnf)) {
        //conference mode
        System.arraycopy(silence, 0, mixed, 0, 160);
        for (int a = 0; a < 6; a++) {
          if ((lines[a].talking) && (lines[a].cnf) && (!lines[a].hld) && (lines[a].rtp.getSamples(lines[a].samples))) {
            mix(mixed, lines[a].samples);
          }
        }
        if (inRinging) mix(mixed, getCallWaiting());
        if (lines[line].dtmf != 'x') mix(mixed, dtmf.getSamples(lines[line].dtmf));
        write(mixed);
      } else {
        //single mode
        System.arraycopy(silence, 0, mixed, 0, 160);
        if (line != -1) {
          if (lines[line].dtmf != 'x') mix(mixed, dtmf.getSamples(lines[line].dtmf));
        }
        if ((line != -1) && (lines[line].talking) && (!lines[line].hld)) {
          if (lines[line].rtp.getSamples(indata)) mix(mixed, indata);
          if (inRinging) mix(mixed, getCallWaiting());
          write(mixed);
        } else {
          if (inRinging || outRinging) mix(mixed, getRinging());
          if ((playing) || (Settings.current.keepAudioOpen)) write(mixed);
        }
      }
      if (record != null) System.arraycopy(mixed, 0, recording, 0, 160);
      //do recording
      boolean readstatus = read(outdata);
      if (!readstatus) JFLog.log("Sound:mic underbuffer");
      if ((mute) || (!readstatus)) System.arraycopy(silence, 0, outdata, 0, 160);
      for (int a = 0; a < 6; a++) {
        if ((lines[a].talking) && (!lines[a].hld)) {
          if ((lines[a].cnf) && (cc > 1)) {
            //conference mode (mix = outdata + all other cnf lines except this one)
            System.arraycopy(outdata, 0, mixed, 0, 160);
            for (int b = 0; b < 6; b++) {
              if (b == a) continue;
              if ((lines[b].talking) && (lines[b].cnf) && (!lines[b].hld)) mix(mixed, lines[b].samples);
            }
            encoded = lines[a].rtp.coder.encode(mixed);
            if (record != null) mix(recording, mixed);
          } else {
            //single mode
            if (line == a) {
              encoded = lines[a].rtp.coder.encode(outdata);
              if (record != null) mix(recording, outdata);
            } else {
              encoded = lines[a].rtp.coder.encode(silence);
            }
          }
          if (lines[a].dtmfend) {
            lines[a].rtp.getDefaultChannel().writeDTMF(lines[a].dtmf, true);
          } else if (lines[a].dtmf != 'x') {
            lines[a].rtp.getDefaultChannel().writeDTMF(lines[a].dtmf, false);
          } else {
            lines[a].rtp.getDefaultChannel().writeRTP(encoded,0,encoded.length);
            writeCnt++;
          }
        }
        if (lines[a].dtmfend) {
          lines[a].dtmfend = false;
          lines[a].dtmf = 'x';
        }
      }
      if (record != null) record.write(recording);  //file I/O - may need to move this out of 20ms timer
    } catch (Exception e) {
      JFLog.log(e);
    }
  }

  /** Mixes 'in' samples into 'out' samples. */

  public void mix(short out[], short in[]) {
    for (int a = 0; a < 160; a++) {
      out[a] += in[a];
    }
  }

  /** Starts a generated ringing phone sound. */

  public void startRinging() {
    ring_440 = 0;
    ring_480 = 0;
    ringCycle = 0;
    ringCount = 0;
    wait_440 = 0;
    waitCycle = 0;
  }

  private final double ringVol = 8000.0;
  private int ring_440, ring_480;
  private int ringCycle;
  private int ringCount;
  private int wait_440;
  private int waitCycle;

  /** Returns next 20ms of a generated ringing phone. */

  public short[] getRinging() {
    //440 + 480
    //2 seconds on/3 seconds off
    if ((inRinging) && (wav.isLoaded())) {
      return wav.getSamples();
    }
    ringCount += 160;
    if (ringCount == 8000) {
      ringCount = 0;
      ringCycle++;
    }
    if (ringCycle == 5) ringCycle = 0;
    if (ringCycle > 1) {
      ring_440 = 0;
      ring_480 = 0;
      return silence;
    }
    //440
    for (int a = 0; a < 160; a++) {
      ringing[a] = (short) (Math.sin((2.0 * Math.PI / (8000.0 / 440.0)) * (a + ring_440)) * ringVol);
    }
    ring_440 += 160;
    if (ring_440 == 8000) ring_440 = 0;
    //480
    for (int a = 0; a < 160; a++) {
      ringing[a] += (short) (Math.sin((2.0 * Math.PI / (8000.0 / 480.0)) * (a + ring_480)) * ringVol);
    }
    ring_480 += 160;
    if (ring_480 == 8000) ring_480 = 0;
    return ringing;
  }

  /** Returns next 20ms of a generated call waiting sound (beep beep). */

  public short[] getCallWaiting() {
    //440 (2 bursts for 0.3 seconds)
    //2on 2off 2on 200off[4sec]
    waitCycle++;
    if (waitCycle == 206) waitCycle = 0;
    if ((waitCycle > 6) || (waitCycle == 2) || (waitCycle == 3)) {
      wait_440 = 0;
      return silence;
    }
    //440
    for (int a = 0; a < 160; a++) {
      callWaiting[a] = (short) (Math.sin((2.0 * Math.PI / (8000.0 / 440.0)) * (a + wait_440)) * ringVol);
    }
    wait_440 += 160;
    if (wait_440 == 8000) wait_440 = 0;
    return callWaiting;
  }

  /** Returns a list of sound mixers in the system. */

  public static String[] getMixers() {
    ArrayList<String> mixers = new ArrayList<String>();
    Mixer.Info mi[] = AudioSystem.getMixerInfo();
    mixers.add("<default>");
    for(int a=0;a<mi.length;a++) {
      mixers.add(mi[a].getName());
    }
    String sa[] = new String[mixers.size()];
    for(int a=0;a<mixers.size();a++) {
      sa[a] = mixers.get(a);
    }
    return sa;
  }

  /** Event from GStreamer (ignored) */
  public void event(int i, int i1, String string) {
  }

  public Record record;

  private class Player extends Thread {
    private volatile boolean active = true;
    private volatile boolean done = false;
    public SamplesBuffer buffer = new SamplesBuffer(8000);
    public final Object lock = new Object();
    public void run() {
      short buf[] = new short[160];
      short dataUp[] = null;
      switch (sampleRate) {
        case 8000: dataUp = buf; break;
        case 32000: dataUp = new short[640]; break;
        case 44100: dataUp = new short[882]; break;
      }
      while (active) {
        synchronized(lock) {
          if (buffer.size() < 160) {
            try { lock.wait(); } catch (Exception e) {}
          }
          if (buffer.size() < 160) continue;
        }
        buffer.get(buf, 0, 160);
        switch (sampleRate) {
          case 32000: {
            switch (Settings.current.interpolation) {
              case Settings.I_LINEAR: scaleBufferFreqUp32kLinear(buf, dataUp); break;
              case Settings.I_FILTER_CAP: scaleBufferFreqUp32kFilterCap(buf, dataUp); break;
            }
            break;
          }
          case 44100: {
            switch (Settings.current.interpolation) {
              case Settings.I_LINEAR: scaleBufferFreqUp44_1kLinear(buf, dataUp); break;
              case Settings.I_FILTER_CAP: scaleBufferFreqUp44_1kFilterCap(buf, dataUp); break;
            }
          }
        }
        switch (Settings.current.soundType) {
          case Settings.SOUND_LINUX: write_linux(dataUp); break;
          case Settings.SOUND_JAVA: write_java(dataUp); break;
          case Settings.SOUND_WINDOWS: write_windows(dataUp); break;
        }
      }
      done = true;
    }
    public void cancel() {
      active = false;
      while (!done) {
        synchronized(lock) {
          lock.notify();
        }
        JF.sleep(10);
      }
    }
  }
}
