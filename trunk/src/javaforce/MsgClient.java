package javaforce;

import java.net.*;
import javax.swing.SwingUtilities;
import java.io.*;

/**
 * MsgClient is a TCP/IP based messaging system designed for use with the
 * DigiForce SDK(C++). This is client side. The server side is only available in
 * C++.
 *
 * @author Peter Quiring
 */
public class MsgClient {

  Socket s;
  InputStream is;
  OutputStream os;
  volatile boolean read_avail;
  byte[] read_data;
  byte[] read_length_bytes;
  int read_length;
  Runnable callback;

  public MsgClient(Runnable cb) {
    callback = cb;
  }

  private class Reader extends Thread {

    public void run() {
      read_length_bytes = new byte[4];
      while (true) {
        while (read_avail) {
          Thread.yield();
        }
        //read packet length (int little endian)
        read_length = 0;
        while (read_length != 4) {
          try {
            read_length += is.read(read_length_bytes, read_length, 4 - read_length);
          } catch (Exception e) {
            return;
          }
        }
        read_length = (int) read_length_bytes[0] & 0xff;
        read_length += ((int) read_length_bytes[1] & 0xff) << 8;
        read_length += ((int) read_length_bytes[2] & 0xff) << 16;
        read_length += ((int) read_length_bytes[3] & 0xff) << 24;
        //read read_length
        int have_read = 0;
        int read;
        read_data = new byte[read_length];
        while (have_read != read_length) {
          try {
            read = is.read(read_data, have_read, read_length - have_read);
            if (read < 0) {
              //ERROR?
            } else {
              have_read += read;
            }
          } catch (Exception e) {
            return;
          }
        }
        read_avail = true;
        callback.run();
      }
    }
  };
  Reader reader;

  public boolean connect(String host, int port) {
    read_avail = false;
    try {
      s = new Socket(host, port);
      is = s.getInputStream();
      os = s.getOutputStream();
    } catch (Exception e) {
      return false;
    }

    reader = new Reader();
    reader.start();

    return true;
  }

  public boolean isConnected() {
    if (s == null) {
      return false;
    }
    return s.isConnected();
  }

  public boolean write(byte[] data) {
    //write a packet
    int len = data.length;
    byte[] packet = new byte[len + 4];
    packet[0] = (byte) (len & 0xff);
    packet[1] = (byte) ((len & 0xff00) >> 8);
    packet[2] = (byte) ((len & 0xff0000) >> 16);
    packet[3] = (byte) ((len & 0x7f000000) >> 24);
    for (int a = 0; a < len; a++) {
      packet[a + 4] = data[a];
    }
    try {
      os.write(packet);
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  public byte[] read() {
    if (!read_avail) {
      return null;
    }
    return read_data;
  }

  public void clear() {
    read_avail = false;
  }

  public void close() {
    read_avail = false;
    if (s == null) {
      return;
    }
    try {
      s.close();
    } catch (Exception e) {
    }
    s = null;
    if (reader == null) {
      return;
    }
    while (reader.isAlive()) {
      Thread.yield();
    }
    reader = null;
    read_avail = false;
  }
};
