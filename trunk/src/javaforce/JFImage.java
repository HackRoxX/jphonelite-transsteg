package javaforce;

import java.awt.*;
import java.awt.image.*;
import java.awt.font.*;
import java.awt.geom.*;
import javax.swing.*;
import java.io.*;
import javaforce.jni.*;
import javax.imageio.ImageIO;

/**
 * Encapsules BufferedImage to provide more functions. Implements
 * javax.swing.Icon so it can also be used with JLabel.
 *
 * @author Peter Quiring
 */
public class JFImage extends JComponent implements Icon {

  private BufferedImage bi;
  private Graphics2D g2d;

  public JFImage() {
  }

  public JFImage(int x, int y) {
    setImageSize(x, y);
  }

  public enum ResizeOperation {

    CLEAR, CHOP, SCALE
  };
  private ResizeOperation defaultResizeOperation = ResizeOperation.CLEAR;

  private void initImage(int x, int y) {
//    System.out.println("JFImage.initImage:" + x + "," + y);
    bi = new BufferedImage(x, y, BufferedImage.TYPE_INT_ARGB);
    g2d = bi.createGraphics();
    setPreferredSize(new Dimension(x, y));
  }

  public void setImageSize(int x, int y) {
    setSize(x, y);
    initImage(x, y);
  }

  public void setBounds(int x, int y, int w, int h) {
    //usually called from LayoutManager / ScrollPane
//    System.out.println("JFImage.setBounds:" + x + "," + y + "," + w + "," + h);
    super.setBounds(x, y, w, h);
    if (bi != null) {
      BufferedImage oldbi = bi;
      int oldw = getWidth();
      int oldh = getHeight();
      switch (defaultResizeOperation) {
        case CLEAR:
          initImage(w, h);
          break;
        case CHOP:
          initImage(w, h);
          g2d.drawImage(oldbi, 0, 0, null);
          break;
        case SCALE:
          initImage(w, h);
          g2d.drawImage(oldbi, 0, 0, w, h, 0, 0, oldw, oldh, null);
          break;
      }
    } else {
      initImage(w, h);
    }
  }

  public void setSize(int x, int y) {
//    System.out.println("JFImage.setSize:" + x + "," + y);
    super.setSize(x, y);  //just calls setBounds(getLocation(),x,y);
  }

  public void setSize(Dimension d) {
//    System.out.println("JFImage.setSize:" + d);
    super.setSize(d);
  }

  /**
   * Controls how this image is resized by layout managers.
   */
  public void setDefaultResizeOperation(ResizeOperation ro) {
    defaultResizeOperation = ro;
  }

  public Image getImage() {
    return bi;
  }

  public BufferedImage getBufferedImage() {
    return bi;
  }

  public Graphics getGraphics() {
    return g2d;
  }

  public Graphics2D getGraphics2D() {
    return g2d;
  }

  public void paint(Graphics g) {
//System.out.println("paint");
    if (bi == null) {
      return;
    }
    g.drawImage(getImage(), 0, 0, null);
  }

  public int getWidth() {
    if (bi == null) {
      return -1;
    }
    return bi.getWidth();
  }

  public int getHeight() {
    if (bi == null) {
      return -1;
    }
    return bi.getHeight();
  }

  public boolean load(InputStream in) {
    //use ImageIO (the returned Image is type TYPE_CUSTOM which is slow so copy pixels to an TYPE_INT_ARGB)
    BufferedImage tmp;
    try {
      tmp = ImageIO.read(in);
    } catch (Exception e) {
      JFLog.log(e);
      return false;
    }
    if (tmp == null) {
      return false;
    }
    int x = tmp.getWidth();
    int y = tmp.getHeight();
    int px[] = tmp.getRGB(0, 0, x, y, null, 0, x);
    setImageSize(x, y);
    putPixels(px, 0, 0, x, y, 0);
    return true;
  }

  public boolean save(OutputStream out, String fmt) {
    try {
      return ImageIO.write(bi, fmt, out);
    } catch (Exception e) {
      JFLog.log(e);
      return false;
    }
  }

  public boolean load(String filename) {
    try {
      return load(new FileInputStream(filename));
    } catch (Exception e) {
      return false;
    }
  }

