/*
 * BasePhone.java
 *
 * Created on Oct 22, 2011, 10:26:03 AM
 *
 * @author pquiring@gmail.com
 *
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.reflect.*;

import javaforce.*;
import javaforce.voip.*;
import javaforce.jni.*;

/** Base Panel contains all phone logic code. */

public abstract class BasePhone extends javax.swing.JPanel implements SIPClientInterface, RTPInterface, ActionListener, KeyEventDispatcher {

  public static String version = "1.0.1";

  public void initBasePhone(GUI gui, WindowController wc) {
    JFLog.init(JF.getUserPath() + "/.jphone.log", true);
    this.gui = gui;
    this.wc = wc;
    setLAF();  //must do this before any GUI elements are created
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    if (Settings.isJavaScript) initRPC();
    Settings.isWindows = JF.isWindows();
    if (Settings.isWindows) {
      if (!WinAPI.init()) Settings.isWindows = false;
    }
  }

  //global data
  public GUI gui;
  public int localport = 5061;
  public int line = -1;  //current selected line (0-5) (-1=none)
  public PhoneLine lines[];
  public JToggleButton lineButtons[];
  public JButton numButtons[];
  public Sound sound = new Sound();
  public WindowController wc;
  public String lastDial;
  public boolean showingContacts = false;
  public boolean showingVideo = false;
  public java.util.Timer timerKeepAlive, timerRegisterExpires, timerRegisterRetries;
  public ImageIcon ii[];
  public String icons[] = {
    "blk.png", "grn.png", "red.png", "grey.png", "orange.png",
    "mic.png", "headset.png",
    "mute.png", "spk.png",
    "swscale.png", "hwscale.png",
    "icon_open.png", "icon_closed.png", "icon_busy.png", "icon_idle.png", "icon_dnd.png",
    "labels1.png", "labels2.png",
    "icon-24x24.png", "icon-16x16.png",
    "call.png", "end.png",
    "call2.png", "end2.png",
    "logo.png", "video.png", "record.png"
  };
  public final int PIC_BLACK = 0;
  public final int PIC_GREEN = 1;
  public final int PIC_RED = 2;
  public final int PIC_GREY = 3;
  public final int PIC_ORANGE = 4;
  public final int PIC_MIC = 5;
  public final int PIC_HEADSET = 6;
  public final int PIC_MUTE = 7;
  public final int PIC_SPK = 8;
  public final int PIC_SWSCALE = 9;
  public final int PIC_HWSCALE = 10;
  public final int PIC_ICON_OPEN = 11;
  public final int PIC_ICON_CLOSED = 12;
  public final int PIC_ICON_BUSY = 13;
  public final int PIC_ICON_IDLE = 14;
  public final int PIC_ICON_DND = 15;
  public final int PIC_LABELS1 = 16;
  public final int PIC_LABELS2 = 17;
  public final int PIC_TRAY_24 = 18;
  public final int PIC_TRAY_16 = 19;
  public final int PIC_CALL = 20;
  public final int PIC_END = 21;
  public final int PIC_CALL2 = 22;
  public final int PIC_END2 = 23;
  public final int PIC_LOGO = 24;
  public final int PIC_VIDEO = 25;
  public final int PIC_RECORD = 26;
  public int registerRetries;
  public SystemTray tray;
  public TrayIcon icon;
  public MenuItem exit, show;
  public Vector<Contact> contactList = new Vector<Contact>();
  public Vector<String> monitorList = new Vector<String>();
  public boolean loadingConfig = false;  //used just in updateContactList() to avoid trying to subscribe before registered
  public boolean active = true;
  public int timestamp = 0;  //video timestamp
  public static double nextStreamID = 4.0;
  public static double publishStreamID = 0.0;
  public static double playStreamID = 0.0;
  public boolean muted = false;

  public abstract void rtp_jpeg_receive(byte data[], int pos, int len);
  public void rtp_jpeg_send(byte data[], int pos, int len) {
    if (line == -1) return;
    if (!lines[line].talking) return;
    if (lines[line].Vrtp == null) return;
    RTPChannel rtpChannel = lines[line].Vrtp.getDefaultChannel();
    //GStreamer RTP already has RTP header
    //GStreamer always uses payload id 96 - need to change to 26 (JPEG)
    data[pos+1] &= (byte)0x80;  //preserve MARK bit
    data[pos+1] |= (byte)26;
    rtpChannel.writeRTP(data, pos, len);
  }

  /** Registers all SIP connections. */

