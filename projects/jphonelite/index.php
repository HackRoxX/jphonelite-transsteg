<html>
  <head>
    <link rel=stylesheet href="style.css" type="text/css">
    <title>jPhoneLite</title>
  </head>
  <body>
  <div class=title>
    <div class=title_content_center>jPhoneLite</div>
    <div class=title_content_left><a href="http://pquiring.homedns.org"><img src=/img/homepage.png></a></div>
  </div>
  <div class=content>

<center>
jphonelite is a Java SIP VoIP SoftPhone for computers.<br>
Features 6 lines with transfer, hold, conference (up to all 6 lines), contact list, recent calls, g711u/a, g729a and RFC 2833, ringtone.<br>
Tested with Asterisk PBX and callcentric.com.<br>
<br>
jPhoneLite is also available for the Android System.<br>
<br>
<br>
<a href="http://sourceforge.net/projects/jphonelite">Project Page</a>
 <img valign="center" src="/img/vr.gif" width=2 height=12>
<a href="http://sourceforge.net/projects/jphonelite/files">Download</a>
 <img valign="center" src="/img/vr.gif" width=2 height=12>
<a href="jphonelite.jnlp">Run (jnlp)</a>
 <img valign="center" src="/img/vr.gif" width=2 height=12>
<a href="applet.php">Run Applet</a>
 <img valign="center" src="/img/vr.gif" width=2 height=12>
<a href="jphonelite-javascript.html">Run Applet (javascript)</a>
<br>
<a href="desktop-whatsnew.txt">Desktop ChangeLog</a>
 <img valign="center" src="/img/vr.gif" width=2 height=12>
<a href="android-whatsnew.txt">Android ChangeLog</a>
<br>
<br>
<br>
</center>
  </div>

  <div class=title>
    <div class=title_content_center>FAQ</div>
    <div class=title_content_left></div>
  </div>
  <div class=content>
Q : How do I configure the settings?<br>
A : Press the CFG (config) button.<br>
<br>
Q : What's the Phone Speaker mode (SPK button)?<br>
A : Speaker phone mode is when your not using a headset.  The phone will operate in half-duplex mode.  When the inbound sound is over the threshold,
your mic is muted until the inbound sound is lower that the threshold and will remain muted for the delay duration, to avoid feedback (echo).<br>
<br>
Q : How do I compile it?<br>
A : Just run build.xml in the root path and then in /projects/jphonelite.<br>
<br>
Q : Why do I get the error "No compatible sound" found when I use Windows System?<br>
A : You probably don't have a microphone (or speaker) attached.<br>
<br>
Q : What are the mini/micro editions?<br>
A : <span>These editions are applets only designed for websites.<br>
The user is forced to dial a number specified in the php files.<br>
Great for online calling card systems or what ever else you can think of.<br>
See the php files for more info.<br>
The best way to use this with Asterisk is:<br>
  - create a configuration without a password (non-registered mode)<br>
  - configure Asterisk to accept anonymous inbound SIP connections<br>
  - configure a DID in Asterisk to goto your calling card system (ie: astcc) or IVR<br>
<br></span>
  </div>

  </body>
</html>

<script type="text/javascript" src="/style.js"></script>
