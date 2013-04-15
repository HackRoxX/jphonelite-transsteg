package javaforce;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.tree.*;

/**
 * This class allows creating GUI by hand.
 *
 * This class is obsolete - use NetBeans IDE GUI Builder instead.
 *
 */
@SuppressWarnings("unchecked")
public class GUI implements
        ActionListener,
        WindowListener,
        MouseListener,
        KeyListener,
        FocusListener,
        ComponentListener,
        DocumentListener,
        WindowFocusListener,
        TableModelListener,
        TreeModelListener {

  protected EventHandler handler = null;

  public void setEventHandler(EventHandler eh) {
    handler = eh;
  }

  public void attachJApplet(JApplet applet, LayoutManager lm) {
    applet.addMouseListener(this);
    applet.addKeyListener(this);
    applet.addFocusListener(this);
    applet.addComponentListener(this);
    applet.setLayout(lm);
  }

  public void attachWindow(Window window) {
    window.addWindowListener(this);
  }
//Event MSG types
  public static final int MSG_NULL = 0x0000;
  public static final int MSG_ACTION = 0x0101;
  public static final int MSG_WINDOW_ACTIVATED = 0x0201;
  public static final int MSG_WINDOW_CLOSED = 0x0202;
  public static final int MSG_WINDOW_CLOSING = 0x0203;
  public static final int MSG_WINDOW_DEACTIVATED = 0x0204;
  public static final int MSG_WINDOW_DEICONIFIED = 0x0205;
  public static final int MSG_WINDOW_ICONIFIED = 0x0206;
  public static final int MSG_WINDOW_OPENED = 0x0207;
  public static final int MSG_MOUSE_CLICKED = 0x0301;
  public static final int MSG_MOUSE_ENTERED = 0x0302;
  public static final int MSG_MOUSE_EXITED = 0x0303;
  public static final int MSG_MOUSE_PRESSED = 0x0304;
  public static final int MSG_MOUSE_RELEASED = 0x0305;
  public static final int MSG_KEY_PRESSED = 0x0401;
  public static final int MSG_KEY_RELEASED = 0x0402;
  public static final int MSG_KEY_TYPED = 0x0403;
  public static final int MSG_GETFOCUS = 0x501;
  public static final int MSG_LOSTFOCUS = 0x502;
  public static final int MSG_HIDE = 0x0601;
  public static final int MSG_MOVE = 0x0602;
  public static final int MSG_SIZE = 0x0603;
  public static final int MSG_SHOW = 0x0604;
  public static final int MSG_CHANGED = 0x0701;
  public static final int MSG_INSERT = 0x0702;
  public static final int MSG_DELETE = 0x0703;
  public static final int MSG_STRUCTURE = 0x0704;

  public JFrame createJFrame(String title, int x, int y, int w, int h, LayoutManager lm) {
    //NOTE : When you add components, you must validate() the frame
    JFrame frame = new JFrame(title);
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(this);
    frame.addMouseListener(this);
    frame.addKeyListener(this);
    frame.addFocusListener(this);
    frame.addComponentListener(this);
    frame.setVisible(true);
    frame.setLocation(x, y);
    //JAVA BUG : getInsets() doesn't work until the Window is visible
    Insets insets = frame.getInsets();
    frame.setSize(w + insets.left + insets.right, h + insets.top + insets.bottom);
    Container mainpane = frame.getContentPane();
    mainpane.setLayout(lm);
    mainpane.addMouseListener(this);
    mainpane.addKeyListener(this);
    mainpane.addFocusListener(this);
    return frame;
  }

  public JDialog createJDialog(String title, int x, int y, int w, int h, LayoutManager lm, JFrame parent) {
    //NOTE : When you add components, you must validate() the frame
    JDialog dialog = new JDialog(parent, title);
    dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    dialog.addWindowListener(this);
    dialog.addWindowFocusListener(this);
    dialog.addMouseListener(this);
    dialog.addKeyListener(this);
    dialog.addFocusListener(this);
    dialog.addComponentListener(this);
    dialog.setVisible(true);
//    dialog.setPosition(x,y);  //???
    //JAVA BUG : getInsets() doesn't work until the Window is visible
    Insets insets = dialog.getInsets();
    dialog.setSize(w + insets.left + insets.right, h + insets.top + insets.bottom);
    Container mainpane = dialog.getContentPane();
    mainpane.setLayout(lm);
    mainpane.addMouseListener(this);
    mainpane.addKeyListener(this);
    mainpane.addFocusListener(this);
    return dialog;
  }

  public JPanel createJPanel(LayoutManager lm, Container parent) {
    JPanel ret = new JPanel();
    ret.setLayout(lm);
    if (parent != null) {
      ret.setBounds(0, 0, parent.getWidth(), parent.getHeight());
    }
    ret.setVisible(false);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  ;
  public JPanel createJPanel(int x, int y, int w, int h, LayoutManager lm, Container parent) {
    JPanel ret = new JPanel();
    ret.setLayout(lm);
    ret.setBounds(x, y, w, h);
    ret.setVisible(false);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  ;
  public JFImage createJFImage(Container parent) {
    JFImage ret = new JFImage();
    if (parent != null) {
      ret.setBounds(0, 0, parent.getWidth(), parent.getHeight());
      parent.add(ret);
    }
    return ret;
  }

  ;
  public JFImage createJFImage(int x, int y, int w, int h, Container parent) {
    JFImage ret = new JFImage();
    ret.setBounds(x, y, w, h);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  ;
//the following are for use with LayoutManager()s
  public JScrollPane createJScrollPane(Component child, Container parent) {
    JScrollPane ret = new JScrollPane(child);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JTabbedPane createJTabbedPane(Container parent) {
    JTabbedPane ret = new JTabbedPane();
//    ret.setLayout(null);  //causes Exception
    ret.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);  //allow scrolling
    ret.addKeyListener(this);
    ret.addMouseListener(this);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  ;
  public JLabel createJLabel(String title, Container parent) {
    JLabel ret = new JLabel(title, SwingConstants.RIGHT);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JLabel createJLabel(String title, Icon icon, Container parent) {
    JLabel ret = new JLabel(title, icon, SwingConstants.RIGHT);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JLabel createJLabel(Icon icon, Container parent) {
    JLabel ret = new JLabel(icon);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JButton createJButton(String title, Container parent) {
    JButton ret = new JButton(title);
    ret.addActionListener(this);
    ret.setMargin(new Insets(0, 0, 0, 0));
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JCheckBox createJCheckBox(String title, Container parent) {
    JCheckBox ret = new JCheckBox(title);
    ret.addActionListener(this);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JComboBox createJComboBox(String[] items, Container parent) {
    JComboBox ret = new JComboBox(items);
    ret.addActionListener(this);
    ret.setEditable(false);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JTextField createJTextField(Container parent) {
    JTextField ret = new JTextField();
    ret.addKeyListener(this);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JPasswordField createJPasswordField(Container parent) {
    JPasswordField ret = new JPasswordField();
    ret.addKeyListener(this);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JTextArea createJTextArea(Container parent) {
    JTextArea ret = new JTextArea();
    ret.addKeyListener(this);
    ret.getDocument().addDocumentListener(this);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JTable createJTable(TableModel tm, Container parent) {
    JTable ret = new JTable(tm);
    if (parent != null) {
      parent.add(ret);
    }
    tm.addTableModelListener(this);
    return ret;
  }

  public JList createJList(ListModel lm, Container parent) {
    JList ret = new JList(lm);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }
  /*
   public JList createJList(Vector<?> v,Container parent) {
   JList ret = new JList(v);
   if (parent != null) parent.add(ret);
   return ret;
   }
   */

  public JList createJList(Object ol[], Container parent) {
    JList ret = new JList(ol);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JTree createJTree(TreeModel tm, Container parent) {
    JTree ret = new JTree(tm);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JTree createJTree(TreeNode tn, Container parent) {
    JTree ret = new JTree(tn);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }
//the following are for manual layout (LayoutManager == null) (setBounds() is used instead)

  public JScrollPane createJScrollPane(Component child, int x, int y, int w, int h, Container parent) {
    JScrollPane ret = new JScrollPane(child);
    ret.setBounds(x, y, w, h);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JTabbedPane createJTabbedPane(int x, int y, int w, int h, Container parent) {
    JTabbedPane ret = new JTabbedPane();
//    ret.setLayout(null);  //causes Exception
    ret.setBounds(x, y, w, h);
    ret.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);  //allow scrolling
    ret.addKeyListener(this);
    ret.addMouseListener(this);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  ;
  public JLabel createJLabel(String title, int x, int y, int w, int h, Container parent) {
    JLabel ret = new JLabel(title, SwingConstants.RIGHT);
    ret.setBounds(x, y, w, h);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JLabel createJLabel(String title, Icon icon, int x, int y, int w, int h, Container parent) {
    JLabel ret = new JLabel(title, icon, SwingConstants.RIGHT);
    ret.setBounds(x, y, w, h);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JLabel createJLabel(Icon icon, int x, int y, int w, int h, Container parent) {
    JLabel ret = new JLabel(icon);
    ret.setBounds(x, y, w, h);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JButton createJButton(String title, int x, int y, int w, int h, Container parent) {
    JButton ret = new JButton(title);
    ret.setBounds(x, y, w, h);
    ret.addActionListener(this);
    ret.setMargin(new Insets(0, 0, 0, 0));
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JCheckBox createJCheckBox(String title, int x, int y, int w, int h, Container parent) {
    JCheckBox ret = new JCheckBox(title);
    ret.setBounds(x, y, w, h);
    ret.addActionListener(this);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JComboBox createJComboBox(String[] items, int x, int y, int w, int h, Container parent) {
    JComboBox ret = new JComboBox(items);
    ret.setBounds(x, y, w, h);
    ret.addActionListener(this);
    ret.setEditable(false);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JTextField createJTextField(int x, int y, int w, int h, Container parent) {
    JTextField ret = new JTextField();
    ret.setBounds(x, y, w, h);
    ret.addKeyListener(this);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JPasswordField createJPasswordField(int x, int y, int w, int h, Container parent) {
    JPasswordField ret = new JPasswordField();
    ret.setBounds(x, y, w, h);
    ret.addKeyListener(this);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JTextArea createJTextArea(int x, int y, int w, int h, Container parent) {
    JTextArea ret = new JTextArea();
    ret.setBounds(x, y, w, h);
    ret.addKeyListener(this);
    ret.getDocument().addDocumentListener(this);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JTable createJTable(TableModel tm, int x, int y, int w, int h, Container parent) {
    JTable ret = new JTable(tm);
    ret.setBounds(x, y, w, h);
    if (parent != null) {
      parent.add(ret);
    }
    tm.addTableModelListener(this);
    return ret;
  }

  public JList createJList(ListModel lm, int x, int y, int w, int h, Container parent) {
    JList ret = new JList(lm);
    ret.setBounds(x, y, w, h);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }
  /*
   public JList createJList(Vector<?> v,int x,int y,int w,int h,Container parent) {
   JList ret = new JList(v);
   ret.setBounds(x,y,w,h);
   if (parent != null) parent.add(ret);
   return ret;
   }
   */

  public JList createJList(Object ol[], int x, int y, int w, int h, Container parent) {
    JList ret = new JList(ol);
    ret.setBounds(x, y, w, h);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JTree createJTree(TreeModel tm, int x, int y, int w, int h, Container parent) {
    JTree ret = new JTree(tm);
    ret.setBounds(x, y, w, h);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }

  public JTree createJTree(TreeNode tn, int x, int y, int w, int h, Container parent) {
    JTree ret = new JTree(tn);
    ret.setBounds(x, y, w, h);
    if (parent != null) {
      parent.add(ret);
    }
    return ret;
  }
//interface ActionListener

  public void actionPerformed(ActionEvent e) {
    handler.event(e.getSource(), MSG_ACTION, 0, 0, 0, 0);
  }
//interface WindowListener

  public void windowOpened(WindowEvent e) {
    handler.event(e.getSource(), MSG_WINDOW_OPENED, 0, 0, 0, 0);
  }

  public void windowClosing(WindowEvent e) {
    handler.event(e.getSource(), MSG_WINDOW_CLOSING, 0, 0, 0, 0);
  }

  public void windowClosed(WindowEvent e) {
    handler.event(e.getSource(), MSG_WINDOW_CLOSED, 0, 0, 0, 0);
  }

  public void windowIconified(WindowEvent e) {
    handler.event(e.getSource(), MSG_WINDOW_ICONIFIED, 0, 0, 0, 0);
  }

  public void windowDeiconified(WindowEvent e) {
    handler.event(e.getSource(), MSG_WINDOW_DEICONIFIED, 0, 0, 0, 0);
  }

  public void windowActivated(WindowEvent e) {
    handler.event(e.getSource(), MSG_WINDOW_ACTIVATED, 0, 0, 0, 0);
  }

  public void windowDeactivated(WindowEvent e) {
    handler.event(e.getSource(), MSG_WINDOW_DEACTIVATED, 0, 0, 0, 0);
  }
//interface KeyListener

  public void keyPressed(KeyEvent e) {
    handler.event(e.getSource(), MSG_KEY_PRESSED, 0, 0, e.getKeyCode(), e.getModifiers());
  }

  public void keyReleased(KeyEvent e) {
    handler.event(e.getSource(), MSG_KEY_RELEASED, 0, 0, e.getKeyCode(), e.getModifiers());
  }

  public void keyTyped(KeyEvent e) {
    handler.event(e.getSource(), MSG_KEY_TYPED, 0, 0, e.getKeyChar(), e.getModifiers());
  }
//interface MouseListener

  public void mouseClicked(MouseEvent e) {
    handler.event(e.getSource(), MSG_MOUSE_CLICKED, e.getX(), e.getY(), e.getButton(), e.getModifiers());
  }

  public void mousePressed(MouseEvent e) {
    handler.event(e.getSource(), MSG_MOUSE_PRESSED, e.getX(), e.getY(), e.getButton(), e.getModifiers());
  }

  public void mouseReleased(MouseEvent e) {
    handler.event(e.getSource(), MSG_MOUSE_RELEASED, e.getX(), e.getY(), e.getButton(), e.getModifiers());
  }

  public void mouseEntered(MouseEvent e) {
    handler.event(e.getSource(), MSG_MOUSE_ENTERED, e.getX(), e.getY(), e.getButton(), e.getModifiers());
  }

  public void mouseExited(MouseEvent e) {
    handler.event(e.getSource(), MSG_MOUSE_EXITED, e.getX(), e.getY(), e.getButton(), e.getModifiers());
  }
//interface FocusListener

  public void focusGained(FocusEvent e) {
    handler.event(e.getSource(), MSG_GETFOCUS, 0, 0, 0, 0);
  }

  public void focusLost(FocusEvent e) {
    handler.event(e.getSource(), MSG_LOSTFOCUS, 0, 0, 0, 0);
  }
//interface ComponentListener

  public void componentHidden(ComponentEvent e) {
    handler.event(e.getComponent(), MSG_HIDE, 0, 0, 0, 0);
  }

  public void componentMoved(ComponentEvent e) {
    handler.event(e.getComponent(), MSG_MOVE, 0, 0, 0, 0);
  }

  public void componentResized(ComponentEvent e) {
    if (e.getComponent() instanceof JFrame) {
      JFrame frame = (JFrame) e.getComponent();
      Insets insets = frame.getInsets();
      handler.event(frame, MSG_SIZE, frame.getWidth() - insets.left - insets.right,
              frame.getHeight() - insets.top - insets.bottom, 0, 0);
    } else {
      handler.event(e.getComponent(), MSG_SIZE, e.getComponent().getWidth(), e.getComponent().getHeight(), 0, 0);
    }
  }

  public void componentShown(ComponentEvent e) {
    handler.event(e.getComponent(), MSG_SHOW, 0, 0, 0, 0);
  }
//interface DocumentListener

  public void changedUpdate(DocumentEvent e) {
    handler.event(e.getDocument(), MSG_CHANGED, 0, 0, 0, 0);
  }

  public void insertUpdate(DocumentEvent e) {
    handler.event(e.getDocument(), MSG_INSERT, 0, 0, 0, 0);
  }

  public void removeUpdate(DocumentEvent e) {
    handler.event(e.getDocument(), MSG_DELETE, 0, 0, 0, 0);
  }
//interface WindowFocusListener

  public void windowGainedFocus(WindowEvent e) {
    handler.event(e.getSource(), MSG_GETFOCUS, 0, 0, 0, 0);
  }

  public void windowLostFocus(WindowEvent e) {
    handler.event(e.getSource(), MSG_LOSTFOCUS, 0, 0, 0, 0);
  }
//interface TableModelListener

  public void tableChanged(TableModelEvent e) {
    handler.event(e.getSource(), MSG_CHANGED, 0, 0, 0, 0);
  }
//interface TreeModelListener

  public void treeNodesChanged(TreeModelEvent e) {
    handler.event(e.getSource(), MSG_CHANGED, 0, 0, 0, 0);
  }

  public void treeNodesInserted(TreeModelEvent e) {
    handler.event(e.getSource(), MSG_INSERT, 0, 0, 0, 0);
  }

  public void treeNodesRemoved(TreeModelEvent e) {
    handler.event(e.getSource(), MSG_DELETE, 0, 0, 0, 0);
  }

  public void treeStructureChanged(TreeModelEvent e) {
    handler.event(e.getSource(), MSG_STRUCTURE, 0, 0, 0, 0);
  }
};