  public void reRegisterAll() {
    int idx;
    String host;
    int port;
    for(int a=0;a<6;a++) {
      if ((a > 0) && (Settings.current.lines[a].same != -1)) continue;
      if (Settings.current.lines[a].host.length() == 0) continue;
      if (Settings.current.lines[a].user.length() == 0) continue;
      lines[a].sip = new SIPClient();
      idx = Settings.current.lines[a].host.indexOf(':');
      if (idx == -1) {
        host = Settings.current.lines[a].host;
        port = 5060;
      } else {
        host = Settings.current.lines[a].host.substring(0,idx);
        port = JF.atoi(Settings.current.lines[a].host.substring(idx+1));
      }
      int attempt = 0;
      while (!lines[a].sip.init(host, port, localport++, this)) {
        attempt++;
        if (attempt==10) {
          lines[a].sip = null;
          lines[a].status = "SIP init failed";
          if (a == line) gui.updateLine();
          break;
        }
      }
      if (lines[a].sip == null) continue;  //sip.init failed
      lines[a].user = Settings.current.lines[a].user;
//JFLog.log("lines[" + a + "].pass=" + Settings.current.lines[a].pass + "!");
      if ((Settings.current.lines[a].pass == null) || (Settings.current.lines[a].pass.length() == 0) || (Settings.current.lines[a].pass.equals("crypto(1,)"))) {
        lines[a].auth = true;
        lines[a].noregister = true;
        lines[a].status = "Ready (" + lines[a].user + ")";
        continue;
      }
    }
    //setup "Same as" lines
    int same;
    for(int a=1;a<6;a++) {
      same = Settings.current.lines[a].same;
      if (same == -1) continue;
      lines[a].sip = lines[same].sip;
      lines[a].user = lines[same].user;
      lines[a].noregister = lines[same].noregister;
      if (lines[a].noregister) {
        lines[a].auth = true;
        lines[a].status = "Ready (" + lines[a].user + ")";
      }
    }
    //register lines
    for(int a=0;a<6;a++) {
      if ((a > 0) && (Settings.current.lines[a].same != -1)) continue;
      if (lines[a].sip == null) continue;
      try {
        lines[a].sip.register(Settings.current.lines[a].user, Settings.current.lines[a].auth, Settings.getPassword(Settings.current.lines[a].pass));
      } catch (Exception e) {
        JFLog.log(e);
      }
    }
    //setup reRegister timer (expires)
    timerRegisterExpires = new java.util.Timer();
    timerRegisterExpires.scheduleAtFixedRate(new ReRegisterExpires(), 3595*1000, 3595*1000);  //do it 5 seconds early just to be sure
    registerRetries = 0;
    timerRegisterRetries = new java.util.Timer();
    timerRegisterRetries.schedule(new ReRegisterRetries(), 1000);
  }

  /** Expires registration with all SIP connections. */

  public void unRegisterAll() {
    if (timerRegisterExpires != null) {
      timerRegisterExpires.cancel();
      timerRegisterExpires = null;
    }
    if (timerRegisterRetries != null) {
      timerRegisterRetries.cancel();
      timerRegisterRetries = null;
    }
    for(int a=0;a<6;a++) {
      if (lines[a].incall) {
        gui.selectLine(a);
        end();
      }
      lines[a].dial = "";
      lines[a].status = "";
      lines[a].unauth = false;
      lines[a].auth = false;
      lines[a].noregister = false;
      lines[a].user = "";
      if ((a > 0) && (Settings.current.lines[a].same != -1)) {
        lines[a].sip = null;
        continue;
      }
      if (lines[a].sip == null) continue;
      if (lines[a].sip.isRegistered()) {
        try {
          lines[a].sip.unregister();
        } catch (Exception e) {
          JFLog.log(e);
        }
      }
    }
    int maxwait;
    for(int a=0;a<6;a++) {
      if (lines[a].sip == null) continue;
      maxwait = 1000;
      while (lines[a].sip.isRegistered()) { JF.sleep(10); maxwait -= 10; if (maxwait == 0) break; }
      lines[a].sip.uninit();
      lines[a].sip = null;
    }
  }

  /** Add a digit to be dialed. */

  public void addDigit(char digit) {
    if (line == -1) return;
    if (lines[line].sip == null) return;
    if (!lines[line].auth) return;
    if (lines[line].incoming) return;
    if (digit == KeyEvent.VK_BACK_SPACE) {
      if ((lines[line].incall)&&(!lines[line].xfer)) return;
      //delete digit
      int len = lines[line].dial.length();
      if (len > 0) lines[line].dial = lines[line].dial.substring(0, len-1);
    } else {
      if ((lines[line].incall)&&(!lines[line].xfer)) return;
      lines[line].dial += digit;
    }
    gui.updateLine();
  }

  /** KeyBinding action.  Causes DTMF generation if in a call. */

  public void pressDigit(char digit) {
    if (line == -1) return;
    if (lines[line].xfer) return;
    lines[line].dtmf = digit;
  }

  /** KeyBinding action.  Stops DTMF generation. */

  public void releaseDigit(char digit) {
    if (line == -1) return;
    if (lines[line].dtmf != 'x') lines[line].dtmfend = true;
  }

  /** Clears number to be dialed. */

  public void clear() {
    if (line == -1) return;
    lines[line].dial = "";
    gui.updateLine();
  }

  /** Sets the entire # to be dialed. */

  public void setDial(String number) {
    if (line == -1) return;
    lines[line].dial = number;
    gui.updateLine();
  }

  /** Starts a call or accepts an inbound call on selected line. */

  public void call() {
    gui.updateCallButton(false);
    if (line == -1) return;
    if (lines[line].sip == null) return;
    if (!lines[line].auth) return;
    if (lines[line].incall) {gui.updateCallButton(true); return;}  //already in call
    if (lines[line].dial.length() == 0) {gui.updateCallButton(false); return;}
    gui.updateCallButton(true);
    if (lines[line].incoming) {
      callAccept();
    } else {
      callInvite();
    }
    if (Settings.current.ac) {
      if (!lines[line].cnf) doConference();
    }
  }

  /** Terminates a call. */

  public void end() {
    gui.updateEndButton(false);
    if (line == -1) return;
    if (lines[line].incoming) {
      lines[line].sip.deny(lines[line].callid, "IGNORE", 480);
      lines[line].incoming = false;
      lines[line].ringing = false;
      lines[line].dial = "";
      lines[line].status = "Hungup";
      gui.updateLine();
      return;
    }
    lines[line].dial = "";
    if (!lines[line].incall) {
      //no call (update status)
      if ((lines[line].sip != null) && (!lines[line].unauth)) lines[line].status = "Ready (" + lines[line].user + ")";
      gui.updateLine();
      return;
    }
    if (lines[line].talking)
      lines[line].sip.bye(lines[line].callid);
    else
      lines[line].sip.cancel(lines[line].callid);
    endLine(line);
  }

  /** Cleanup after a call is terminated (call terminated local or remote). */

