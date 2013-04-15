import javaforce.voip.*;
import java.awt.*;

/** Keeps track of each line. */

public class PhoneLine {
  public boolean unauth, auth;
  public boolean noregister;
  public boolean incall;   //INVITE (outbound)
  public boolean trying;   //100 trying
  public boolean ringing;  //180 ringing
  public boolean talking;  //200 ok

  public String user;

  public boolean incoming; //INVITE (inbound)

  public String dial, status;
  public String callid;  //Call-ID in SIP header (not callerid)
  public String to;  //remote number
  public String callerid;  //TEXT name of person calling

  public SIPClient sip;

  public RTP rtp, Vrtp;
  public String remotertphost;
  public int remotertpport;  //audio
  public int remoteVrtpport;  //video

  public Codec codecs[];  //codecs for incoming INVITE
  public Coder coder;  //selected codec [en]coder

  public int clr = -1;

  public boolean xfer,hld,dnd,cnf;

  public short samples[] = new short[160];  //used in conference mode only

  //RFC 2833 - DTMF
  public char dtmf = 'x';
  public boolean dtmfend = false;

  public boolean msgwaiting = false;

  public PhoneLine() {
    dial = "";
    status = "";
    callid = "";
  }
}
