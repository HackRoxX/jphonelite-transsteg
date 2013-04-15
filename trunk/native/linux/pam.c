#include <security/pam_appl.h>
#include <sys/types.h>
#include <stdbool.h>
#include <memory.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include "javaforce_jni_LnxAPI.h"

/* Native Code for pam auth
 *
 * For NetBeans editor:
 * Tools -> Options -> C/C++ Tab -> Code Assistance Tab -> C Compiler Tab -> Include Directories:
 *   /usr/include/gstreamer-1.0
 *   /usr/include/glib-2.0
 *   /usr/lib/jvm/java-7-openjdk-i386/include
 *   /usr/lib/i386-linux-gnu/glib-2.0/include
 *   /usr/include/libxml2
 *   /usr/include
 */

// Created : Mar 24, 2012

//from LXDE (lxdm/lxdm.c)
static char *user_pass[2];
static int do_conv(int num, const struct pam_message **msg,struct pam_response **resp, void *arg)
{
  int result = PAM_SUCCESS;
  int i;
  *resp = (struct pam_response *) calloc(num, sizeof(struct pam_response));
  for(i=0;i<num;i++)
  {
    //printf("MSG: %d %s\n",msg[i]->msg_style,msg[i]->msg);
    switch(msg[i]->msg_style){
    case PAM_PROMPT_ECHO_ON:
      resp[i]->resp=strdup(user_pass[0]?user_pass[0]:"");
      break;
    case PAM_PROMPT_ECHO_OFF:
      resp[i]->resp=strdup(user_pass[1]?user_pass[1]:"");
      break;
    case PAM_ERROR_MSG:
    case PAM_TEXT_INFO:
      //printf("PAM: %s\n",msg[i]->msg);
      break;
    default:
      break;
    }
  }
  return result;
}
static struct pam_conv conv;
static pam_handle_t *pamh;

JNIEXPORT jboolean JNICALL Java_javaforce_jni_LnxAPI_authUser
  (JNIEnv *env, jclass cls, jstring juser, jstring jpass)
{
  char *user = (char*)(*env)->GetStringUTFChars(env, juser, NULL);
  char *pass = (char*)(*env)->GetStringUTFChars(env, jpass, NULL);
  int ret;
  conv.conv=do_conv;
  conv.appdata_ptr=user_pass;
  ret = pam_start("passwd", user, &conv, &pamh);
  if (ret != PAM_SUCCESS) {
    (*env)->ReleaseStringUTFChars(env, juser, user);
    (*env)->ReleaseStringUTFChars(env, jpass, pass);
    return 0;
  }
  user_pass[0]=user;user_pass[1]=pass;
  ret = pam_authenticate(pamh,PAM_SILENT);
  user_pass[0]=0;user_pass[1]=0;
  pam_end(pamh,0);
  (*env)->ReleaseStringUTFChars(env, juser, user);
  (*env)->ReleaseStringUTFChars(env, jpass, pass);
  return (ret == PAM_SUCCESS);
}
