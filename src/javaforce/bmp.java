package javaforce;

//BMP - M$-Windows Bitmap file format
import javaforce.*;
import java.awt.Dimension;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Internal class to provide BMP file support.
 *
 * @author Peter Quiring
 */
public class bmp {
  /*/
   struct _bmp_main {   //18 bytes
   char sig[2];       //signature = 'BM'
   uint32 filesize;   //entire file size
   uint8 junk[4];
   uint32 bitoffset;  //offset of bits
   uint32 siz;        //1st element of win/os2  (to detect which one it is)
   };   //main header  (always 1st)

   // * = ignored!
   struct _bmp_win {   //36       //Win 3.0 BMP info block (2nd part if Win 3.0)
   //  uint32 siz;    //40
   uint32 x;
   uint32 y;
   uint16 planes;  //1    //*
   uint16 bpp;
   uint32 comp;    //0=none  1=RLE8  2=RLE4
   uint32 size_image;    //*
   uint32 xpm;           //*
   uint32 ypm;           //*
   uint32 clrused;       // must be 0
   uint32 clrimp;        //*
   };

   struct _bmp_os2 {  //8        //OS/2 info block (2nd part if OS/2)
   //  uint32 siz;    //12
   uint16 x;       //DF/1.2.2 - this was uint32
   uint16 y;
   uint16 planes;  //1    *
   uint16 bpp;
   };
   /*/

  protected static int[] load(InputStream in, Dimension size) {
    int a;
    int clrs;
    int bmp_x, bmp_y, bmp_bpp, bmp_bypp;
    int fs;
    int bo;
    int siz;
    int ret[];

    //read signature
    try {
      if (in.read() != 'B') {
        return null;
      }
      if (in.read() != 'M') {
        return null;
      }
    } catch (Exception e) {
      return null;
    }

    fs = JF.readuint32(in);
    a = JF.readuint32(in);  //junk
    bo = JF.readuint32(in);
    siz = JF.readuint32(in);

    switch (siz) {
      case 14:    //OS/2
        bmp_x = JF.readuint16(in);
        bmp_y = JF.readuint16(in);
        a = JF.readuint16(in);  //planes - ignore
        if (JF.readuint16(in) != 24) {
          return null;
        }
        break;
      case 40:    //win 3.0
        bmp_x = JF.readuint32(in);
        bmp_y = JF.readuint32(in);
        a = JF.readuint16(in);  //planes - ignore
        if (JF.readuint16(in) != 24) {
          return null;
        }
        if (JF.readuint32(in) != 0) {
          return null;  //no compression supported
        }
        for (a = 0; a < 5; a++) {
          JF.readuint32(in);
        }
        break;
      default:
        return null;
    }

    //each scan-line is DWORD aligned!
    int slsiz = (bmp_x * 3 + 3) & 0x7ffffffc;
    int sbufsiz = slsiz * bmp_y;

    byte simg[] = new byte[sbufsiz];
    try {
      if (in.read(simg) != sbufsiz) {
        return null;
      }
    } catch (Exception e) {
      return null;
    }

    ret = new int[bmp_x * bmp_y];

    int sidx = slsiz * (bmp_y - 1);
    int didx = 0;

    for (int y = 0; y < bmp_y; y++) {
      for (int x = 0; x < bmp_x; x++) {
        ret[didx++] = (((int) simg[sidx + x * 3 + 2] & 0xff) << 16 | ((int) simg[sidx + x * 3 + 1] & 0xff) << 8 | (int) simg[sidx + x * 3] & 0xff) | JFImage.OPAQUE;
      }
      sidx -= slsiz;
    }
    size.width = bmp_x;
    size.height = bmp_y;
    return ret;
  }

  protected static boolean save(OutputStream out, int buf[], Dimension size, boolean noheader, boolean icon) {

    int a;
    int slsiz = size.width * 3;
    int dlsiz = (slsiz + 3) & 0xfffffffc;
    int slack = slsiz - dlsiz;
    int dbufsiz = dlsiz * size.height;
    int tmp;
    int fs;

    if (!noheader) {
      if (!icon) {
        JF.writeuint8(out, 'B');
        JF.writeuint8(out, 'M');
        fs = dlsiz * size.width + 54;
        JF.writeuint32(out, fs);
        a = 0;
        JF.writeuint32(out, a);
        a = 54;  //offset of bits
        JF.writeuint32(out, a);
        a = 40;  //size of win header
        JF.writeuint32(out, a);
      } else {
        a = 40;
        JF.writeuint32(out, a);
      }
      JF.writeuint32(out, size.width);
      if (icon) {
        JF.writeuint32(out, size.height * 2);  //why?
      } else {
        JF.writeuint32(out, size.height);
      }
      JF.writeuint16(out, 1); //planes
      JF.writeuint16(out, 24);  //bpp
      JF.writeuint32(out, 0);  //comp
      JF.writeuint32(out, size.width * size.height * 3);
      for (a = 0; a < 4; a++) {
        JF.writeuint32(out, 0);
      }
    }

    byte dbuf[] = new byte[dbufsiz];
    int sidx = size.width * (size.height - 1);
    int didx = 0;
    //Invert BMP! (Image is stored Upside Down!!!)
    for (int y = 0; y < size.height; y++) {
      for (int x = 0; x < size.width; x++) {
        dbuf[didx++] = (byte) ((buf[sidx] >> 16) & 0xff);
        dbuf[didx++] = (byte) ((buf[sidx] >> 8) & 0xff);
        dbuf[didx++] = (byte) (buf[sidx] & 0xff);
        sidx++;
      }
      didx += slack;
      sidx -= (size.width * 2);
    }

    try {
      out.write(dbuf);
    } catch (Exception e) {
      return false;
    }

    return true;
  }
}