  public void endLine(int xline) {
    gui.updateEndButton(false);
    gui.updateCallButton(false);
    lines[xline].dial = "";
    lines[xline].status = "Hungup";
    lines[xline].trying = false;
    lines[xline].ringing = false;
    lines[xline].incoming = false;
    lines[xline].cnf = false;
    lines[xline].xfer = false;
    lines[xline].incall = false;
    lines[xline].talking = false;
    if (lines[xline].rtp != null) {
      lines[xline].rtp.stop();
    }
    lines[xline].rtp = null;
    if (lines[xline].Vrtp != null) {
      lines[xline].Vrtp.stop();
    }
    lines[xline].Vrtp = null;
    lines[xline].callid = "";
    gui.updateLine();
    gui.endLineUpdate(xline);
  }

  /** Starts a outbound call. */

  public void callInvite() {
    lines[line].to = lines[line].dial;
    lines[line].rtp = new RTP();
    lines[line].rtp.init(this);
    lines[line].Vrtp = new RTP();
    lines[line].Vrtp.init(this);
    lines[line].Vrtp.setMTU(32768);
    lines[line].incall = true;
    lines[line].trying = false;
    lines[line].ringing = false;
    lines[line].talking = false;
    lines[line].incoming = false;
    lines[line].status = "Dialing";
    lastDial = lines[line].dial;
    Settings.addCallLog(lines[line].dial);
    Codec[] codecs = new Codec[0];
    int enabledCodecs[] = Settings.current.getCodecs();
    for(int a=0;a<enabledCodecs.length;a++) {
      if (enabledCodecs[a] == RTP.CODEC_G729a.id) codecs = SIP.addCodec(codecs, RTP.CODEC_G729a);
      if (enabledCodecs[a] == RTP.CODEC_G711u.id) codecs = SIP.addCodec(codecs, RTP.CODEC_G711u);
      if (enabledCodecs[a] == RTP.CODEC_G711a.id) codecs = SIP.addCodec(codecs, RTP.CODEC_G711a);
    }
    int idx = Settings.current.lines[line].same;
    if (idx == -1) idx = line;
    if (!Settings.current.lines[idx].disableVideo) {
      switch (Settings.current.videoType) {
        case Settings.VIDEO_LINUX: codecs = SIP.addCodec(codecs, RTP.CODEC_JPEG); break;
        case Settings.VIDEO_WINDOWS: codecs = SIP.addCodec(codecs, RTP.CODEC_JPEG); break;
      }
    }
    lines[line].callid = lines[line].sip.invite(lines[line].dial, lines[line].rtp.getlocalrtpport(), lines[line].Vrtp.getlocalrtpport(), codecs);
    gui.updateLine();
    gui.callInviteUpdate();
  }

  /** Accepts an inbound call on selected line. */

  public void callAccept() {
    if ( (!SIP.hasCodec(lines[line].codecs, RTP.CODEC_G729a) || !Settings.current.hasCodec(RTP.CODEC_G729a))
      && (!SIP.hasCodec(lines[line].codecs, RTP.CODEC_G711u) || !Settings.current.hasCodec(RTP.CODEC_G711u))
      && (!SIP.hasCodec(lines[line].codecs, RTP.CODEC_G711a) || !Settings.current.hasCodec(RTP.CODEC_G711a)) )
    {
      JFLog.log("err:callAccept() : No compatible audio codec offered");
      lines[line].sip.deny(lines[line].callid, "NO_COMPATIBLE_CODEC", 415);
      onCancel(lines[line].sip, lines[line].callid, 415);
      return;
    }
    lines[line].to = lines[line].dial;
    lines[line].rtp = new RTP();
    lines[line].rtp.init(this);
    if (lines[line].remoteVrtpport != -1) {
      lines[line].Vrtp = new RTP();
      lines[line].Vrtp.init(this);
      lines[line].Vrtp.setMTU(32768);
    }
    int enabledCodecs[] = Settings.current.getCodecs();
    for(int a=0;a<enabledCodecs.length;a++) {
      if ((enabledCodecs[a] == RTP.CODEC_G729a.id) && (SIP.hasCodec(lines[line].codecs, RTP.CODEC_G729a))) {
        lines[line].codecs = SIP.delCodec(lines[line].codecs, RTP.CODEC_G711u);
        lines[line].codecs = SIP.delCodec(lines[line].codecs, RTP.CODEC_G711a);
        break;
      }
      if ((enabledCodecs[a] == RTP.CODEC_G711u.id) && (SIP.hasCodec(lines[line].codecs, RTP.CODEC_G711u))) {
        lines[line].codecs = SIP.delCodec(lines[line].codecs, RTP.CODEC_G729a);
        lines[line].codecs = SIP.delCodec(lines[line].codecs, RTP.CODEC_G711a);
        break;
      }
      if ((enabledCodecs[a] == RTP.CODEC_G711a.id) && (SIP.hasCodec(lines[line].codecs, RTP.CODEC_G711a))) {
        lines[line].codecs = SIP.delCodec(lines[line].codecs, RTP.CODEC_G711u);
        lines[line].codecs = SIP.delCodec(lines[line].codecs, RTP.CODEC_G729a);
        break;
      }
    }
    lines[line].sip.accept(lines[line].callid, lines[line].rtp.getlocalrtpport(),
      (lines[line].remoteVrtpport != -1 ? lines[line].Vrtp.getlocalrtpport() : -1), lines[line].codecs);
    sound.flush();
    lines[line].rtp.start(lines[line].remotertphost, lines[line].remotertpport, lines[line].codecs, false);
    if (lines[line].remoteVrtpport != -1) {
      lines[line].Vrtp.start(lines[line].remotertphost, lines[line].remoteVrtpport, lines[line].codecs, true);
    }
    lines[line].incall = true;
    lines[line].ringing = false;
    lines[line].incoming = false;
    lines[line].talking = true;
    lines[line].status = "Connected";
    gui.updateLine();
    updateIconTray();
  }

  /** Triggered when an outbound call (INVITE) was accepted. */

