#include <sys/types.h>
#include <X11/X.h>
#include <X11/Xlib.h>
#include <X11/Xatom.h>
#include <stdbool.h>
#include <memory.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include "javaforce_jni_LnxAPI.h"

/* Native Code for X11 */

static Window windowWithName(Display *dpy, Window top, const char *name)
{
  Window *children, dummy;
  unsigned int nchildren;
  unsigned int i;
  Window w = 0;
  char *window_name;

  if (XFetchName(dpy, top, &window_name) && !strcmp(window_name, name)) return top;

  if (!XQueryTree(dpy, top, &dummy, &dummy, &children, &nchildren)) return 0;

  for (i = 0; i < nchildren; i++)
  {
    w = windowWithName(dpy, children[i], name);
    if (w) break;
  }
  if (children) XFree((char *) children);
  return (w);
}

JNIEXPORT jint JNICALL Java_javaforce_jni_LnxAPI_x11_1get_1id
  (JNIEnv *env, jclass cls, jstring _title)
{
  const char *title = (*env)->GetStringUTFChars(env,_title,NULL);

  Display *display = XOpenDisplay(NULL);
  int screen = DefaultScreen(display);
	Window rootwin = RootWindow(display, screen);
  int ret = windowWithName(display, rootwin, title);

  (*env)->ReleaseStringUTFChars(env, _title, title);

  XCloseDisplay(display);

  return ret;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_x11_1set_1desktop
  (JNIEnv *env, jclass cls, jint id)
{
  Display *disp = XOpenDisplay(NULL);

  int a;
  for(a=0;a<2;a++) {
    Atom state = XInternAtom(disp, "_NET_WM_STATE", false);
    Atom state_atoms[] = {
      XInternAtom(disp, "_NET_WM_STATE_BELOW", false),
      XInternAtom(disp, "_NET_WM_STATE_SKIP_PAGER", false),
      XInternAtom(disp, "_NET_WM_STATE_SKIP_TASKBAR", false),
      XInternAtom(disp, "_NET_WM_STATE_STICKY", false)
    };
    XChangeProperty(disp, id, state, XA_ATOM, 32, PropModeReplace, (unsigned char *)(state_atoms), 4);

    Atom type = XInternAtom(disp, "_NET_WM_WINDOW_TYPE", false);
    Atom type_atoms = XInternAtom(disp, "_NET_WM_WINDOW_TYPE_DESKTOP", false);
    XChangeProperty(disp, id, type, XA_ATOM, 32, PropModeReplace, (unsigned char *)(&type_atoms), 1);
  }

  XCloseDisplay(disp);

  return 1;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_x11_1set_1dock
  (JNIEnv *env, jclass cls, jint id)
{
  Display *disp = XOpenDisplay(NULL);

  int a;
  for(a=0;a<2;a++) {
    Atom state = XInternAtom(disp, "_NET_WM_STATE", false);
    Atom state_atoms[] = {
      XInternAtom(disp, "_NET_WM_STATE_ABOVE", false),
      XInternAtom(disp, "_NET_WM_STATE_SKIP_PAGER", false),
      XInternAtom(disp, "_NET_WM_STATE_SKIP_TASKBAR", false),
      XInternAtom(disp, "_NET_WM_STATE_STICKY", false)
    };
    XChangeProperty(disp, id, state, XA_ATOM, 32, PropModeReplace, (unsigned char *)(state_atoms), 4);

    Atom type = XInternAtom(disp, "_NET_WM_WINDOW_TYPE", false);
    Atom type_atoms = XInternAtom(disp, "_NET_WM_WINDOW_TYPE_DOCK", false);
    XChangeProperty(disp, id, type, XA_ATOM, 32, PropModeReplace, (unsigned char *)(&type_atoms), 1);
  }

  XCloseDisplay(disp);

  return 1;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_x11_1set_1strut
  (JNIEnv *env, jclass cls, jint id, jint panelHeight, jint x, jint y, jint width, jint height)
{
  Display *disp = XOpenDisplay(NULL);
  int values[12];

  Atom strut = XInternAtom(disp, "_NET_WM_STRUT_PARTIAL", false);
  values[0] = 0;  //left
  values[1] = 0;  //right
  values[2] = 0;  //top
  values[3] = panelHeight;   //bottom
  values[4] = 0;  //left_start_y
  values[5] = height-1;  //left_end_y
  values[6] = 0;  //right_start_y
  values[7] = height-1;  //right_end_y
  values[8] = 0;  //top_start_x
  values[9] = width-1;  //top_end_x
  values[10] = 0;  //bottom_start_x
  values[11] = width-1;  //bottom_end_x
  XChangeProperty(disp, id, strut, XA_CARDINAL, 32, PropModeReplace, (unsigned char *)(&values), 12);

  XCloseDisplay(disp);

  return 1;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_x11_1set_1workarea
  (JNIEnv *env, jclass cls, jint id, jint x, jint y, jint width, jint height)
{
  Display *disp = XOpenDisplay(NULL);
  int values[4];

  Atom workarea = XInternAtom(disp, "_NET_WORKAREA", false);
  int root = XDefaultRootWindow(disp);
  values[0] = x;
  values[1] = y;
  values[2] = width;
  values[3] = height;
  XChangeProperty(disp, root, workarea, XA_CARDINAL, 32, PropModeReplace, (unsigned char *)(&values), 4);

  XCloseDisplay(disp);

  return 1;
}

JNIEXPORT jint JNICALL Java_javaforce_jni_LnxAPI_x11_1keysym_1to_1keycode
  (JNIEnv *env, jclass cls, jchar keysym)
{
  Display *disp = XOpenDisplay(NULL);
  int keycode = XKeysymToKeycode(disp, keysym);
  XCloseDisplay(disp);
  switch (keysym) {
    case '!':
    case '@':
    case '#':
    case '$':
    case '%':
    case '^':
    case '&':
    case '*':
    case '"':
    case ':':
      keycode |= 0x100;
  }
  return keycode;
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_x11_1send_1event
  (JNIEnv *env, jclass cls, jint keycode, jboolean down)
{
  Display *disp = XOpenDisplay(NULL);

  Window x11id;
  int revert;
  XGetInputFocus(disp, &x11id, &revert);

  XKeyEvent event;

  memset(&event, 0, sizeof(event));
  event.type = (down ? KeyPress : KeyRelease);
  event.keycode = keycode & 0xff;
  event.display = disp;
  event.window = x11id;
  event.root = XDefaultRootWindow(disp);
  event.subwindow = None;
  event.time = CurrentTime;
  event.x = 1;
  event.y = 1;
  event.x_root = 1;
  event.y_root = 1;
  event.same_screen = True;
  if (keycode & 0x100) event.state = ShiftMask;

  int status = XSendEvent(event.display, event.window, True, KeyPressMask, (XEvent*)&event);

  XCloseDisplay(disp);

  return status != 0;
}

//system tray code

static Display *tray_display;
static Window tray_window;
static volatile int tray_active;
static Atom tray_opcode, tray_data;
static jmethodID tray_method;
static jclass tray_cls;
static jobject tray_obj;
static JNIEnv *tray_env;
static int tray_count = 0;
#define MAX_TRAY_ICONS 64
static Window tray_icons[MAX_TRAY_ICONS];
static int screen_width;

static void tray_move_icons() {
  int a, x = 2, y = 2;
  for(a=0;a<MAX_TRAY_ICONS;a++) {
    if (tray_icons[a] == 0) continue;
    XMoveWindow(tray_display, tray_icons[a], x, y);
    if (y == 2) {
      y += 23 + 2;
    } else {
      y = 2;
      x += 23 + 2;
    }
  }
  //reposition/resize tray window
  int cols = (tray_count + 1) / 2;
  if (cols == 0) cols = 1;
  XMoveResizeWindow(tray_display, tray_window, screen_width - cols * (23+2) - 2 - 5 , 5
    , cols * (23+2) + 2, 52);
}

static void tray_add_icon(Window w) {
  if (tray_count == MAX_TRAY_ICONS) return;  //ohoh
  tray_count++;
  int a;
  for(a=0;a<MAX_TRAY_ICONS;a++) {
    if (tray_icons[a] == 0) {
      tray_icons[a] = w;
      break;
    }
  }
  XReparentWindow(tray_display, w, tray_window, 0, 0);
  tray_move_icons();
  XMapWindow(tray_display, w);
  (*tray_env)->CallNonvirtualVoidMethod(tray_env, tray_obj, tray_cls, tray_method, 1, tray_count);
}

/* Tray opcode messages from System Tray Protocol Specification
 * http://freedesktop.org/Standards/systemtray-spec/systemtray-spec-0.2.html */
#define SYSTEM_TRAY_REQUEST_DOCK    0
#define SYSTEM_TRAY_BEGIN_MESSAGE   1
#define SYSTEM_TRAY_CANCEL_MESSAGE  2

static void tray_client_message(XClientMessageEvent ev) {
  if (ev.message_type == tray_opcode) {
    switch (ev.data.l[1]) {
      case SYSTEM_TRAY_REQUEST_DOCK:
        tray_add_icon(ev.data.l[2]);
        break;
      case SYSTEM_TRAY_BEGIN_MESSAGE:
        break;
      case SYSTEM_TRAY_CANCEL_MESSAGE:
        break;
    }
  }
}

static void tray_remove_icon(XDestroyWindowEvent ev) {
  int a;
  for(a=0;a<MAX_TRAY_ICONS;a++) {
    if (tray_icons[a] == ev.window) {
      tray_icons[a] = 0;
      tray_count--;
      tray_move_icons();
      (*tray_env)->CallNonvirtualVoidMethod(tray_env, tray_obj, tray_cls, tray_method, 2, tray_count);
      break;
    }
  }
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_x11_1tray_1main
  (JNIEnv *env, jclass cls, jclass cb_cls, jobject cb_obj, jint pid, jint width)
{
  XEvent ev;

  int a;
  for(a=0;a<MAX_TRAY_ICONS;a++) {
    tray_icons[a] = 0;
  }

  tray_method = (*env)->GetMethodID(env, cb_cls, "trayEvent", "(II)V");
  tray_cls = cb_cls;
  tray_obj = cb_obj;
  tray_env = env;
  screen_width = width;

  tray_display = XOpenDisplay(NULL);

  Atom tray_atom = XInternAtom(tray_display, "_NET_SYSTEM_TRAY_S0", False);
  tray_opcode = XInternAtom(tray_display, "_NET_SYSTEM_TRAY_OPCODE", False);
  tray_data = XInternAtom(tray_display, "_NET_SYSTEM_TRAY_MESSAGE_DATA", False);

 	tray_window = XCreateSimpleWindow(
    tray_display,
    pid,
    width - 23 - 4 - 5, 5,  //pos
    23 + 4, 52,  //size
    1,  //border_width
    0xcccccc,  //border clr
    0xdddddd);  //backgnd clr

	XSetSelectionOwner(tray_display, tray_atom, tray_window, CurrentTime);

  //get DestroyNotify events
  XSelectInput(tray_display, tray_window, SubstructureNotifyMask);

  XMapWindow(tray_display, tray_window);

  tray_active = True;
  while (tray_active) {
    XNextEvent(tray_display, &ev);
    switch (ev.type) {
			case ClientMessage:
        tray_client_message(ev.xclient);
        break;
			case DestroyNotify:
        tray_remove_icon(ev.xdestroywindow);
        break;
    }
  }

  XCloseDisplay(tray_display);
}

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_x11_1tray_1stop
  (JNIEnv *env, jclass cls)
{
  tray_active = False;
  //TODO : send a message to tray_window to cause main() loop to abort
}