  public boolean save(String filename, String fmt) {
    try {
      return save(new FileOutputStream(filename), fmt);
    } catch (Exception e) {
      return false;
    }
  }

  public boolean loadJPG(InputStream in) {
    return load(in);
  }

  public boolean saveJPG(OutputStream out) {
    boolean ok = save(out, "jpg");
    if (ok) {
      return ok;
    }
    //try using native jpeg encoder (assumes you have already called LnxAPI.init())
    if (!new File("/usr/share/java/javaforce.jar").exists()) {
      return false;
    }
    try {
      int x = getWidth();
      int y = getHeight();
      int px[] = getPixels();
      byte jpeg[] = LnxAPI.jpeg_encoder(px, x, y, 90);
      if (jpeg == null) {
        throw new Exception("LnxAPI.jpeg_encoder() failed");
      }
      out.write(jpeg);
      return true;
    } catch (Exception e) {
      JFLog.log(e);
    }
    return false;
  }

  public boolean loadJPG(String filename) {
    try {
      return loadJPG(new FileInputStream(filename));
    } catch (Exception e) {
      return false;
    }
  }

  public boolean saveJPG(String filename) {
    try {
      return saveJPG(new FileOutputStream(filename));
    } catch (Exception e) {
      return false;
    }
  }

  public boolean loadPNG(InputStream in) {
    return load(in);
  }

  public boolean savePNG(OutputStream out) {
    return save(out, "png");
  }

  public boolean loadPNG(String filename) {
    try {
      return loadPNG(new FileInputStream(filename));
    } catch (Exception e) {
      return false;
    }
  }

  public boolean savePNG(String filename) {
    try {
      return savePNG(new FileOutputStream(filename));
    } catch (Exception e) {
      return false;
    }
  }
  //NOTE : Not all JREs support "bmp" so I use custom code

  public boolean loadBMP(String filename) {
    try {
      return loadBMP(new FileInputStream(filename));
    } catch (Exception e) {
      return false;
    }
  }

  public boolean saveBMP(String filename) {
    try {
      return saveBMP(new FileOutputStream(filename));
    } catch (Exception e) {
      return false;
    }
  }

  public boolean loadBMP(InputStream in) {
    int buf[];
    Dimension size = new Dimension(0, 0);
    buf = bmp.load(in, size);
    if (buf == null) {
      return false;
    }
    if (size.width == 0 || size.height == 0) {
      return false;
    }
    setImageSize(size.width, size.height);
    putPixels(buf, 0, 0, size.width, size.height, 0);
    return true;
  }

  public boolean saveBMP(OutputStream out) {
    int pixels[];
    pixels = getPixels(0, 0, getWidth(), getHeight());
    Dimension size = new Dimension(getWidth(), getHeight());
    return bmp.save(out, pixels, size, false, false);
  }

  public boolean loadSVG(String filename) {
    try {
      return loadSVG(new FileInputStream(filename));
    } catch (Exception e) {
      return false;
    }
  }

  public boolean saveSVG(String filename) {
    try {
      return saveSVG(new FileOutputStream(filename));
    } catch (Exception e) {
      return false;
    }
  }

  public boolean loadSVG(InputStream in) {
    int buf[];
    Dimension size = new Dimension(0, 0);
    buf = svg.load(in, size);
    if (buf == null) {
      return false;
    }
    if (size.width == 0 || size.height == 0) {
      return false;
    }
    setImageSize(size.width, size.height);
    putPixels(buf, 0, 0, size.width, size.height, 0);
    return true;
  }

  public boolean saveSVG(OutputStream out) {
    ByteArrayOutputStream png_data = new ByteArrayOutputStream();
    savePNG(png_data);
    Dimension size = new Dimension(getWidth(), getHeight());
    return svg.save(out, png_data.toByteArray(), size);
  }

  public void putJFImage(JFImage img, int x, int y) {
    g2d.drawImage(img.bi, x, y, null);
  }

  public void putJFImageKeyClr(JFImage img, int x, int y, int kc) {
    int px[] = img.getPixels(0, 0, img.getWidth(), img.getHeight());
    putPixelsKeyClr(px, x, y, img.getWidth(), img.getHeight(), 0, kc);
  }