  public boolean callInviteSuccess(int xline, String remotertphost, int remotertpport, int remoteVrtpport) {
    JFLog.log("note:callInviteSuccess():remotertpport=" + remotertpport + ",remoteVrtpprt=" + remoteVrtpport);
    if ( (!SIP.hasCodec(lines[xline].codecs, RTP.CODEC_G729a) || !Settings.current.hasCodec(RTP.CODEC_G729a))
      && (!SIP.hasCodec(lines[xline].codecs, RTP.CODEC_G711u) || !Settings.current.hasCodec(RTP.CODEC_G711u))
      && (!SIP.hasCodec(lines[xline].codecs, RTP.CODEC_G711a) || !Settings.current.hasCodec(RTP.CODEC_G711a)) )
    {
      JFLog.log("err:callInviteSuccess() : No compatible audio codec returned");
      lines[xline].sip.bye(lines[xline].callid);
      onCancel(lines[xline].sip, lines[xline].callid, 415);
      return false;
    }
    lines[xline].remotertphost = remotertphost;
    lines[xline].remotertpport = remotertpport;
    lines[xline].remoteVrtpport = remoteVrtpport;
    sound.flush();
    lines[xline].rtp.start(remotertphost, remotertpport, lines[xline].codecs, false);
    if (remoteVrtpport != -1) {
      lines[xline].Vrtp.start(remotertphost, remoteVrtpport, lines[xline].codecs, true);
    }
    if (Settings.current.aa) gui.selectLine(xline);
    return true;
  }

  /** Triggered when an outbound call (INVITE) was refused. */

  public void callInviteFail(int xline) {
    lines[xline].incall = false;
    lines[xline].trying = false;
    lines[xline].rtp.uninit();
    lines[xline].rtp = null;
    lines[xline].callid = "";
  }

  /** Start or finish a call transfer. */

  public void doXfer() {
    if (line == -1) return;
    if (!lines[line].incall) return;
    if (lines[line].xfer) {
      if (lines[line].dial.length() == 0) {
        //cancel xfer
        lines[line].status = "Connected";
        lines[line].xfer = false;
      } else {
        lines[line].sip.refer(lines[line].callid, lines[line].dial);
        lines[line].status = "XFER to " + lines[line].dial;
        lines[line].dial = "";
        lines[line].xfer = false;
        endLine(line);
      }
    } else {
      lines[line].dial = "";
      lines[line].status = "Enter dest and then XFER again";
      lines[line].xfer = true;
    }
    gui.updateLine();
  }

  /** Put a call into or out of hold. */

  public void doHold() {
    gui.hld_setIcon(ii[PIC_GREY]);
    if (line == -1) return;
    if (!lines[line].incall) return;
    if (lines[line].sip.isHold(lines[line].callid)) return;  //can't put on hold if you are on hold from other side
    if (lines[line].hld) {
      lines[line].sip.reinvite(lines[line].callid, lines[line].rtp.getlocalrtpport(), lines[line].codecs);
      lines[line].hld = false;
    } else {
      lines[line].sip.hold(lines[line].callid, lines[line].rtp.getlocalrtpport());
      lines[line].hld = true;
    }
    gui.hld_setIcon(ii[lines[line].hld ? PIC_RED : PIC_GREY]);
  }

  /** Redial last number dialed. */

  public void doRedial() {
    if (line == -1) return;
    if (lines[line].incall) return;
    if (lastDial == null) return;
    if (lastDial.length() == 0) return;
    lines[line].dial = lastDial;
    gui.updateLine();
    call();
  }

  /** Toggles AA (Auto Answer). */

  public void toggleAA() {
    Settings.current.aa = !Settings.current.aa;
    gui.aa_setIcon(ii[Settings.current.aa ? PIC_GREEN : PIC_GREY]);
  }

  /** Toggle AC (Auto Conference). */

  public void toggleAC() {
    Settings.current.ac = !Settings.current.ac;
    gui.ac_setIcon(ii[Settings.current.ac ? PIC_GREEN : PIC_GREY]);
  }

  /** Toggle DND (Do-Not-Disturb). */

  public void toggleDND() {
    if (line == -1) return;
    if (lines[line].incall) return;
    lines[line].dnd = !lines[line].dnd;
    gui.dnd_setIcon(ii[lines[line].dnd ? PIC_RED : PIC_GREY]);
    if (lines[line].dnd)
      lines[line].dial = Settings.current.dndCodeOn;
    else
      lines[line].dial = Settings.current.dndCodeOff;
    call();
  }

  /** Toggles the conference state of a line. */

  public void doConference() {
    if (line == -1) return;
    if (!lines[line].incall) return;
    lines[line].cnf = !lines[line].cnf;
    gui.cnf_setIcon(ii[lines[line].cnf ? PIC_GREEN : PIC_GREY]);
  }

  /** Toggles mute. */

  public void toggleMute() {
    muted = !muted;
    sound.setMute(muted);
    gui.mute_setIcon(ii[muted ? PIC_RED : PIC_GREY]);
  }

  /** Creates a timer to send keep-alives on all SIP connections.  Keep alive are done every 30 seconds (many routers have a 60 second timeout). */

  public void keepAliveinit() {
    timerKeepAlive = new java.util.Timer();
    timerKeepAlive.scheduleAtFixedRate(new KeepAlive(),0,30*1000);
  }

  /** TimerTask to perform keep-alives. on all SIP connections. */

  public class KeepAlive extends java.util.TimerTask {
    public void run() {
      for(int a=0;a<6;a++) {
        if (Settings.current.lines[a].same != -1) continue;
        if (lines[a].sip == null) continue;
        if (!lines[a].sip.isRegistered()) continue;
        lines[a].sip.keepalive();
      }
    }
  }

  /** TimerTask that reregisters all SIP connection after they expire (every 3600 seconds). */

