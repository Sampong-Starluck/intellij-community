/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.intellij.MacIntelliJTextFieldUI;
import com.intellij.ide.ui.laf.intellij.WinIntelliJTextFieldUI;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.ExtendableTextField;
import com.intellij.ui.components.ExtendableTextField.Extension;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Objects;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.ui.JBUI.scale;

/**
 * @author Konstantin Bulenkov
 * @author Sergey Malenkov
 */
public abstract class TextFieldWithPopupHandlerUI extends BasicTextFieldUI implements Condition {
  private static final String DOCUMENT = "document";
  private static final String MONOSPACED = "monospaced";
  private static final String VARIANT = "JTextField.variant";
  private static final String POPUP = "JTextField.Search.FindPopup";
  private static final String INPLACE_HISTORY = "JTextField.Search.InplaceHistory";
  private static final String ON_CLEAR = "JTextField.Search.CancelAction";
  @SuppressWarnings("UseDPIAwareInsets")
  private final Insets insets = new Insets(0, 0, 0, 0);
  protected final LinkedHashMap<String, IconHolder> icons = new LinkedHashMap<>();
  protected final JTextField myTextField;
  private final Handler handler = new Handler();
  private boolean monospaced;
  private Object variant;
  private int cursor;

  /**
   * @param insets a border insets without a space for icons
   * @see javax.swing.border.Border#getBorderInsets
   */
  public static void updateBorderInsets(Component c, Insets insets) {
    if (c instanceof JTextComponent) {
      JTextComponent component = (JTextComponent)c;
      if (component.getUI() instanceof TextFieldWithPopupHandlerUI) {
        TextFieldWithPopupHandlerUI ui = (TextFieldWithPopupHandlerUI)component.getUI();
        insets.top += ui.insets.top;
        insets.left += ui.insets.left;
        insets.right += ui.insets.right;
        insets.bottom += ui.insets.bottom;
      }
    }
  }

  public TextFieldWithPopupHandlerUI(JTextField textField) {
    myTextField = textField;
  }

  /**
   * @return a search icon in one of the four states or {@code null} to hide it
   */
  protected Icon getSearchIcon(boolean hovered, boolean clickable) {
    return AllIcons.Actions.Search;
  }

  /**
   * @return a preferred space to paint the search icon
   */
  protected int getSearchIconPreferredSpace() {
    Icon icon = getSearchIcon(true, true);
    return icon == null ? 0 : icon.getIconWidth() + getSearchIconGap();
  }

  /**
   * @return a gap between the search icon and the editable area
   */
  protected int getSearchIconGap() {
    return scale(2);
  }

  /**
   * @return a clear icon in one of the four states or {@code null} to hide it
   */
  protected Icon getClearIcon(boolean hovered, boolean clickable) {
    return !clickable ? null : hovered ? AllIcons.Actions.Clean : AllIcons.Actions.CleanLight;
  }

  /**
   * @return a preferred space to paint the clear icon
   */
  protected int getClearIconPreferredSpace() {
    Icon icon = getClearIcon(true, true);
    return icon == null ? 0 : icon.getIconWidth() + getClearIconGap();
  }

  /**
   * @return a gap between the clear icon and the editable area
   */
  protected int getClearIconGap() {
    return scale(2);
  }

  /**
   * @return {@code true} if component exists and contains non-empty string
   */
  protected boolean hasText() {
    JTextComponent component = getComponent();
    return (component != null) && !isEmpty(component.getText());
  }

  protected void updateIconsLayout(Rectangle bounds) {
    for (IconHolder holder : icons.values()) {
      int gap = holder.extension.getIconGap();
      if (holder.extension.isIconBeforeText()) {
        holder.bounds.x = bounds.x;
        bounds.width -= holder.bounds.width + gap;
        bounds.x += holder.bounds.width + gap;
      }
      else {
        bounds.width -= holder.bounds.width + gap;
        holder.bounds.x = bounds.x + bounds.width + gap;
      }
      holder.bounds.y = bounds.y + (bounds.height - holder.bounds.height) / 2;
    }
  }

  protected SearchAction getActionUnder(@NotNull Point p) {
    return null;
  }

  protected void showSearchPopup() {
  }

  /**
   * Adds listeners to the current text component and sets its variant.
   */
  @Override
  protected void installListeners() {
    JTextComponent component = getComponent();
    handler.installListener(component.getDocument());
    component.addPropertyChangeListener(handler);
    component.addMouseMotionListener(handler);
    component.addMouseListener(handler);
    component.addFocusListener(handler);
    setVariant(component.getClientProperty(VARIANT));
    setMonospaced(component.getClientProperty(MONOSPACED));
  }