  public void putJFImageBlend(JFImage img, int x, int y) {
    int px[] = img.getPixels(0, 0, img.getWidth(), img.getHeight());
    putPixelsBlend(px, x, y, img.getWidth(), img.getHeight(), 0);
  }

  public JFImage getJFImage(int x, int y, int w, int h) {
    JFImage ret = new JFImage();
    ret.bi = bi.getSubimage(x, y, w, h);
    ret.g2d = ret.bi.createGraphics();
    return ret;
  }
  public static final int ALPHA_MASK = 0xff000000;  //Alpha
  public static final int OPAQUE = 0xff000000;
  public static final int TRANSPARENT = 0x00000000;
  public static final int RED_MASK = 0x00ff0000;
  public static final int GREEN_MASK = 0x0000ff00;
  public static final int BLUE_MASK = 0x000000ff;
  public static final int RGB_MASK = 0x00ffffff;

  public void putPixel(int x, int y, int r, int g, int b) {
    bi.setRGB(x, y, OPAQUE | r << 16 | g << 8 | b);
  }

  public void putPixel(int x, int y, int c) {
    bi.setRGB(x, y, OPAQUE | c);
  }

  public int getPixel(int x, int y) {
    return bi.getRGB(x, y) & RGB_MASK;
  }

  public int getAlpha(int x, int y) {
    return bi.getRGB(x, y) & ALPHA_MASK;
  }

  public void putAlpha(int x, int y, int lvl) {
    int px = bi.getRGB(x, y);
    px &= 0xffffff;
    px |= (lvl << 24);
    bi.setRGB(x, y, px);
  }

  public void putPixels(int[] px, int x, int y, int w, int h, int offset) {
    //do clipping
    int ow = w;
    if (y < 0) {
      y *= -1;
      h -= y;
      if (h <= 0) {
        return;
      }
      offset += y * w;
      y = 0;
    }
    if (x < 0) {
      x *= -1;
      w -= x;
      if (w <= 0) {
        return;
      }
      offset += x;
      x = 0;
    }
    if (x + w > getWidth()) {
      w = getWidth() - x;
      if (w <= 0) {
        return;
      }
    }
    if (y + h > getHeight()) {
      h = getHeight() - y;
      if (h <= 0) {
        return;
      }
    }
    bi.setRGB(x, y, w, h, px, offset, ow);
  }

  public void putPixels(int[] px, int x, int y, int w, int h, int offset, int scansize) {
    //do clipping
    if (y < 0) {
      y *= -1;
      h -= y;
      if (h <= 0) {
        return;
      }
      offset += y * scansize;
      y = 0;
    }
    if (x < 0) {
      x *= -1;
      w -= x;
      if (w <= 0) {
        return;
      }
      offset += x;
      x = 0;
    }
    if (x + w > getWidth()) {
      w = getWidth() - x;
      if (w <= 0) {
        return;
      }
    }
    if (y + h > getHeight()) {
      h = getHeight() - y;
      if (h <= 0) {
        return;
      }
    }
    bi.setRGB(x, y, w, h, px, offset, scansize);
  }

  public void putPixelsKeyClr(int[] src, int x, int y, int w, int h, int offset, int keyclr) {
    int dest[] = getPixels(x, y, w, h);
    for (int a = 0; a < w * h; a++) {
      if ((src[a] & RGB_MASK) != keyclr) {
        dest[a] = src[a + offset];
      }
    }
    putPixels(dest, x, y, w, h, 0);
  }

  public void putPixelsBlend(int[] src, int x, int y, int w, int h, int offset) {
    int dest[] = getPixels(x, y, w, h);
    int slvl, dlvl, sp, dp;
    for (int a = 0; a < w * h; a++) {
      sp = src[a];
      slvl = sp & ALPHA_MASK;
      sp &= RGB_MASK;
      slvl >>>= 24;
      if (slvl > 0) {
        slvl++;
      }
      dlvl = 0x100 - slvl;
      dp = dest[a] & RGB_MASK;
      dest[a] = OPAQUE
              + ((((dp & RED_MASK) * dlvl) >> 8) & RED_MASK)
              + ((((dp & GREEN_MASK) * dlvl) >> 8) & GREEN_MASK)
              + ((((dp & BLUE_MASK) * dlvl) >> 8) & BLUE_MASK)
              + ((((sp & RED_MASK) * slvl) >> 8) & RED_MASK)
              + ((((sp & GREEN_MASK) * slvl) >> 8) & GREEN_MASK)
              + ((((sp & BLUE_MASK) * slvl) >> 8) & BLUE_MASK);
    }
    putPixels(dest, x, y, w, h, 0);
  }

