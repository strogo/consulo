/*
 * Copyright 2013-2016 consulo.io
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
package consulo.web.gwt.client.util;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.ui.*;
import com.vaadin.client.ComponentConnector;
import com.vaadin.client.ui.AbstractComponentContainerConnector;
import consulo.annotations.DeprecationInfo;
import consulo.web.gwt.client.ui.WidgetWithUpdateUI;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 20-May-16
 */
public class GwtUIUtil {
  public static Widget loadingPanel() {
    // http://tobiasahlin.com/spinkit/
    // MIT
    FlowPanel flowPanel = new FlowPanel();
    flowPanel.addStyleName("sk-cube-grid");

    for(int i = 1; i <= 9; i++) {
      FlowPanel child = new FlowPanel();
      child.addStyleName("sk-cube sk-cube" + i);
      flowPanel.add(child);
    }

    FlowPanel container = GwtUIUtil.fillAndReturn(new FlowPanel());
    container.getElement().getStyle().setProperty("display", "flex");
    container.getElement().getStyle().setProperty("justifyContent", "center");

    flowPanel.getElement().getStyle().setProperty("alignSelf", "center");
    container.add(flowPanel);
    return container;
  }

  @Deprecated
  @DeprecationInfo("This is part of research 'consulo as web app'. Code was written in hacky style. Must be dropped, or replaced by Consulo UI API")
  public static Widget loadingPanelDeprecated() {
    VerticalPanel verticalPanel = fillAndReturn(new VerticalPanel());
    verticalPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
    verticalPanel.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);

    verticalPanel.add(new Label("Loading..."));

    return verticalPanel;
  }

  @NotNull
  public static List<Widget> remapWidgets(AbstractComponentContainerConnector abstractLayout) {
    List<Widget> widgets = new ArrayList<>();
    for (ComponentConnector connector : abstractLayout.getChildComponents()) {
      widgets.add(connector.getWidget());
    }

    return widgets;
  }

  public static Widget getWidget(Element element) {
    EventListener listener = DOM.getEventListener(element);

    if (listener == null) {
      return null;
    }
    if (listener instanceof Widget) {
      return (Widget)listener;
    }
    return null;
  }

  public static Widget icon(List<String> icons) {
    FlowPanel panel = new FlowPanel();
    panel.setStyleName("imageWrapper");

    for (String icon : icons) {
      Image image = new Image("/icon?path=\"" + icon + "\"");
      image.setStyleName("overlayImage");

      panel.add(image);
    }
    return panel;
  }

  @Deprecated
  @DeprecationInfo("This is part of research 'consulo as web app'. Code was written in hacky style. Must be dropped, or replaced by Consulo UI API")
  public static void updateUI(Widget widget) {
    if (widget instanceof WidgetWithUpdateUI) {
      ((WidgetWithUpdateUI)widget).updateUI();
    }

    if (widget instanceof HasWidgets) {
      for (Widget child : (HasWidgets)widget) {
        updateUI(child);
      }
    }

    if (widget instanceof Grid) {
      Grid grid = (Grid)widget;
      for (int c = 0; c < grid.getColumnCount(); c++) {
        for (int r = 0; r < grid.getRowCount(); r++) {
          Widget temp = grid.getWidget(r, c);

          if (temp != null) {
            updateUI(temp);
          }
        }
      }
    }
  }

  public static <T extends UIObject> T fillAndReturn(T object) {
    object.setWidth("100%");
    object.setHeight("100%");
    return object;
  }

  public static void fill(UIObject object) {
    object.setWidth("100%");
    object.setHeight("100%");
  }
}
