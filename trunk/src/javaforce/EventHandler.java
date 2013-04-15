package javaforce;

import java.awt.Component;

/**
 * This class is used with the GUI class to perform event handling.
 *
 * This class is obsolete - use NetBeans IDE GUI Builder instead.
 *
 */
public interface EventHandler {

  public void event(Object id, int msg, int x, int y, int f1, int f2);
  //Key Events : f1 = KeyChar, f2 = KeyCode
  //Mouse Events : f1 = Button f2 = Modifiers
}