  public class ReRegisterExpires extends java.util.TimerTask {
    public void run() {
      for(int a=0;a<6;a++) {
        if (Settings.current.lines[a].same != -1) continue;
        if (lines[a].sip == null) continue;
        if (lines[a].noregister) continue;
        lines[a].sip.reregister();
      }
      registerRetries = 0;
      if (timerRegisterRetries != null) {
        timerRegisterRetries = new java.util.Timer();
        timerRegisterRetries.schedule(new ReRegisterRetries(), 1000);
      }
    }
  }

  /** TimerTask that reregisters any SIP connections that have failed to register (checks every 1 second upto 5 attempts). */

  public class ReRegisterRetries extends java.util.TimerTask {
    public void run() {
      boolean again = false;
      for(int a=0;a<6;a++) {
        if (Settings.current.lines[a].same != -1) continue;
        if (lines[a].sip == null) continue;
        if (lines[a].unauth) continue;
        if (lines[a].noregister) continue;
        if (!lines[a].sip.isRegistered()) {
          JFLog.log("warn:retry register on line:" + (a+1));
          lines[a].sip.reregister();
          again = true;
        }
      }
      registerRetries++;
      if ((again) && (registerRetries < 5)) {
        timerRegisterRetries = new java.util.Timer();
        timerRegisterRetries.schedule(new ReRegisterRetries(), 1000);
      } else {
        for(int a=0;a<6;a++) {
          if (Settings.current.lines[a].same != -1) continue;
          if (lines[a].sip == null) continue;
          if (lines[a].unauth) continue;
          if (lines[a].noregister) continue;
          if (!lines[a].sip.isRegistered()) {
            lines[a].unauth = true;  //server not responding after 5 attempts to register
          }
        }
        timerRegisterRetries = null;
      }
    }
  }

  /** Loads icons during startup. */

  public void loadIcons() {
    ii = new ImageIcon[icons.length];
    for(int a=0;a<icons.length;a++) {
      try {
        InputStream is = getClass().getClassLoader().getResourceAsStream(icons[a]);
        int len = is.available();
        byte data[] = new byte[len];
        is.read(data);
        is.close();
        ii[a] = new ImageIcon(data);
      } catch (Exception e) {
        JFLog.log("err:loadIcons() Failed:" + e);
        System.exit(0);
      }
    }
  }

  /** Checks for an online update on startup. */

  public void checkVersion() {
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(
        new URL("http://jphonelite.sourceforge.net/version.php").openStream()));
      String line = reader.readLine();
      if (line.equals(version)) {JFLog.log("version is up-to-date"); return;}
      JFLog.log("newer version is available : " + line);
      JOptionPane.showMessageDialog(this,
        "A newer version of jphonelite is available! (v" + line + ")\r\nPlease goto http://jphonelite.sourceforge.net to download it",
        "Info",
        JOptionPane.INFORMATION_MESSAGE);
    } catch (Exception e) {
      JFLog.log("err:unable to check for version update");
      JFLog.log(e);
    }
  }

  /** Updates notification area test. */

  public void updateIconTray() {
    if (icon == null) return;
    if (active) return;
    StringBuffer buf = new StringBuffer();
    for(int a=0;a<6;a++) {
      if (lines[a].incoming == true) {
        if (buf.length() > 0) buf.append("\r\n");
        buf.append("\"" + lines[a].callerid + "\" " + lines[a].dial + " is on Line " + (a+1));
      }
    }
    if (buf.length() > 0) {
      icon.displayMessage("Incoming Call(s)", buf.toString(), TrayIcon.MessageType.INFO);
    } else {
      //BUG? How do you hide a message if visible???
    }
  }

  /** Converts byte[4] to int. (big endian) */

  public static int getuint32(byte data[], int offset) {
    int ret;
    ret  = (int)data[offset+3] & 0xff;
    ret += ((int)data[offset+2] & 0xff) << 8;
    ret += ((int)data[offset+1] & 0xff) << 16;
    ret += ((int)data[offset+0] & 0xff) << 24;
    return ret;
  }

  /** Converts int to byte[4]. (big endian) */

  public static void setuint32(byte data[], int offset, int value) {
    int ret;
    data[offset+3] = (byte)(value & 0xff);
    value >>= 8;
    data[offset+2] = (byte)(value & 0xff);
    value >>= 8;
    data[offset+1] = (byte)(value & 0xff);
    value >>= 8;
    data[offset+0] = (byte)(value & 0xff);
  }

  /** Toggles speaker phone mode. */

  public void toggleSpeaker() {
    Settings.current.speakerMode = !Settings.current.speakerMode;
    gui.spk_setIcon(ii[Settings.current.speakerMode ? PIC_GREEN : PIC_GREY]);
  }

  /** Returns status of a line. */

  public String getStatus(int line) {
    return lines[line].status;
  }

