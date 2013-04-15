package javaforce;

import java.io.*;
import java.net.*;

/*
 //22 bytes
 struct FONT_HEADER {
 uint8 head[4];   //='FNT',26
 Version ver;     //version info (8bytes) [ignored]
 uint16 x, y;     //char size
 uint8 bpp;       //bits / pixel
 uint8 flgs;      //flags
 uint32 kc;       //KeyColor (transparent) only for coloured fonts
 };
 */
/**
 * Class to hold one font file that is proprietary to the DigiForce SDK. These
 * fonts are bitmap based and very simple. JFImage supports normal Java Font's.
 *
 * @author Peter Quiring
 */
public class JFFont {

  private final int FONT_FLG_MONO = 0x01;  //bit packed font
  private final int FONT_FLG_KEYCLR = 0x02;
  private int pixels[];
  private boolean mono;
  private boolean usekeyclr;
  private int clr;
  private int keyclr;
  private int TabSpaces;
  private int flgs;
  private int charsiz;
  public int x, y;

  public JFFont() {
    pixels = null;
    clr = 31;
    keyclr = 0;
    TabSpaces = 2;
  }

  ;

  public void unLoad() {
    pixels = null;
  }

  private int read8(InputStream in) {
    int ret;
    try {
      ret = in.read() & 0xff;
    } catch (Exception e) {
      return -1;
    }
    return ret;
  }

  private int read16(InputStream in) {
    byte buf[] = new byte[2];
    try {
      if (in.read(buf) != 2) {
        return -1;
      }
    } catch (Exception e) {
      return -1;
    }
    return (int) (((short) buf[1] & 0xff) << 8 | ((short) buf[0] & 0xff));
  }

  private int read32(InputStream in) {
    byte buf[] = new byte[4];
    try {
      if (in.read(buf) != 4) {
        return -1;
      }
    } catch (Exception e) {
      return -1;
    }
    return (((int) buf[3] & 0xff) << 24 | ((int) buf[2] & 0xff) << 16 | ((int) buf[1] & 0xff) << 8 | (int) buf[0] & 0xff);
  }

  private void skip(InputStream in, int bytes) {
    //NOTE : Some InputStream do not support skip() so read() and discard instead
    byte tmp[] = new byte[bytes];
    try {
      in.read(tmp);
    } catch (Exception e) {
    }
  }

  public boolean load(InputStream in) {
    unLoad();

    //Load Header (and verify it)
    int magic = read32(in);
    if (magic != 0x1a544e46) {
      unLoad();
      return false;
    }  //not a FNT file
    skip(in, 8);
    x = read16(in);
    y = read16(in);
    if ((x < 1) || (x > 1024) || (y < 1) || (y > 1024)) {
      unLoad();
      return false;
    }  //not sane
    int bpp = read8(in);
    flgs = read8(in);
    usekeyclr = ((flgs & FONT_FLG_KEYCLR) == FONT_FLG_KEYCLR);
    keyclr = read32(in);
    int totalsiz;
    byte buf[];
    switch (bpp) {
      case 1:
        if ((flgs & FONT_FLG_MONO) == 0) {
          unLoad();
          return false;
        }  //bad font
        mono = true;
        charsiz = (x * y + 7) >> 3;
        totalsiz = charsiz * 256;
        buf = new byte[totalsiz];
        try {
          in.read(buf);
        } catch (Exception e) {
          unLoad();
          return false;
        }
        pixels = new int[totalsiz];
        for (int a = 0; a < totalsiz; a++) {
          pixels[a] = buf[a] & 0xff;
        }
        break;
      case 24:
        if ((flgs & FONT_FLG_MONO) == FONT_FLG_MONO) {
          unLoad();
          return false;
        }  //bad font
        mono = false;
        charsiz = (x * y * 4);
        totalsiz = charsiz * 256;
        buf = new byte[totalsiz];
        try {
          in.read(buf);
        } catch (Exception e) {
          unLoad();
          return false;
        }
        pixels = new int[x * y * 256];
        for (int a = 0; a < x * y * 256; a++) {
          pixels[a] = buf[a * 4 + 0] & 0xff;
          pixels[a] <<= 8;
          pixels[a] |= buf[a * 4 + 1] & 0xff;
          pixels[a] <<= 8;
          pixels[a] |= buf[a * 4 + 2] & 0xff;
          pixels[a] <<= 8;
          pixels[a] |= buf[a * 4 + 3] & 0xff;
          pixels[a] <<= 8;
        }
        break;
      default:
        unLoad();
        return false;  //unsupported font
    }

    return true;
  }

