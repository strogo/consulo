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
package consulo.ui.internal;

import com.vaadin.ui.AbstractComponentContainer;
import com.vaadin.ui.Component;
import consulo.ui.Menu;
import consulo.ui.MenuItem;
import consulo.ui.RequiredUIAccess;
import consulo.ui.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author VISTALL
 * @since 14-Jun-16
 */
public class WGwtMenuImpl extends AbstractComponentContainer implements Menu, VaadinWrapper {
  private List<Component> myMenuItems = new ArrayList<>();

  public WGwtMenuImpl(String text) {
    getState().caption = text;
  }

  @RequiredUIAccess
  @NotNull
  @Override
  public Menu add(@NotNull MenuItem menuItem) {
    myMenuItems.add((Component)menuItem);
    addComponent((Component)menuItem);
    return this;
  }

  @RequiredUIAccess
  @NotNull
  @Override
  public Menu separate() {
    WGwtMenuSeparatorImpl separator = new WGwtMenuSeparatorImpl();
    myMenuItems.add(separator);
    addComponent(separator);
    return this;
  }

  @Override
  public void replaceComponent(Component oldComponent, Component newComponent) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getComponentCount() {
    return myMenuItems.size();
  }

  @Override
  public Iterator<Component> iterator() {
    return myMenuItems.iterator();
  }

  @NotNull
  @Override
  public String getText() {
    return getState().caption;
  }

  @Nullable
  @Override
  public consulo.ui.Component getParentComponent() {
    return (consulo.ui.Component)getParent();
  }

  @RequiredUIAccess
  @Override
  public void setSize(@NotNull Size size) {

  }
}
