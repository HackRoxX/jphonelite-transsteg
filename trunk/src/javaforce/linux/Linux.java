package javaforce.linux;

/**
 * Created : Mar 12, 2012
 *
 * @author pquiring
 */
import java.util.*;
import java.io.*;

import javaforce.*;

/**
 * Common functions for Linux administration.
 */
public class Linux {

  public static enum DistroTypes {

    Unknown, Ubuntu, Fedora
  };
  public static DistroTypes distro = DistroTypes.Unknown;

  /**
   * Detects Linux distribution type. (Support Ubuntu, Fedora currently)
   */
  public static boolean detectDistro() {
    if (distro != DistroTypes.Unknown) {
      return true;
    }
    try {
      //Ubuntu : /etc/lsb-release
      File lsb = new File("/etc/lsb-release");
      if (lsb.exists()) {
        FileInputStream fis = new FileInputStream(lsb);
        byte data[] = JF.readAll(fis);
        fis.close();
        String str = new String(data);
        if (str.indexOf("Ubuntu") != -1) {
          distro = DistroTypes.Ubuntu;
          return true;
        }
      }
      //Fedora : /etc/fedora-release
      File fedora = new File("/etc/fedora-release");
      if (fedora.exists()) {
        FileInputStream fis = new FileInputStream(fedora);
        byte data[] = JF.readAll(fis);
        fis.close();
        String str = new String(data);
        if (str.indexOf("Fedora") != -1) {
          distro = DistroTypes.Fedora;
          return true;
        }
      }
    } catch (Exception e) {
      JFLog.log(e);
    }
    return false;
  }

  /**
   * Creates folder as root
   */
  public static boolean mkdir(String folder) {
    ShellProcess sp = new ShellProcess();
    ArrayList<String> cmd = new ArrayList<String>();
    cmd.add("sudo");
    cmd.add("mkdir");
    cmd.add("-p");
    cmd.add(folder);
    String output = sp.run(cmd, false);
    if (output == null) {
      JFLog.log("Failed to exec mkdir");
      return false;
    }
    if (output.length() > 0) {
      JFLog.log("Error:" + output);
      return false;
    }
    return true;
  }

  /**
   * Copies src to dst as root
   */
  public static boolean copyFile(String src, String dst) {
    ShellProcess sp = new ShellProcess();
    ArrayList<String> cmd = new ArrayList<String>();
    cmd.add("sudo");
    cmd.add("cp");
    cmd.add(src);
    cmd.add(dst);
    String output = sp.run(cmd, false);
    if (output == null) {
      JFLog.log("Failed to exec cp");
      return false;
    }
    if (output.length() > 0) {
      JFLog.log("Error:" + output);
      return false;
    }
    return true;
  }

  /**
   * Creates Link to Target as root
   */
  public static boolean createLink(String target, String link) {
    ShellProcess sp = new ShellProcess();
    ArrayList<String> cmd = new ArrayList<String>();
    cmd.add("sudo");
    cmd.add("ln");
    cmd.add("-s");
    cmd.add(target);
    cmd.add(link);
    String output = sp.run(cmd, false);
    if (output == null) {
      JFLog.log("Failed to exec ln");
      return false;
    }
    if (output.length() > 0) {
      JFLog.log("Error:" + output);
      return false;
    }
    return true;
  }

  /**
   * Deletes file as root
   */
  public static boolean deleteFile(String file) {
    ShellProcess sp = new ShellProcess();
    ArrayList<String> cmd = new ArrayList<String>();
    cmd.add("sudo");
    cmd.add("rm");
    cmd.add("-f");
    cmd.add(file);
    String output = sp.run(cmd, false);
    if (output == null) {
      JFLog.log("Failed to exec ln");
      return false;
    }
    if (output.length() > 0) {
      JFLog.log("Error:" + output);
      return false;
    }
    return true;
  }

  /**
   * Restarts a service
   */
  public static boolean restartService(String name) {
    ShellProcess sp = new ShellProcess();
    ArrayList<String> cmd = new ArrayList<String>();
    cmd.add("sudo");
    cmd.add("service");
    cmd.add(name);
    cmd.add("restart");
    String output = sp.run(cmd, false);
    if (output == null) {
      JFLog.log("Failed to exec service");
      return false;
    }
    if (sp.getErrorLevel() != 0) {
      JFLog.log("Error:" + output);
      return false;
    }
    return true;
  }