  /**
   * Removes all installed listeners from the current text component.
   */
  @Override
  protected void uninstallListeners() {
    JTextComponent component = getComponent();
    component.removeFocusListener(handler);
    component.removeMouseListener(handler);
    component.removeMouseMotionListener(handler);
    component.removePropertyChangeListener(handler);
    handler.uninstallListener(component.getDocument());
  }

  @Override
  public int getNextVisualPositionFrom(JTextComponent t, int pos, Position.Bias b, int direction, Position.Bias[] biasRet)
    throws BadLocationException {
    int position = DarculaUIUtil.getPatchedNextVisualPositionFrom(t, pos, direction);
    return position != -1 ? position : super.getNextVisualPositionFrom(t, pos, b, direction, biasRet);
  }

  @Override
  protected Caret createCaret() {
    return Registry.is("ide.text.mouse.selection.new") ? new MyCaret(getComponent()) : super.createCaret();
  }

  @Override
  public boolean value(Object o) {
    if (o instanceof MouseEvent) {
      MouseEvent me = (MouseEvent)o;
      if (getActionUnder(me.getPoint()) != null) {
        if (me.getID() == MouseEvent.MOUSE_CLICKED) {
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> handler.mouseClicked(me));
        }
        return true;
      }
    }
    return false;
  }

  public static boolean isSearchField(Component c) {
    return c instanceof JTextField && "search".equals(((JTextField)c).getClientProperty("JTextField.variant"));
  }

  public static boolean isSearchFieldWithHistoryPopup(Component c) {
    return isSearchField(c) && ((JTextField)c).getClientProperty("JTextField.Search.FindPopup") instanceof JPopupMenu;
  }

  @Nullable
  public static AbstractAction getNewLineAction(Component c) {
    if (!isSearchField(c) || !Registry.is("ide.find.show.add.newline.hint")) return null;
    Object action = ((JTextField)c).getClientProperty("JTextField.Search.NewLineAction");
    return action instanceof AbstractAction ? (AbstractAction)action : null;
  }

  public enum SearchAction {
    POPUP, CLEAR, NEWLINE
  }

  /**
   * Default handler for mouse moved, mouse clicked, property changed and document modified.
   */
  private final class Handler extends MouseAdapter implements DocumentListener, FocusListener, PropertyChangeListener {
    /**
     * Starts listening changes in the specified document.
     */
    private void installListener(Document document) {
      if (document != null) document.addDocumentListener(this);
    }

    /**
     * Stops listening changes in the specified document.
     */
    private void uninstallListener(Document document) {
      if (document != null) document.removeDocumentListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
      if (DOCUMENT.equals(event.getPropertyName())) {
        if (event.getOldValue() instanceof Document) uninstallListener((Document)event.getOldValue());
        if (event.getNewValue() instanceof Document) installListener((Document)event.getNewValue());
      }
      else if (MONOSPACED.equals(event.getPropertyName())) {
        setMonospaced(event.getNewValue());
      }
      else if (VARIANT.equals(event.getPropertyName())) {
        setVariant(event.getNewValue());
      }
      else if (POPUP.equals(event.getPropertyName())) {
        updateIcon(icons.get("search"));
      }
    }

    @Override
    public void focusGained(FocusEvent event) {
      repaint(false);
    }

    @Override
    public void focusLost(FocusEvent event) {
      repaint(false);
    }

    @Override
    public void insertUpdate(DocumentEvent event) {
      changedUpdate(event);
    }

    @Override
    public void removeUpdate(DocumentEvent event) {
      changedUpdate(event);
    }

    @Override
    public void changedUpdate(DocumentEvent event) {
      if (!icons.isEmpty()) {
        for (IconHolder holder : icons.values()) {
          updateIcon(holder);
        }
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (!icons.isEmpty()) {
        handleMouse(e, false);
      }
      else if (getComponent() != null && isSearchField(myTextField)) {
        SearchAction action = getActionUnder(e.getPoint());
        if (action == SearchAction.POPUP && !isSearchFieldWithHistoryPopup(myTextField)) {
          action = null;
        }
        setCursor(action != null ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR);
      }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (!icons.isEmpty()) {
        handleMouse(e, true);
      }
      else if (isSearchField(myTextField)) {
        final SearchAction action = getActionUnder(e.getPoint());
        if (action != null) {
          switch (action) {
            case POPUP:
              showSearchPopup();
              break;
            case CLEAR:
              Object listener = myTextField.getClientProperty("JTextField.Search.CancelAction");
              if (listener instanceof ActionListener) {
                ((ActionListener)listener).actionPerformed(new ActionEvent(myTextField, ActionEvent.ACTION_PERFORMED, "action"));
              }
              myTextField.setText("");
              break;
            case NEWLINE: {
              AbstractAction newLineAction = getNewLineAction(myTextField);
              if (newLineAction != null) {
                newLineAction.actionPerformed(new ActionEvent(myTextField, ActionEvent.ACTION_PERFORMED, "action"));
              }
              break;
            }
          }
          e.consume();
        }
      }
    }
  }

  @Override
  public String getToolTipText(JTextComponent component, Point point) {
    if (!icons.isEmpty() && component != null && component.isEnabled()) {
      for (IconHolder holder : icons.values()) {
        if (holder.bounds.contains(point)) {
          return holder.extension.getTooltip();
        }
      }
    }
    return super.getToolTipText(component, point);
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    Dimension size = super.getMinimumSize(c);
    if (size != null) updatePreferredSize(size);
    return size;
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    if (size != null) updatePreferredSize(size);
    return size;
  }

  protected void updatePreferredSize(Dimension size) {
    size.height = Math.max(size.height, getMinimumHeight());
    JBInsets.addTo(size, insets);
  }

  protected int getMinimumHeight() {
    return 0;
  }

  @Override
  protected Rectangle getVisibleEditorRect() {
    Rectangle bounds = super.getVisibleEditorRect();
    if (bounds != null && !icons.isEmpty()) {
      JBInsets.addTo(bounds, insets);
      updateIconsLayout(bounds);
    }
    return bounds;
  }

  /**
   * Always calls the {@link #paintBackground} method before painting the current text component,
   * and then paints visible icons if needed.
   *
   * @see #getVisibleEditorRect
   */
  @Override
  protected void paintSafely(Graphics g) {
    JTextComponent component = getComponent();
    if (!component.isOpaque()) paintBackground(g);
    Shape clip = g.getClip();
    super.paintSafely(g);
    if (!icons.isEmpty()) {
      g.setClip(clip);
      for (IconHolder holder : icons.values()) {
        if (holder.icon != null) {
          holder.icon.paintIcon(component, g, holder.bounds.x, holder.bounds.y);
        }
      }
    }
  }

  /**
   * Notifies a repaint manager to repaint the current text component later.
   *
   * @param invalid {@code true} if needed to revalidate before painting.
   */
  private void repaint(boolean invalid) {
    JTextComponent component = getComponent();
    if (component != null) {
      if (invalid) component.revalidate();
      component.repaint();
    }
  }

  private void updateIcon(IconHolder holder) {
    if (holder != null) {
      Icon icon = holder.extension.getIcon(holder.hovered);
      if (holder.icon != icon) repaint(holder.setIcon(icon));
    }
  }

  private void handleMouse(MouseEvent event, boolean run) {
    JTextComponent component = getComponent();
    if (component != null) {
      boolean invalid = false;
      boolean repaint = false;
      IconHolder result = null;
      for (IconHolder holder : icons.values()) {
        holder.hovered = component.isEnabled() && holder.bounds.contains(event.getX(), event.getY());
        if (holder.hovered) result = holder;
        Icon icon = holder.extension.getIcon(holder.hovered);
        if (holder.icon != icon) {
          if (holder.setIcon(icon)) invalid = true;
          repaint = true;
        }
      }
      if (repaint) repaint(invalid);
      Runnable action = result == null ? null : result.extension.getActionOnClick();
      if (action == null) {
        setCursor(Cursor.TEXT_CURSOR);
      }
      else {
        setCursor(Cursor.HAND_CURSOR);
        if (run) {
          action.run();
          event.consume();
        }
      }
    }
  }

  private void setCursor(int cursor) {
    if (this.cursor != cursor) {
      this.cursor = cursor; // do not update cursor every time
      JTextComponent component = getComponent();
      if (component != null) component.setCursor(Cursor.getPredefinedCursor(cursor));
    }
  }

  private void setVariant(Object variant) {
    if (!Objects.equals(this.variant, variant)) {
      this.variant = variant;

      icons.clear();
      insets.set(0, 0, 0, 0);
      if (ExtendableTextField.VARIANT.equals(variant)) {
        JTextComponent component = getComponent();
        if (component instanceof ExtendableTextField) {
          ExtendableTextField field = (ExtendableTextField)component;
          for (Extension extension : field.getExtensions()) {
            if (extension != null) addExtension(extension);
          }
        }
      }
      else if ("search".equals(variant) && (this instanceof MacIntelliJTextFieldUI || this instanceof WinIntelliJTextFieldUI)) {
        addExtension(new SearchExtension());
        addExtension(new ClearExtension());
      }
    }
  }

  private void addExtension(Extension extension) {
    icons.put(extension.toString(), new IconHolder(extension));
    if (extension.isIconBeforeText()) {
      insets.left += extension.getPreferredSpace();
    }
    else {
      insets.right += extension.getPreferredSpace();
    }
  }

  private void setMonospaced(Object value) {
    boolean monospaced = Boolean.TRUE.equals(value);
    if (this.monospaced != monospaced) {
      this.monospaced = monospaced;
      JTextComponent component = getComponent();
      if (component != null) {
        Font font = component.getFont();
        if (font == null || font instanceof UIResource) {
          font = UIManager.getFont(getPropertyPrefix() + ".font");
          component.setFont(!monospaced ? font : new FontUIResource("monospaced", font.getStyle(), font.getSize()));
        }
      }
    }
  }


  public static final class IconHolder {
    public final Rectangle bounds = new Rectangle();
    public final Extension extension;
    public boolean hovered;
    public Icon icon;

    private IconHolder(Extension extension) {
      this.extension = extension;
      if (extension instanceof SearchExtension) {
        SearchExtension se = (SearchExtension)extension;
        se.bounds = bounds;
      }
      setIcon(extension.getIcon(false));
    }

    private boolean setIcon(Icon icon) {
      this.icon = icon;
      int width = icon == null ? 0 : icon.getIconWidth();
      int height = icon == null ? 0 : icon.getIconHeight();
      if (bounds.width == width && bounds.height == height) return false;
      bounds.width = width;
      bounds.height = height;
      return true;
    }

    public boolean isClickable() {
      return null != extension.getActionOnClick();
    }
  }


  private final class SearchExtension implements Extension {
    private Rectangle bounds; // should be bound to IconHandler#bounds

    @Override
    public Icon getIcon(boolean hovered) {
      return getSearchIcon(hovered, null != getActionOnClick());
    }

    @Override
    public int getIconGap() {
      return getSearchIconGap();
    }

    @Override
    public int getPreferredSpace() {
      return getSearchIconPreferredSpace();
    }

    @Override
    public boolean isIconBeforeText() {
      return true;
    }

    @Override
    public Runnable getActionOnClick() {
      JTextComponent component = getComponent();
      Object property = component == null ? null : component.getClientProperty(POPUP);
      JPopupMenu popup = property instanceof JPopupMenu ? (JPopupMenu)property : null;
      return popup == null ? null : () -> {
        Rectangle editor = getVisibleEditorRect();
        if (editor != null) popup.show(component, bounds.x, editor.y + editor.height);
      };
    }

    @Override
    public String getTooltip() {
      String prefix = null;
      if (UIUtil.getClientProperty(getComponent(), INPLACE_HISTORY ) != null) prefix = "Recent Search";
      if (getActionOnClick() != null) prefix = "Search History";
      return (prefix == null) ? null : prefix + " (" + KeymapUtil.getKeystrokeText(SearchTextField.SHOW_HISTORY_KEYSTROKE) + ")";
    }

    @Override
    public String toString() {
      return "search";
    }
  }

  private class ClearExtension implements Extension {
    @Override
    public Icon getIcon(boolean hovered) {
      return getClearIcon(hovered, hasText());
    }

    @Override
    public int getIconGap() {
      return getClearIconGap();
    }

    @Override
    public int getPreferredSpace() {
      return getClearIconPreferredSpace();
    }

    @Override
    public Runnable getActionOnClick() {
      JTextComponent component = getComponent();
      return component == null ? null : () -> {
        component.setText(null);
        Object property = component.getClientProperty(ON_CLEAR);
        if (property instanceof ActionListener) {
          ActionListener listener = (ActionListener)property;
          listener.actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, "clear"));
        }
      };
    }

    @Override
    public String toString() {
      return "clear";
    }
  }

  static class MyCaret extends BasicCaret {
    private final JTextComponent myComponent;

    public MyCaret(JTextComponent component) {
      myComponent = component;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      if (e.getID() == MouseEvent.MOUSE_DRAGGED && !myComponent.getText().contains("\n")) {
        boolean consumed = e.isConsumed();
        e = new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), e.getModifiers() | e.getModifiersEx(), e.getX(),
                           myComponent.getHeight() / 2,
                           e.getClickCount(), e.isPopupTrigger(), e.getButton());
        if (consumed) e.consume();
      }
      super.mouseDragged(e);
    }
  }
}
