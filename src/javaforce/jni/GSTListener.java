package javaforce.jni;

/**
 * Created : Mar 25, 2012
 *
 * @author pquiring
 */
public interface GSTListener {

  /**
   * Callback for gsteamer bus events.<br> NOTE : Due to current bugs event()
   * can only access 'static' members for now.
   */
  public void event(int id, int msg, String str);
}