  /**
   * Work with Ubuntu packages.
   */
  private static boolean apt(String action, String pkg, String desc) {
    JFTask task = new JFTask() {
      String action, pkg, desc;
      int lns, total;

      public boolean work() {
        setLabel((action.equals("install") ? "Installing " : "Removing ") + desc);
        ShellProcess sp = new ShellProcess();
        sp.removeEnvironmentVariable("TERM");
        sp.addEnvironmentVariable("DEBIAN_FRONTEND", "noninteractive");
//        sp.addResponse("Do you want to continue [Y/n]?", "y\n", true);
//        sp.addResponse("Install these packages without verification [y/N]?", "y\n", true);
        //    sp.log = true;
        sp.addListener(this);
        lns = 0;
        total = 25;  //TODO : how many lines of output is expected???
        String output = sp.run(new String[]{"sudo", "-E", "apt-get", "--yes", "--allow-unauthenticated", action, pkg}, true);
        //    sp.log = false;
        if (output == null) {
          setLabel("Failed to exec apt-get");
          JFLog.log("Failed to exec apt-get");
          return false;
        }
        if (output.indexOf("Unable to locate package") != -1) {
          setLabel("Package not found");
          JFLog.log("Package not found");
          return false;
        }
        setLabel("Complete");
        setProgress(100);
        return true;
      }

      public void shellProcessOutput(String out) {
        lns += (out.split("\n").length - 1);
        if (lns > total) {
          total = lns + 25;
        }
        setProgress(lns * 100 / total);
      }

      ;
      public JFTask init(String action, String pkg, String desc) {
        this.action = action;
        this.pkg = pkg;
        this.desc = desc;
        return this;
      }
    }.init(action, pkg, desc);
    new ProgressDialog(null, true, task).setVisible(true);
    return task.getStatus();
  }

  /**
   * Work with Fedora packages.
   */
  private static boolean yum(String action, String pkg, String desc) {
    JFTask task = new JFTask() {
      String action, pkg, desc;
      int lns, total;

      public boolean work() {
        setLabel((action.equals("install") ? "Installing " : "Removing ") + desc);
        ShellProcess sp = new ShellProcess();
        sp.removeEnvironmentVariable("TERM");  //prevent config dialogs
        sp.addResponse("Is this ok [y/N]?", "y\r\n", true);
        //    sp.log = true;
        sp.addListener(this);
        lns = 0;
        total = 25;  //TODO : how many lines of output is expected???
        String output = sp.run(new String[]{"sudo", "-E", "yum", action, pkg}, false);
        //    sp.log = false;
        if (output == null) {
          setLabel("Failed to exec yum");
          JFLog.log("Failed to exec yum");
          return false;
        }
        setLabel("Complete");
        setProgress(100);
        return true;
      }

      public void shellProcessOutput(String out) {
        lns += (out.split("\n").length - 1);
        if (lns > total) {
          total = lns + 25;
        }
        setProgress(lns * 100 / total);
      }

      ;
      public JFTask init(String action, String pkg, String desc) {
        this.action = action;
        this.pkg = pkg;
        this.desc = desc;
        return this;
      }
    }.init(action, pkg, desc);
    new ProgressDialog(null, true, task).setVisible(true);
    return task.getStatus();
  }

  public static boolean installPackage(String pkg, String desc) {
    detectDistro();
    switch (distro) {
      case Ubuntu:
        return apt("install", pkg, desc);
      case Fedora:
        return yum("install", pkg, desc);
    }
    return false;
  }

  public static boolean removePackage(String pkg, String desc) {
    detectDistro();
    switch (distro) {
      case Ubuntu:
        return apt("autoremove", pkg, desc);
      case Fedora:
        return yum("remove", pkg, desc);
    }
    return false;
  }

  /**
   * Sets file as executable as root
   */
  public static boolean setExec(String file) {
    ShellProcess sp = new ShellProcess();
    ArrayList<String> cmd = new ArrayList<String>();
    cmd.add("sudo");
    cmd.add("chmod");
    cmd.add("+x");
    cmd.add(file);
    String output = sp.run(cmd, false);
    if (output == null) {
      JFLog.log("Failed to exec chmod");
      return false;
    }
    if (output.length() > 0) {
      JFLog.log("Error:" + output);
      return false;
    }
    return true;
  }
  private static String pkgList[][];