//SIPClientInterface interface

  /** SIPClientInterface.onRegister() : triggered when a SIPClient has confirmation of a registration with server. */

  public void onRegister(SIPClient sip, boolean status) {
    if (status) {
      //success
      for(int a=0;a<6;a++) {
        if (lines[a].sip != sip) continue;
        if (lines[a].status.length() == 0) lines[a].status = "Ready (" + lines[a].user + ")";
        lines[a].auth = true;
        if (line == -1) {
          gui.selectLine(a);
        } else {
          if (line == a) gui.updateLine();
        }
      }
      sip.subscribe(sip.getUser(), "message-summary", 3600);  //SUBSCRIBE to self for message-summary event (not needed with Asterisk but X-Lite does it)
      gui.onRegister(sip);
    } else {
      //failed
      for(int a=0;a<6;a++) {
        if (lines[a].sip == sip) {
          lines[a].status = "Unauthorized";
          lines[a].unauth = true;
          if (line == a) gui.selectLine(-1);
        }
      }
    }
  }

  /** SIPClientInterface.onTrying() : triggered when an INVITE returns status code 100 (TRYING). */

  public void onTrying(SIPClient sip, String callid) {
    //is a line trying to do an invite
    for(int a=0;a<6;a++) {
      if ((lines[a].incall)&&(!lines[a].trying)) {
        if (lines[a].callid.equals(callid)) {
          lines[a].trying = true;
          lines[a].status = "Trying";
          if (line == a) gui.updateLine();
        }
      }
    }
  }

  /** SIPClientInterface.onRinging() : triggered when an INVITE returns status code 180/183 (RINGING). */

  public void onRinging(SIPClient sip, String callid) {
    //is a line trying to do an invite
    for(int a=0;a<6;a++) {
      if ((lines[a].incall)&&(!lines[a].ringing)) {
        if (lines[a].callid.equals(callid)) {
          lines[a].ringing = true;
          lines[a].status = "Ringing";
          if (line == a) gui.updateLine();
        }
      }
    }
  }

  /** SIPClientInterface.onSuccess() : triggered when an INVITE returns status code 200 (OK). */

  public void onSuccess(SIPClient sip, String callid, String remotertphost, int remotertpport, int remoteVrtpport, Codec codecs[]) {
    for(int a=0;a<codecs.length;a++) {
      JFLog.log("note:onSuccess : codecs[] = " + codecs[a].name + ":" + codecs[a].id);
    }
    if (remotertphost == null) {
      JFLog.log("err:remotertphost == null");
      return;
    }
    //is a line trying to do an invite or hold
    for(int a=0;a<6;a++) {
      if (!lines[a].incall) continue;
      if (!lines[a].callid.equals(callid)) continue;
      if (!lines[a].talking) {
        int cnt = 0;
        if (SIP.hasCodec(codecs, RTP.CODEC_G729a)) cnt++;
        if (SIP.hasCodec(codecs, RTP.CODEC_G711u)) cnt++;
        if (SIP.hasCodec(codecs, RTP.CODEC_G711a)) cnt++;
        if ((cnt > 1) && (Settings.current.reinvite)) {
          //returned more than one audio codec, reinvite with only one codec
          boolean ok = false;
          int enabledCodecs[] = Settings.current.getCodecs();
          lines[a].codecs = null;
          for(int b=0;b<enabledCodecs.length;b++) {
            if ((enabledCodecs[b] == RTP.CODEC_G729a.id) && (SIP.hasCodec(codecs, RTP.CODEC_G729a))) {
              codecs = new Codec[] {RTP.CODEC_G729a};
              ok = true;
              break;
            }
            if ((enabledCodecs[b] == RTP.CODEC_G711u.id) && (SIP.hasCodec(codecs, RTP.CODEC_G711u))) {
              codecs = new Codec[] {RTP.CODEC_G711u};
              ok = true;
              break;
            }
            if ((enabledCodecs[b] == RTP.CODEC_G711a.id) && (SIP.hasCodec(codecs, RTP.CODEC_G711a))) {
              codecs = new Codec[] {RTP.CODEC_G711a};
              ok = true;
              break;
            }
          }
          if (!ok) {
            lines[a].sip.bye(lines[a].callid);
            onCancel(lines[a].sip, lines[a].callid, 415);
            return;
          }
          lines[a].sip.reinvite(callid, lines[a].rtp.getlocalrtpport(), codecs);
          return;
        }
        lines[a].codecs = codecs;
        lines[a].status = "Connected";
        if (line == a) gui.updateLine();
        if (!callInviteSuccess(a, remotertphost, remotertpport, remoteVrtpport)) return;
        lines[a].talking = true;
        lines[a].ringing = false;
        return;
      }
      if (lines[a].hld) {
        lines[a].rtp.hold(true);
      } else {
        lines[a].rtp.hold(false);
      }
      return;
    }
    JFLog.log("err:onSuccess() for unknown call:" + callid);
  }

  /** SIPClientInterface.onBye() : triggered when server terminates a call. */

  public void onBye(SIPClient sip, String callid) {
    for(int a=0;a<6;a++) {
      if (lines[a].incall) {
        if (lines[a].callid.equals(callid)) {
          endLine(a);
          updateIconTray();
        }
      }
    }
  }

  /** SIPClientInterface.onInvite() : triggered when server send an INVITE to jphonelite. */

  public int onInvite(SIPClient sip, String callid, String fromid, String fromnumber, String remotertphost, int remotertpport, int remoteVrtpport, Codec codecs[]) {
    //NOTE : onInvite() can not change codecs (use SIP.accept() to do that)
    for(int a=0;a<codecs.length;a++) {
      JFLog.log("note:onInvite : codecs[] = " + codecs[a].name + ":" + codecs[a].id);
    }
    for(int a=0;a<6;a++) {
      if (lines[a].sip == sip) {
        if (lines[a].incall) {
          if (lines[a].callid.equals(callid)) {
            //reINVITEd (usually to change RTP host/port) (codec should not change since we only accept 1 codec)
            lines[a].codecs = codecs;
            lines[a].remotertphost = remotertphost;
            lines[a].remotertpport = remotertpport;
            lines[a].remoteVrtpport = remoteVrtpport;
            lines[a].rtp.change(remotertphost, remotertpport);
            lines[a].rtp.change(codecs);
            if (remoteVrtpport != -1) {
              lines[a].Vrtp.change(remotertphost, remoteVrtpport);
              lines[a].Vrtp.change(codecs);
            }
            return 200;
          }
          continue;
        }
        lines[a].dial = fromnumber;
        lines[a].callerid = fromid;
        if ((lines[a].callerid == null) || (lines[a].callerid.trim().length() == 0)) lines[a].callerid = "Unknown";
        Settings.addCallLog(lines[a].dial);
        gui.updateRecentList();
        lines[a].status = fromid + " is calling";
        lines[a].incoming = true;
        lines[a].remotertphost = remotertphost;
        lines[a].remotertpport = remotertpport;
        lines[a].remoteVrtpport = remoteVrtpport;
        lines[a].callid = callid;
        lines[a].ringing = true;
        lines[a].codecs = codecs;
        if (Settings.current.aa) {
          gui.selectLine(a);
          gui.updateLine();
          call();  //this will send a reply
          return -1;  //do NOT send a reply
        } else {
          if (line == a) gui.updateLine();
        }
        updateIconTray();
        return 180;  //reply RINGING
      }
    }
    return 486;  //reply BUSY
  }

  /** SIPClientInterface.onCancel() : triggered when server send a CANCEL request after an INVITE, or an error occured. */

  public void onCancel(SIPClient sip, String callid, int code) {
    for(int a=0;a<6;a++) {
      if (lines[a].callid.equals(callid)) {
        lines[a].dial = "";
        lines[a].status = "Hungup (" + code + ")";
        lines[a].ringing = false;
        lines[a].incoming = false;
        lines[a].incall = false;
        if (line == a) gui.updateLine();
        updateIconTray();
      }
    }
  }

  /** SIPClientInterface.onRefer() : triggered when the server signals a successful transfer (REFER). */

  public void onRefer(SIPClient sip, String callid) {
/*
    for(int a=0;a<6;a++) {
      if (lines[a].callid.equals(callid)) {
        endLine(a);
      }
    }
*/
  }

  /** SIPClientInterface.onNotify() : processes SIP:NOTIFY messages. */

  public void onNotify(SIPClient sip, String event, String content) {
//    JFLog.log("notify()");
    String contentLines[] = content.split("\r\n");
    if (event.equals("message-summary")) {
      String msgwait = SIP.getHeader("Messages-Waiting:", contentLines);
      if (msgwait != null) {
        for(int a=0;a<6;a++) {
          if (lines[a].sip == sip) {
//            JFLog.log("notify() line=" + a + ", msgwaiting = " + msgwaiting);
            lines[a].msgwaiting = msgwait.equalsIgnoreCase("yes");
          }
        }
      }
      return;
    }
    if (event.equals("presence")) {
      JFLog.log("note:Presence:" + content);
      if (!content.startsWith("<?xml")) {JFLog.log("Not valid presence data (1)"); return;}
      content = content.replaceAll("\r", "").replaceAll("\n", "");
      XML xml = new XML();  //against my better judgement I'm going to use my own XML (un)marshaller to process the data
      ByteArrayInputStream bais = new ByteArrayInputStream(content.getBytes());
      if (!xml.read(bais)) {JFLog.log("Not valid presence data (2)"); return;}
      XML.XMLTag contact = xml.getTag(new String[] { "presence", "tuple", "contact" });
      if (contact == null) {JFLog.log("Not valid presence data (3)"); return;}
      String fields[] = SIP.split("Unknown<" + contact.getContent() + ">");
      if (fields == null) {JFLog.log("Not valid presence data (4)"); return;}
      XML.XMLTag status = xml.getTag(new String[] { "presence", "tuple", "status", "basic" });
      if (status == null) {JFLog.log("Not valid presence data (5)"); return;}
      gui.setStatus(fields[1], fields[2], status.getContent().trim());
      return;
    }
    JFLog.log("Warning : unknown NOTIFY type : " + event);
  }

  /** SIPClientInterface.getResponse() - Not needed.  Password is already given to SIPClient. */
  public String getResponse(SIPClient sip, String realm, String cmd, String uri, String nonce, String qop, String nc, String cnonce) {return null;}

  /** Processes keyboard input. */

  public boolean dispatchKeyEvent(KeyEvent e) {
    if (line == -1) return false;
    Object awtsrc = e.getSource();
    if (!(awtsrc instanceof JComponent)) return false;
    JComponent src = (JComponent)awtsrc;
    if (src.getParent() != (Object)this) return false;
//    JFLog.log("KeyEvent : KeyCode=" + e.getKeyCode() + " KeyChar=" + e.getKeyChar() + " Mods=" + e.getModifiers() + " ID=" + e.getID());
    int id = e.getID();
    char ch = e.getKeyChar();
    int cc = e.getKeyCode();
    switch (id) {
      case KeyEvent.KEY_TYPED:
        if ((ch >= '0') && (ch <= '9')) addDigit(ch);
        if (ch == '*') addDigit(ch);
        if (ch == '#') addDigit(ch);
        if (ch == '/') addDigit('#');  //for keypad usage
        break;
      case KeyEvent.KEY_PRESSED:
        if (lines[line].xfer) {
          if (cc == KeyEvent.VK_ESCAPE) {lines[line].dial = ""; doXfer();}
          if (ch == KeyEvent.VK_ENTER) doXfer();
          break;
        }
        if ((ch >= '0') && (ch <= '9')) pressDigit(ch);
        switch (ch) {
          case '*': pressDigit(ch); break;
          case '#': pressDigit(ch); break;
          case '/': pressDigit('#'); break;  //for keypad usage
          case KeyEvent.VK_ENTER: {
            if (!lines[line].incall) call(); else pressDigit('#');
            break;
          }
        }
        switch (cc) {
          case KeyEvent.VK_BACK_SPACE: addDigit((char)cc); break;
          case KeyEvent.VK_ESCAPE: {
            if (!lines[line].incall) {lines[line].dial = ""; gui.updateLine();} else pressDigit('*');
            break;
          }
          case KeyEvent.VK_F1: gui.selectLine(0); break;
          case KeyEvent.VK_F2: gui.selectLine(1); break;
          case KeyEvent.VK_F3: gui.selectLine(2); break;
          case KeyEvent.VK_F4: gui.selectLine(3); break;
          case KeyEvent.VK_F5: gui.selectLine(4); break;
          case KeyEvent.VK_F6: gui.selectLine(5); break;
        }
        break;
      case KeyEvent.KEY_RELEASED:
        if (lines[line].xfer) break;
        if ((ch >= '0') && (ch <= '9')) releaseDigit(ch);
        if (ch == '*') releaseDigit(ch);
        if (ch == '#') releaseDigit(ch);
        if (ch == '/') releaseDigit('#');  //for keypad usage
        if (ch == KeyEvent.VK_ENTER) {
          if (lines[line].incall) releaseDigit('#');
        }
        if (cc == KeyEvent.VK_ESCAPE) releaseDigit('*');
        break;
    }
    return false;  //pass on as normal
  }