  public int[] getPixels(int x, int y, int w, int h) {
    int ret[] = new int[w * h];
    //do clipping
    int ow = w;
    int offset = 0;
    if (y < 0) {
      y *= -1;
      h -= y;
      if (h <= 0) {
        return ret;
      }
      offset += y * w;
      y = 0;
    }
    if (x < 0) {
      x *= -1;
      w -= x;
      if (w <= 0) {
        return ret;
      }
      offset += x;
      x = 0;
    }
    if (x + w > getWidth()) {
      w = getWidth() - x;
      if (w <= 0) {
        return ret;
      }
    }
    if (y + h > getHeight()) {
      h = getHeight() - y;
      if (h <= 0) {
        return ret;
      }
    }
    return bi.getRGB(x, y, w, h, ret, offset, ow);
  }

  public int[] getPixels() {
    return getPixels(0, 0, getWidth(), getHeight());
  }

  public void fill(int x, int y, int w, int h, int c) {
    c |= OPAQUE;
    g2d.setColor(new Color(c));
    g2d.fillRect(x, y, w, h);
  }

  public void fillAlpha(int x, int y, int w, int h, int lvl) {
    lvl <<= 24;
    int px[] = getPixels(x, y, w, h);
    for (int a = 0; a < w * h; a++) {
      px[a] &= RGB_MASK;
      px[a] |= lvl;
    }
    putPixels(px, x, y, w, h, 0);
  }

  public void fillAlphaKeyClr(int x, int y, int w, int h, int lvl, int keyClr) {
    lvl <<= 24;
    int px[] = getPixels(x, y, w, h);
    for (int a = 0; a < w * h; a++) {
      if ((px[a] & RGB_MASK) == keyClr) {
        px[a] &= RGB_MASK;
        px[a] |= lvl;
      }
    }
    putPixels(px, x, y, w, h, 0);
  }

  public void box(int x, int y, int w, int h, int c) {
    c |= OPAQUE;
    line(x, y, x + w - 1, y, c);
    line(x, y, x, y + h - 1, c);
    line(x + w - 1, y, x + w - 1, y + h - 1, c);
    line(x, y + h - 1, x + w - 1, y + h - 1, c);
  }

  private int abs(int x) {
    if (x < 0) {
      return x * -1;
    }
    return x;
  }

  public void hline(int x1, int x2, int y, int c) {
    line(x1, y, x2, y, c);
  }

  public void vline(int x, int y1, int y2, int c) {
    line(x, y1, x, y2, c);
  }

  public void line(int x1, int y1, int x2, int y2, int c) {
    c |= OPAQUE;
    g2d.setColor(new Color(c));
    g2d.drawLine(x1, y1, x2, y2);
  }

  public int[] getFontMetrics(Font fnt, String txt) {
    //return : [0]=width [1]=ascent [2]=descent (total height = ascent + descent)
    //Note : add ascent with y coordinate of print() to start printing below your coordinates
    //For a static version see JF.java
    FontRenderContext frc = g2d.getFontRenderContext();
    TextLayout tl = new TextLayout(txt, fnt, frc);
    int ret[] = new int[3];
    ret[0] = (int) tl.getBounds().getWidth();
    ret[1] = (int) tl.getAscent();
    ret[2] = (int) tl.getDescent();
    return ret;
  }

  public void print(Font fnt, int x, int y, String txt, int clr) {
    g2d.setColor(new Color(clr));
    g2d.setFont(fnt);
    g2d.drawString(txt, x, y);  //x,y = baseline of font
  }

  public void setFont(Font font) {
    //this is a little confusing because setFont() is inherited from JComponent which is what you probably don't want
    getGraphics().setFont(font);
  }
//interface Icon

  public int getIconHeight() {
    return getHeight();
  }

  public int getIconWidth() {
    return getWidth();
  }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    g.drawImage(getImage(), x, y, null);
  }
};