  /*  public static String[][] getPackages() {
   if (dpkg == null) updateInstalled();
   return dpkg;
   }*/
  private static String[][] ubuntu_searchPackages(String regex) {
    ShellProcess sp = new ShellProcess();
    ArrayList<String> cmd = new ArrayList<String>();
    cmd.add("apt-cache");
    cmd.add("search");
    cmd.add(regex);
    String output = sp.run(cmd, false);
    if (output == null) {
      JFLog.log("Error:unable to execute apt-cache");
      return null;
    }
    String lns[] = output.split("\n");
    String ret[][] = new String[lns.length][2];
    String f[];
    for (int a = 0; a < lns.length; a++) {
      f = lns[a].split(" - ");
      if (f.length != 2) {
        continue;
      }
      ret[a][0] = f[0];  //package name
      ret[a][1] = f[1];  //package desc
    }
    return ret;
  }

  private static String[][] fedora_searchPackages(String regex) {
    ShellProcess sp = new ShellProcess();
    ArrayList<String> cmd = new ArrayList<String>();
    cmd.add("yum");
    cmd.add("search");
    cmd.add(regex);
    String output = sp.run(cmd, false);
    if (output == null) {
      JFLog.log("Error:unable to execute yum");
      return null;
    }
    String lns[] = output.split("\n");
    String ret[][] = new String[lns.length][2];
    String f[];
    for (int a = 0; a < lns.length; a++) {
      f = lns[a].split(" : ");
      if (f.length != 2) {
        continue;
      }
      ret[a][0] = f[0];  //package name
      ret[a][1] = f[1];  //package desc
    }
    return ret;
  }

  /**
   * Searches for available packages (NOTE:There may be nulls in the output)
   */
  public static String[][] searchPackages(String regex) {
    detectDistro();
    switch (distro) {
      case Ubuntu:
        return ubuntu_searchPackages(regex);
      case Fedora:
        return fedora_searchPackages(regex);
    }
    return null;
  }

  public static void ubuntu_updateInstalled() {
    ShellProcess sp = new ShellProcess();
    ArrayList<String> cmd = new ArrayList<String>();
    cmd.add("dpkg");
    cmd.add("-l");
    String output = sp.run(cmd, false);
    if (output == null) {
      JFLog.log("Error:unable to execute dpkg");
      return;
    }
    String lns[] = output.split("\n");
    pkgList = new String[lns.length - 5][3];
    String f[];
    for (int a = 5; a < lns.length; a++) {
      f = lns[a].split(" +", 4);  //greedy spaces
      pkgList[a - 5][0] = f[1];  //package name
      pkgList[a - 5][1] = f[3];  //package desc
      pkgList[a - 5][2] = (f[0].charAt(0) == 'i' ? "true" : "false");  //package installed?
    }
  }

  public static void fedora_updateInstalled() {
    //NOTE:can't use "rpm -qa" because the version # is mangled in the name
    ShellProcess sp = new ShellProcess();
    ArrayList<String> cmd = new ArrayList<String>();
    cmd.add("yum");
    cmd.add("list");
    cmd.add("installed");
    String output = sp.run(cmd, false);
    if (output == null) {
      JFLog.log("Error:unable to execute yum");
      return;
    }
    String lns[] = output.split("\n");
    pkgList = new String[lns.length][3];
    for (int a = 0; a < lns.length; a++) {
      String f[] = lns[a].split(" +");  //greedy spaces
      if (f.length != 3) {
        continue;
      }
      int idx = f[0].lastIndexOf(".");  //strip arch
      pkgList[a][0] = f[0].substring(0, idx);  //package name
      pkgList[a][1] = f[1];  //package desc
      pkgList[a][2] = "true";  //package installed?
    }
  }

  public static void updateInstalled() {
    detectDistro();
    switch (distro) {
      case Ubuntu:
        ubuntu_updateInstalled();
        break;
      case Fedora:
        fedora_updateInstalled();
        break;
    }
  }

  public static boolean isInstalled(String pkg) {
    if (pkgList == null) {
      updateInstalled();
    }
    if (pkg == null) {
      return true;
    }
    for (int a = 0; a < pkgList.length; a++) {
      if (pkgList[a][0].equals(pkg)) {
        return pkgList[a][2].equals("true");
      }
    }
    return false;
  }

