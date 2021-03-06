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
package consulo.ui.internal.image;

import com.intellij.openapi.util.IconLoader;
import consulo.ui.image.Image;
import consulo.ui.internal.WGwtUIThreadLocal;
import consulo.web.gwt.shared.ui.state.image.ImageState;
import consulo.web.gwt.shared.ui.state.image.MultiImageState;
import consulo.web.servlet.ui.UIServlet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.net.URL;

/**
 * @author VISTALL
 * @since 13-Jun-16
 */
public class WGwtImageImpl implements Image, WGwtImageWithState {
  private Icon myIcon;
  private int myURLHash;

  public WGwtImageImpl(@NotNull URL url) {
    myIcon = IconLoader.findIcon(url);
    myURLHash = WGwtImageUrlCache.hashCode(url);
  }

  @Override
  public int getHeight() {
    return myIcon.getIconHeight();
  }

  @Override
  public int getWidth() {
    return myIcon.getIconWidth();
  }

  @Override
  public void toState(MultiImageState m) {
    ImageState state = new ImageState();
    UIServlet.UIImpl current = (UIServlet.UIImpl)WGwtUIThreadLocal.getUI();
    state.myURL = WGwtImageUrlCache.createURL(myURLHash, current.getURLPrefix());

    m.myImageState = state;
  }
}
