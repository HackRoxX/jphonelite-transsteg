//Usage : openpty cmd [args]

//Desc : executes cmd in a *new* pty

//Author : Peter Quiring
//I have no idea why this simple program doesn't exist in Linux yet

//Created : Apr 29, 2012

//Version 1.0

//License : GPL

#include <errno.h>
#include <fcntl.h>
#include <grp.h>
#include <signal.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <termios.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <stdarg.h>
#include <pthread.h>

int masterFd, slaveFd;
const char *slavePtyName;
int argc;
char **argv;

void error(const char *msg, ...) {
  va_list lst;
  va_start(lst, msg);
  vprintf(msg, lst);
  va_end(lst);
  printf("\n");
  exit(1);
}

void runChild() {
  // A process relinquishes its controlling terminal when it creates a new session with the setsid(2) function.
  if (setsid() == -1) {
    error("setsid()");
  }
  slaveFd = open(slavePtyName, O_RDWR);
  if (slaveFd == -1) {
    error("open(%s, O_RDWR) failed - did you run out of pseudo-terminals?", slavePtyName);
  }
  close(masterFd);  //close masterFd in child process (inherited from parent)
  struct termios terminalAttributes;
  if (tcgetattr(slaveFd, &terminalAttributes) != 0) {
    error("tcgetattr() failed");
  }
  // Humans don't need XON/XOFF flow control of output, and it only serves to confuse those who accidentally hit ^S or ^Q, so turn it off.
  terminalAttributes.c_iflag &= ~IXON;
#if defined(IUTF8)
  // Assume input is UTF-8; this allows character-erase to be correctly performed in cooked mode.
  terminalAttributes.c_iflag |= IUTF8;
#endif
  terminalAttributes.c_cc[VERASE] = 127;
  if (tcsetattr(slaveFd, TCSANOW, &terminalAttributes) != 0) {
    error("tcsetattr() failed");
  }
  // Slave becomes stdin/stdout/stderr of child.
  if (slaveFd != STDIN_FILENO && dup2(slaveFd, STDIN_FILENO) != STDIN_FILENO) {
    error("dup2() stdin failed");
  }
  if (slaveFd != STDOUT_FILENO && dup2(slaveFd, STDOUT_FILENO) != STDOUT_FILENO) {
    error("dup2() stdout failed");
  }
  if (slaveFd != STDERR_FILENO && dup2(slaveFd, STDERR_FILENO) != STDERR_FILENO) {
    error("dup2() stderr failed");
  }
  signal(SIGINT, SIG_DFL);
  signal(SIGQUIT, SIG_DFL);
  signal(SIGCHLD, SIG_DFL);
  char *cmd = argv[1];
  //remove argv[0] (argv[argc] = NULL)
  int a;
  for(a=0;a<argc;a++) {
    argv[a] = argv[a+1];
  }
  execvp(cmd, argv);  //replaces current process (Note: argv[0] should be cmd as well)
  error("Failed to execute:%s", cmd);
}

void setTermWindowSize(int x, int y) {
  struct winsize size;
  size.ws_col = x;
  size.ws_row = y;
  size.ws_xpixel = x*8;
  size.ws_ypixel = y*8;
  if (ioctl(masterFd, TIOCSWINSZ, &size) < 0) {
    error("setTermWindowSize() failed");
  }
}

struct args {
  int read, write;
  int resize;  //monitor for special ANSI command to signal set terminal window size "ESC[x,y."
} arg1, arg2;

void relay(struct args *arg) {
  char buf[256];
  int len, x, y, ok, a, cc;
  char *comma;
  while (1) {
    len = read(arg->read, buf, 256);
    if (len <= 0) exit(0);
    if (arg->resize) {
      if ((len > 5) && (len < 11)) {  //ESC[xxx,yyy.
        if ((buf[0] == 0x1b) && (buf[1] == '[') && (buf[len-1] == '.')) {
          ok = 1;
          cc = 0;
          for(a=2;a<len-1;a++) {
            if (buf[a] == ',') {cc++; continue;}
            if ((buf[a] >= '0') && (buf[a] <= '9')) continue;
            ok = 0;
            break;
          }
          if ((ok) && (cc == 1)) {
            buf[len] = 0;
            comma = strchr(buf, ',');
            *comma = 0;
            comma++;
            buf[len-1] = 0;
            x = atoi(buf + 2);
            y = atoi(comma);
            setTermWindowSize(x, y);
            continue;  //don't send buf to child
          }
        }
      }
    }
    write(arg->write, buf, len);
  }
}

pthread_t p1, p2;

void runParent() {
  //read stdin and write to masterFd
  arg1.read = STDIN_FILENO;
  arg1.write = masterFd;
  arg1.resize = 1;
  pthread_create(&p1, NULL, (void *(*)(void*))&relay, &arg1);
  //read masterFd and write to stdout
  arg2.read = masterFd;
  arg2.write = STDOUT_FILENO;
  arg2.resize = 0;
  pthread_create(&p2, NULL, (void *(*)(void*))&relay, &arg2);
}

int status;

int main(int _argc, char **_argv) {
  if (_argc == 1) {
    printf("openpty/1.0\n");
    printf("Desc : Runs a program with a new pty.\n");
    printf("Usage : openpty cmd [args]\n");
    printf("Make sure you setup environment before calling (ie:TERM)\n");
    printf("To set terminal size send the follow ANSI command : \"ESC[x,y.\"\n");
    printf("Author : Peter Quiring\n");
    return;
  }
  argc = _argc;
  argv = _argv;
  masterFd = posix_openpt(O_RDWR | O_NOCTTY);
  if (masterFd == -1) {
    error("posix_openpt(O_RDWR | O_NOCTTY) failed");
  }

  slavePtyName = (const char*)ptsname(masterFd);
  if (slavePtyName == NULL) {
    error("ptsname(%d) failed", masterFd);
  }

  if (grantpt(masterFd) != 0) {
    error("grantpt(%d) failed", masterFd);
  }
  if (unlockpt(masterFd) != 0) {
    error("unlockpt(%d) failed", masterFd);
  }

  pid_t pid = fork();

  if (pid < 0) error("fork() failed");
  if (pid == 0)
    runChild();
  else
    runParent();
  wait(&status);

  return 0;
}