  public static String getPackageDesc(String pkg) {
    if (pkgList == null) {
      updateInstalled();
    }
    if (pkg == null) {
      return "";
    }
    for (int a = 0; a < pkgList.length; a++) {
      if (pkgList[a][0].equals(pkg)) {
        return pkgList[a][1];
      }
    }
    return "";
  }

  public static boolean isMemberOf(String user, String group) {
    try {
      ShellProcess sp = new ShellProcess();
      String output = sp.run(new String[]{"groups", user}, false).replaceAll("\n", "");
      //output = "user : group1 group2 ..."
      String groups[] = output.split(" ");
      for (int a = 2; a < groups.length; a++) {
        if (groups[a].equals(group)) {
          return true;
        }
      }
      return false;
    } catch (Exception e) {
      JFLog.log(e);
      return false;
    }
  }

  private static String expandQuotes(String inString) {
    while (true) {
      int i1 = inString.indexOf('\"');
      if (i1 == -1) {
        return inString;
      }
      int i2 = inString.indexOf('\"', i1 + 1);
      if (i2 == -1) {
        return inString;
      }
      inString = inString.substring(0, i1) + inString.substring(i1 + 1, i2).replace(' ', '\u1234') + inString.substring(i2 + 1);
    }
  }

  private static String[] expandBackslash(String inString) {
    StringBuilder out = new StringBuilder();
    char inCA[] = inString.toCharArray();
    for (int a = 0; a < inCA.length; a++) {
      if (inCA[a] == '\\') {
        if (inCA[a + 1] == '\\') {
          if (inCA[a + 2] == '\\') {
            if (inCA[a + 3] == '\\') {
              out.append('\\');
              out.append('\\');
              a += 3;
              continue;
            }
          } else if (inCA[a + 2] == ' ') {
            out.append('\u1234');
            a += 2;
            continue;
          } else {
            out.append('\\');
            a++;
            continue;
          }
        }
      }
      out.append(inCA[a]);
    }
    String cmd[] = out.toString().split(" ");
    for (int a = 0; a < cmd.length; a++) {
      if (cmd[a].indexOf('\u1234') != -1) {
        cmd[a] = cmd[a].replace('\u1234', ' ');
      }
    }
    return cmd;
  }

  /**
   * Currently only supports %f,%F,%u,%U
   */
  public static String[] expandDesktopExec(String exec, String file) {
    if (file == null) {
      file = "";
    }
    file = '\"' + file + '\"';
    exec = exec.replaceAll("%f", file);
    exec = exec.replaceAll("%F", file);
    exec = exec.replaceAll("%u", file);
    exec = exec.replaceAll("%U", file);
    exec = expandQuotes(exec);
    return expandBackslash(exec);
  }

  /**
   * Currently only supports %f,%F,%u,%U
   */
  public static String[] expandDesktopExec(String exec, String file[]) {
    String files = "";
    if (file != null) {
      for (int a = 0; a < file.length; a++) {
        if (a > 0) {
          files += " ";
        }
        files += '\"' + file[a] + '\"';
      }
    } else {
      file = new String[1];
      file[0] = "";
    }
    exec = exec.replaceAll("%f", '\"' + file[0] + '\"');
    exec = exec.replaceAll("%F", files);
    exec = exec.replaceAll("%u", '\"' + file[0] + '\"');
    exec = exec.replaceAll("%U", files);
    exec = expandQuotes(exec);
    return expandBackslash(exec);
  }

  public static int detectBits() {
    if (new File("/usr/lib64").exists()) {
      return 64;
    }
    return 32;
  }

  /**
   * Runs a bash script as root
   */
  public static boolean runScript(String lns[]) {
    try {
      File tmpFile = File.createTempFile("script", ".sh", new File("/tmp"));
      FileOutputStream fos = new FileOutputStream(tmpFile);
      fos.write("#!/bin/bash\n".getBytes());
      for (int a = 0; a < lns.length; a++) {
        fos.write((lns[a] + "\n").getBytes());
      }
      fos.close();
      tmpFile.setExecutable(true);
      ShellProcess sp = new ShellProcess();
      String output = sp.run(new String[]{"sudo", tmpFile.getAbsolutePath()}, true);
      tmpFile.delete();
      return sp.getErrorLevel() == 0;
    } catch (Exception e) {
      JFLog.log(e);
      return false;
    }
  }
}
