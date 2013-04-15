package javaforce;

import java.util.*;

/**
 * <code>GlobaLPropertes</code> is designed for JSP pages that need to share
 * data between sessions/pages.<br> Place the generated .class file into
 * WEB-INF/classes/javaforce
 */
public class GlobalProperties {

  private Hashtable<String, Object> props;
  private static GlobalProperties instance;

  private GlobalProperties() {
    props = new Hashtable<String, Object>();
  }

  public static synchronized GlobalProperties getInstance() {
    if (instance == null) {
      instance = new GlobalProperties();
    }
    return instance;
  }

  public void set(String id, Object val) {
    if (val == null) {
      remove(id);
    } else {
      props.put(id, val);
    }
  }

  public Object get(String id) {
    return props.get(id);
  }

  public void remove(String id) {
    props.remove(id);
  }
}