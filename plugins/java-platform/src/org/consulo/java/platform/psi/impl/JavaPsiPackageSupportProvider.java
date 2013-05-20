/*
 * Copyright 2013 Consulo.org
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
package org.consulo.java.platform.psi.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiManager;
import org.consulo.java.platform.module.extension.JavaModuleExtension;
import org.consulo.psi.PsiPackage;
import org.consulo.psi.PsiPackageManager;
import org.consulo.psi.PsiPackageSupportProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author VISTALL
 * @since 8:43/20.05.13
 */
public class JavaPsiPackageSupportProvider implements PsiPackageSupportProvider {
  @Override
  public boolean isSupported(@NotNull Module module) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    return moduleRootManager.getExtension(JavaModuleExtension.class) != null;
  }

  @NotNull
  @Override
  public PsiPackage createPackage(@NotNull PsiManager psiManager, @NotNull PsiPackageManager packageManager, @NotNull String packageName) {
    return new JavaPsiPackageImpl(psiManager, packageManager, packageName);
  }
}
