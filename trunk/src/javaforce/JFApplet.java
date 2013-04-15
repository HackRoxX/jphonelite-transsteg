package javaforce;

import javax.swing.JApplet;
import java.net.URL;

/**
 * Base class for creating Java Applets. Includes some helpfull methods.
 *
 * @author Peter Quiring
 */
public class JFApplet extends JApplet {
//Applet functions

  public boolean redir(String url, String frame) {
    try {
      getAppletContext().showDocument(new URL(url), frame);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public boolean redir(String url) {
    try {
      getAppletContext().showDocument(new URL(url));
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public boolean link(String main, String nav) {
    if (!redir(nav, "nav")) {
      return false;
    }
    return redir(main, "main");
  }
}
