/*
 * Copyright 2013-2017 consulo.io
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
package consulo.web.gwt.client.ui;

import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.SplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;

/**
 * @author VISTALL
 * @since 13-Sep-17
 *
 * idea from https://stackoverflow.com/a/16403608/3129079
 */
public abstract class GwtSplitLayoutImpl extends SplitLayoutPanel {
  private SimplePanel myFirstWidget = new SimplePanel();
  private SimplePanel mySecondWidget = new SimplePanel();

  private int myProportion = 50;
  private int myLastElementSize;

  public GwtSplitLayoutImpl() {
    this(1);
  }

  protected abstract int getElementSize(Widget widget);

  protected abstract Direction getDirection();

  public GwtSplitLayoutImpl(int splitterSize) {
    super(splitterSize);

    myFirstWidget.setSize("100%", "100%");
    mySecondWidget.setSize("100%", "100%");

    insert(myFirstWidget, getDirection(), 300, null);

    add(mySecondWidget);
  }

  public void setFirstWidget(Widget widget) {
    widget.setSize("100%", "100%");
    myFirstWidget.clear();
    myFirstWidget.add(widget);
    setWidgetToggleDisplayAllowed(myFirstWidget, true);
  }

  public void setSecondWidget(Widget widget) {
    widget.setSize("100%", "100%");
    mySecondWidget.clear();
    mySecondWidget.add(widget);
  }

  public void setSplitPosition(String percent) {
    percent = percent.replace("%", "");
    int p = Integer.parseInt(percent);
    myProportion = p;

    if (!isAttached()) {
      return;
    }

    int elementHeight = getElementSize(this);
    if (elementHeight == 0) {
      elementHeight = myLastElementSize;
    }
    else {
      myLastElementSize = elementHeight;
    }

    double size = (elementHeight * p) / 100.0;
    setWidgetSize(myFirstWidget, size);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    setSplitPosition(myProportion + "%");
  }

  @Override
  public void onResize() {
    super.onResize();

    int thisElementSize = getElementSize(this);
    if (thisElementSize == 0) {
      thisElementSize = myLastElementSize;
    }
    else {
      myLastElementSize = thisElementSize;
    }

    double elementSize = getElementSize(myFirstWidget);

    myProportion = (int)(elementSize / thisElementSize * 100.);
  }

  public void updateOnResize() {
    setSplitPosition(myProportion + "%");
  }
}