  public boolean load(File file) {
    try {
      return load(new FileInputStream(file));
    } catch (Exception e) {
      unLoad();
      return false;
    }
  }

  public boolean load(URL url) {
    try {
      HttpURLConnection huc = (HttpURLConnection) url.openConnection();
      huc.setRequestMethod("GET");
      huc.connect();
      if (huc.getResponseCode() != HttpURLConnection.HTTP_OK) {
        return false;
      }
      InputStream is = huc.getInputStream();
      return load(is);
    } catch (Exception e) {
      return false;
    }
  }

  private void print_clr(JFImage img, int _x, int _y, String str, boolean vert) {
    int ch;
    int size = str.length();
    for (int a = 0; a < size; a++) {
      ch = str.charAt(a) & 0xff;
      img.putPixels(pixels, _x, _y, x, y, ch * x * y);
      if (ch == 0x09) {
        if (vert) {
          _y += y * TabSpaces;
        } else {
          _x += x * TabSpaces;
        }
      } else {
        if (vert) {
          _y += y;
        } else {
          _x += x;
        }
      }
    }
  }

  private void print_clrkeyclr(JFImage img, int _x, int _y, String str, boolean vert) {
    int ch;
    int size = str.length();
    for (int a = 0; a < size; a++) {
      ch = str.charAt(a) & 0xff;
      img.putPixelsKeyClr(pixels, _x, _y, x, y, ch * x * y, keyclr);
      if (ch == 0x09) {
        if (vert) {
          _y += y * TabSpaces;
        } else {
          _x += x * TabSpaces;
        }
      } else {
        if (vert) {
          _y += y;
        } else {
          _x += x;
        }
      }
    }
  }

  private void print_mono(JFImage img, int _x, int _y, String str, boolean vert) {
    int cx = _x, cy = _y;   //current x,y
    int xc, yc;         //x,y count
    int bo;             //bit offset
    int offset;         //pixels offset

    int ch;
    int size = str.length();
    for (int a = 0; a < size; a++) {
      ch = str.charAt(a) & 0xff;
      bo = 0x1;
      offset = ch * charsiz;
      yc = y;
      for (cy = _y; yc > 0; yc--, cy++) {
        xc = x;
        for (cx = _x; xc > 0; xc--, cx++) {
          if ((pixels[offset] & bo) != 0) {
            img.putPixel(cx, cy, clr);
          }
          bo <<= 1;
          if (bo == 0x100) {
            bo = 0x1;
            offset++;
          }
        }
      }
      if (ch == 0x09) {
        if (vert) {
          _y += y * TabSpaces;
        } else {
          _x += x * TabSpaces;
        }
      } else {
        if (vert) {
          _y += y;
        } else {
          _x += x;
        }
      }
    }
  }

  public void print(JFImage img, int x, int y, String str, boolean vert) {
    if (mono) {
      print_mono(img, x, y, str, vert);
      return;
    }
    if (usekeyclr) {
      print_clrkeyclr(img, x, y, str, vert);
      return;
    }
    print_clr(img, x, y, str, vert);
  }

  public void print(JFImage img, int x, int y, String str) {
    print(img, x, y, str, false);
  }

  public void setClr(int clr) {
    this.clr = clr;
  }
}