//interface RTPInterface

  /** RTPInterface.rtpDigit() */
  public void rtpDigit(RTP rtp, char digit) {}
  /** RTPInterface.rtpSamples() */
  public void rtpSamples(RTP rtp) {}
  /** RTPInterface.rtpPacket() */
  public void rtpPacket(RTP rtp, boolean rtcp, byte data[], int off, int len) {}
  /** RTPInterface.rtpH263() */
  public void rtpH263(RTP rtp, byte data[], int off, int len) {}
  /** RTPInterface.rtpH264() */
  public void rtpH264(RTP rtp, byte data[], int off, int len) {}
  /** RTPInterface.rtpJPEG() */
  public void rtpJPEG(RTP rtp, byte data[], int off, int len) {
    if (line == -1) return;
    switch (Settings.current.videoType) {
      case Settings.VIDEO_LINUX:
      case Settings.VIDEO_WINDOWS:
        if (lines[line].Vrtp == rtp) {
          rtp_jpeg_receive(data, off, len);
        }
        break;
    }
  }

  /** ActionListener : actionPerformed() - for the SystemTray Icon actions. */

  public void actionPerformed(ActionEvent e) {
    Object o = e.getSource();
    if (o == exit) {
      unRegisterAll();
      System.exit(0);
    }
    if (o == show) {
      wc.setPanelVisible();
    }
  }

  public void setLAF() {
//    LookAndFeel laf = UIManager.getLookAndFeel();
//    JFLog.log("current laf=" + laf);
    try { UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel"); } catch (Exception e) { JFLog.log(e); }  //only acceptable LAF
//    try { UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel"); } catch (Exception e) { JFLog.log(e); }  //crud
//    try { UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel"); } catch (Exception e) { JFLog.log(e); }  //crud
//    try { UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel"); } catch (Exception e) { JFLog.log(e); }  //crud
/*
    UIManager.LookAndFeelInfo infos[] = UIManager.getInstalledLookAndFeels();
    for(int a=0;a<infos.length;a++) {
      JFLog.log("infos[] = " + infos[a]);
    }
    UIManager.setLookAndFeel();
*/
  }

  public void selectLine(String ln) {
    gui.selectLine(JF.atoi(ln));
  }

  /*
   * These functions are for calling functions from outside the EDT. (ie: javascript)
   * and also preserve the applet's elevated security permissions (was not easy)
   * It seems like when an applet is called from javascript it's called within a lowered
   * security context, so I had to create a sort of RPC system to get around that.
   */

  private static Method method;
  private static Object object;
  private static String param;
  private static Object lock = new Object();
  private static Object lockReturn = new Object();
  private static String retValue;
  private static Vector<String> funcs = new Vector<String>();

  public void initRPC() {
    object = this;
    new RPCServer().start();
  }

  public static class RPCServer extends Thread {
    public void run() {
      synchronized(lock) {
        while (true) {
          try {lock.wait();} catch (Exception e) {JFLog.log(e);}
          if (funcs.size() == 0) continue;
          String func = funcs.remove(0);
          if (func.startsWith("=")) {
            try {
              method = object.getClass().getMethod(func.substring(1));
              EventQueue.invokeLater(new Runnable() {
                public void run() {
                  try {
                    retValue = (String)method.invoke(object);
                  } catch (InvocationTargetException e) {
                    JFLog.log(e.getCause());
                  } catch (Exception e) {
                    JFLog.log(e);
                  }
                  try {
                    synchronized(lockReturn) {
                      lockReturn.notifyAll();
                    }
                  } catch (Exception e) {
                    JFLog.log(e);
                  }
                }
              });
            } catch (Exception e) {
              JFLog.log(e);
            }
            continue;
          }
          int idx = func.indexOf(",");
          if (idx == -1) {
            try {
              method = object.getClass().getMethod(func);
              EventQueue.invokeLater(new Runnable() {
                public void run() {
                  try {
                    method.invoke(object);
                  } catch (InvocationTargetException e) {
                    JFLog.log(e.getCause());
                  } catch (Exception e) {
                    JFLog.log(e);
                  }
                }
              });
            } catch (Exception e) {
              JFLog.log(e);
            }
          } else {
            param = func.substring(idx+1);
            func = func.substring(0, idx);
            try {
              method = object.getClass().getMethod(func, String.class);
              EventQueue.invokeLater(new Runnable() {
                public void run() {
                  try {
                    method.invoke(object, param);
                  } catch (InvocationTargetException e) {
                    JFLog.log(e.getCause());
                  } catch (Exception e) {
                    JFLog.log(e);
                  }
                }
              });
            } catch (Exception e) {
              JFLog.log(e);
            }
          }
        }
      }
    }
  }

  public void callEDT(String func) {
    synchronized(lock) {
      funcs.add(func);
      lock.notifyAll();
    }
  }

  public void callEDT(String func, String param) {
    this.param = param;
    synchronized(lock) {
      funcs.add(func + "," + param);
      lock.notifyAll();
    }
  }

  public String callEDTreturn(String func) {
    synchronized(lockReturn) {
      retValue = "";
      synchronized(lock) {
        funcs.add("=" + func);
        lock.notifyAll();
      }
      try { lockReturn.wait(); } catch (Exception e) {}
      return retValue;
    }
  }
}
