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
package consulo.web.gwt.shared.transport;

import consulo.annotations.DeprecationInfo;

import java.io.Serializable;

/**
 * @author VISTALL
 * @since 17-May-16
 */
@Deprecated
@DeprecationInfo("This is part of research 'consulo as web app'. Code was written in hacky style. Must be dropped, or replaced by Consulo UI API")
public class GwtColor implements Serializable {
  private int myRed;
  private int myGreen;
  private int myBlue;

  public GwtColor(int red, int green, int blue) {
    myRed = red;
    myBlue = blue;
    myGreen = green;
  }

  public GwtColor() {
  }


  public int getRed() {
    return myRed;
  }

  public int getGreen() {
    return myGreen;
  }

  public int getBlue() {
    return myBlue;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("GwtColor{");
    sb.append("myRed=").append(myRed);
    sb.append(", myGreen=").append(myGreen);
    sb.append(", myBlue=").append(myBlue);
    sb.append('}');
    return sb.toString();
  }
}
