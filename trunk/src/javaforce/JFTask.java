package javaforce;

/**
 * Created : Mar 12, 2012
 *
 * @author pquiring
 */
import javax.swing.*;

/**
 * A special Thread (task) for ProgressDialog.<br> You should override work();
 */
public abstract class JFTask extends Thread implements ShellProcessListener {

  private ProgressDialog pd;
  private boolean status;
  public volatile boolean abort = false;

  public void start(ProgressDialog pd) {
    this.pd = pd;
    start();
  }

  public void run() {
    status = work();
    pd.done();
  }

  public void abort() {
    abort = true;
  }

  public void dispose() {
    pd.dispose();
  }

  public abstract boolean work();

  public boolean getStatus() {
    return status;
  }

  public void setLabel(String txt) {
    pd.setLabel(txt);
  }

  public void setTitle(String txt) {
    pd.setTitle(txt);
  }

  public void setProgress(int value) {
    pd.setProgress(value);
  }

  public void shellProcessOutput(String out) {
  }
;  //you can override
}
