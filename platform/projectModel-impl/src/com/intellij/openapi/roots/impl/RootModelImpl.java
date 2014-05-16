/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.consulo.module.extension.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author dsl
 */
public class RootModelImpl extends RootModelBase implements ModifiableRootModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.RootModelImpl");

  private final Set<ContentEntry> myContent = new TreeSet<ContentEntry>(ContentComparator.INSTANCE);

  private final List<OrderEntry> myOrderEntries = new Order();
  // cleared by myOrderEntries modification, see Order
  @Nullable private OrderEntry[] myCachedOrderEntries;

  @NotNull private final ModuleLibraryTable myModuleLibraryTable;
  final ModuleRootManagerImpl myModuleRootManager;
  private boolean myWritable;
  private final VirtualFilePointerManager myFilePointerManager;
  private boolean myDisposed = false;
  private final Set<ModuleExtension<?>> myExtensions = new LinkedHashSet<ModuleExtension<?>>();
  private final List<Element> myUnknownModuleExtensions = new SmartList<Element>();

  private final RootConfigurationAccessor myConfigurationAccessor;

  private final ProjectRootManagerImpl myProjectRootManager;
  // have to register all child disposables using this fake object since all clients just call ModifiableModel.dispose()
  private final CompositeDisposable myDisposable = new CompositeDisposable();

  RootModelImpl(@NotNull ModuleRootManagerImpl moduleRootManager,
                ProjectRootManagerImpl projectRootManager,
                VirtualFilePointerManager filePointerManager) {
    myModuleRootManager = moduleRootManager;
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;

    myWritable = false;

    addSourceOrderEntries();
    myModuleLibraryTable = new ModuleLibraryTable(this, myProjectRootManager);

    for (ModuleExtensionProviderEP providerEP : ModuleExtensionProviderEP.EP_NAME.getExtensions()) {
      ModuleExtension<?> immutable = providerEP.createImmutable(this);
      if(immutable == null) {
        continue;
      }
      myExtensions.add(immutable);
    }
    myConfigurationAccessor = new RootConfigurationAccessor();
  }

  RootModelImpl(@NotNull Element element,
                @NotNull ModuleRootManagerImpl moduleRootManager,
                ProjectRootManagerImpl projectRootManager,
                VirtualFilePointerManager filePointerManager,
                boolean writable) throws InvalidDataException {
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;
    myModuleRootManager = moduleRootManager;

    myModuleLibraryTable = new ModuleLibraryTable(this, myProjectRootManager);

    RootModelImpl originalRootModel = moduleRootManager.getRootModel();

    createMutableExtensions(originalRootModel);

    List<Element> moduleExtensionChild = element.getChildren("extension");

    for (Element child : moduleExtensionChild) {
      final String id = child.getAttributeValue("id");

      ModuleExtensionProviderEP providerEP = ModuleExtensionProviderEP.findProviderEP(id);
      if (providerEP != null) {
        ModuleExtension rootModuleExtension = originalRootModel.getExtensionWithoutCheck(id);
        assert rootModuleExtension != null;
        //noinspection unchecked
        rootModuleExtension.loadState(child);

        ModuleExtension moduleExtension = getExtensionWithoutCheck(id);
        assert moduleExtension != null;
        //noinspection unchecked
        moduleExtension.commit(rootModuleExtension);
      }
      else {
        myUnknownModuleExtensions.add(child.clone());
      }
    }

    final List<Element> contentChildren = element.getChildren(ContentEntryImpl.ELEMENT_NAME);
    for (Element child : contentChildren) {
      ContentEntryImpl contentEntry = new ContentEntryImpl(child, this);
      myContent.add(contentEntry);
    }

    final List<Element> orderElements = element.getChildren(OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME);
    boolean moduleSourceAdded = false;
    for (Element child : orderElements) {
      final OrderEntry orderEntry = OrderEntryFactory.createOrderEntryByElement(child, this, myProjectRootManager);
      if (orderEntry == null) {
        continue;
      }
      if (orderEntry instanceof ModuleSourceOrderEntry) {
        if (moduleSourceAdded) {
          continue;
        }
        moduleSourceAdded = true;
      }
      myOrderEntries.add(orderEntry);
    }

    if (!moduleSourceAdded) {
      myOrderEntries.add(new ModuleSourceOrderEntryImpl(this));
    }


    myWritable = writable;

    myConfigurationAccessor = new RootConfigurationAccessor();
  }

  //creates modifiable model
  RootModelImpl(@NotNull RootModelImpl rootModel,
                ModuleRootManagerImpl moduleRootManager,
                final RootConfigurationAccessor rootConfigurationAccessor,
                @NotNull VirtualFilePointerManager filePointerManager,
                ProjectRootManagerImpl projectRootManager) {
    myFilePointerManager = filePointerManager;
    myModuleRootManager = moduleRootManager;
    myProjectRootManager = projectRootManager;

    myModuleLibraryTable = new ModuleLibraryTable(this, myProjectRootManager);

    myWritable = true;
    myConfigurationAccessor = rootConfigurationAccessor;

    final Set<ContentEntry> thatContent = rootModel.myContent;
    for (ContentEntry contentEntry : thatContent) {
      if (contentEntry instanceof ClonableContentEntry) {
        ContentEntry cloned = ((ClonableContentEntry)contentEntry).cloneEntry(this);
        myContent.add(cloned);
      }
    }

    createMutableExtensions(rootModel);

    setOrderEntriesFrom(rootModel);
  }

  @SuppressWarnings("unchecked")
  private void createMutableExtensions(RootModelImpl rootModel) {
    for (ModuleExtensionProviderEP providerEP : ModuleExtensionProviderEP.EP_NAME.getExtensions()) {
      final ModuleExtension<?> originalExtension = rootModel.getExtensionWithoutCheck(providerEP.getKey());
      assert originalExtension != null;
      MutableModuleExtension mutable = providerEP.createMutable(this);
      if(mutable == null) {
        continue;
      }

      mutable.commit(originalExtension);

      myExtensions.add(mutable);
    }
  }

  private void addSourceOrderEntries() {
    myOrderEntries.add(new ModuleSourceOrderEntryImpl(this));
  }

  @Override
  public boolean isWritable() {
    return myWritable;
  }

  public RootConfigurationAccessor getConfigurationAccessor() {
    return myConfigurationAccessor;
  }

  private void setOrderEntriesFrom(@NotNull RootModelImpl rootModel) {
    removeAllOrderEntries();
    for (OrderEntry orderEntry : rootModel.myOrderEntries) {
      if (orderEntry instanceof ClonableOrderEntry) {
        myOrderEntries.add(((ClonableOrderEntry)orderEntry).cloneEntry(this, myProjectRootManager, myFilePointerManager));
      }
    }
  }

  private void removeAllOrderEntries() {
    for (OrderEntry entry : myOrderEntries) {
      Disposer.dispose((OrderEntryBaseImpl)entry);
    }
    myOrderEntries.clear();
  }

  @Override
  @NotNull
  public OrderEntry[] getOrderEntries() {
    OrderEntry[] cachedOrderEntries = myCachedOrderEntries;
    if (cachedOrderEntries == null) {
      myCachedOrderEntries = cachedOrderEntries = myOrderEntries.toArray(new OrderEntry[myOrderEntries.size()]);
    }
    return cachedOrderEntries;
  }

  Iterator<OrderEntry> getOrderIterator() {
    return Collections.unmodifiableList(myOrderEntries).iterator();
  }

  @Override
  public void removeContentEntry(@NotNull ContentEntry entry) {
    assertWritable();
    LOG.assertTrue(myContent.contains(entry));
    if (entry instanceof RootModelComponentBase) {
      Disposer.dispose((RootModelComponentBase)entry);
    }
    myContent.remove(entry);
  }

  @Override
  public void addOrderEntry(@NotNull OrderEntry entry) {
    assertWritable();
    LOG.assertTrue(!myOrderEntries.contains(entry));
    myOrderEntries.add(entry);
  }

  @NotNull
  @Override
  public LibraryOrderEntry addLibraryEntry(@NotNull Library library) {
    assertWritable();
    final LibraryOrderEntry libraryOrderEntry = new LibraryOrderEntryImpl(library, this, myProjectRootManager);
    assert libraryOrderEntry.isValid();
    myOrderEntries.add(libraryOrderEntry);
    return libraryOrderEntry;
  }

  @NotNull
  @Override
  public ModuleExtensionWithSdkOrderEntry addModuleExtensionSdkEntry(@NotNull ModuleExtensionWithSdk<?> moduleExtension) {
    assertWritable();
    final ModuleExtensionWithSdkOrderEntryImpl moduleSdkOrderEntry =
      new ModuleExtensionWithSdkOrderEntryImpl(moduleExtension.getId(), this);
    assert moduleSdkOrderEntry.isValid();

    // add module extension sdk entry after another SDK entry or before module source
    int sourcePosition = -1, sdkPosition = -1;
    for (int j = 0; j < myOrderEntries.size(); j++) {
      OrderEntry orderEntry = myOrderEntries.get(j);
      if (orderEntry instanceof ModuleSourceOrderEntry) {
        sourcePosition = j;
      }
      else if (orderEntry instanceof ModuleExtensionWithSdkOrderEntry) {
        sdkPosition = j;
      }
    }

    if (sdkPosition >= 0) {
      myOrderEntries.add(sdkPosition + 1, moduleSdkOrderEntry);
    }
    else if (sourcePosition >= 0) {
      myOrderEntries.add(sourcePosition, moduleSdkOrderEntry);
    }
    else {
      myOrderEntries.add(0, moduleSdkOrderEntry);
    }
    return moduleSdkOrderEntry;
  }

  @NotNull
  @Override
  public LibraryOrderEntry addInvalidLibrary(@NotNull String name, @NotNull String level) {
    assertWritable();
    final LibraryOrderEntry libraryOrderEntry = new LibraryOrderEntryImpl(name, level, this, myProjectRootManager);
    myOrderEntries.add(libraryOrderEntry);
    return libraryOrderEntry;
  }

  @NotNull
  @Override
  public ModuleOrderEntry addModuleOrderEntry(@NotNull Module module) {
    assertWritable();
    LOG.assertTrue(!module.equals(getModule()));
    LOG.assertTrue(Comparing.equal(myModuleRootManager.getModule().getProject(), module.getProject()));
    final ModuleOrderEntryImpl moduleOrderEntry = new ModuleOrderEntryImpl(module, this);
    myOrderEntries.add(moduleOrderEntry);
    return moduleOrderEntry;
  }

  @NotNull
  @Override
  public ModuleOrderEntry addInvalidModuleEntry(@NotNull String name) {
    assertWritable();
    LOG.assertTrue(!name.equals(getModule().getName()));
    final ModuleOrderEntryImpl moduleOrderEntry = new ModuleOrderEntryImpl(name, this);
    myOrderEntries.add(moduleOrderEntry);
    return moduleOrderEntry;
  }

  @Nullable
  @Override
  public LibraryOrderEntry findLibraryOrderEntry(@NotNull Library library) {
    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry && library.equals(((LibraryOrderEntry)orderEntry).getLibrary())) {
        return (LibraryOrderEntry)orderEntry;
      }
    }
    return null;
  }

  @Override
  public ModuleExtensionWithSdkOrderEntry findModuleExtensionSdkEntry(@NotNull ModuleExtension extension) {
    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof ModuleExtensionWithSdkOrderEntry &&
          extension.getId().equals(((ModuleExtensionWithSdkOrderEntry)orderEntry).getModuleExtensionId())) {
        return (ModuleExtensionWithSdkOrderEntry)orderEntry;
      }
    }
    return null;
  }

  @Override
  public void removeOrderEntry(@NotNull OrderEntry entry) {
    assertWritable();
    removeOrderEntryInternal(entry);
  }

  private void removeOrderEntryInternal(OrderEntry entry) {
    LOG.assertTrue(myOrderEntries.contains(entry));
    Disposer.dispose((OrderEntryBaseImpl)entry);
    myOrderEntries.remove(entry);
  }

  @Override
  public void rearrangeOrderEntries(@NotNull OrderEntry[] newEntries) {
    assertWritable();
    assertValidRearrangement(newEntries);
    myOrderEntries.clear();
    ContainerUtil.addAll(myOrderEntries, newEntries);
  }

  private void assertValidRearrangement(@NotNull OrderEntry[] newEntries) {
    String error = checkValidRearrangement(newEntries);
    LOG.assertTrue(error == null, error);
  }

  @Nullable
  private String checkValidRearrangement(@NotNull OrderEntry[] newEntries) {
    if (newEntries.length != myOrderEntries.size()) {
      return "Size mismatch: old size=" + myOrderEntries.size() + "; new size=" + newEntries.length;
    }
    Set<OrderEntry> set = new HashSet<OrderEntry>();
    for (OrderEntry newEntry : newEntries) {
      if (!myOrderEntries.contains(newEntry)) {
        return "Trying to add nonexisting order entry " + newEntry;
      }

      if (set.contains(newEntry)) {
        return "Trying to add duplicate order entry " + newEntry;
      }
      set.add(newEntry);
    }
    return null;
  }

  @Override
  public void clear() {
    removeAllContentEntries();
    removeAllOrderEntries();

    addSourceOrderEntries();
  }

  private void removeAllContentEntries() {
    for (ContentEntry entry : myContent) {
      if (entry instanceof RootModelComponentBase) {
        Disposer.dispose((RootModelComponentBase)entry);
      }
    }
    myContent.clear();
  }

  @Override
  public void commit() {
    myModuleRootManager.commitModel(this);
    myWritable = false;
  }

  @SuppressWarnings("unchecked")
  public void doCommit() {
    assert isWritable();

    if (areOrderEntriesChanged()) {
      getSourceModel().setOrderEntriesFrom(this);
    }

    if (areContentEntriesChanged()) {
      getSourceModel().removeAllContentEntries();
      for (ContentEntry contentEntry : myContent) {
        ContentEntry cloned = ((ClonableContentEntry)contentEntry).cloneEntry(getSourceModel());
        getSourceModel().myContent.add(cloned);
      }
    }

    ModuleExtensionChangeListener moduleExtensionChangeListener =
            getProject().getMessageBus().syncPublisher(ModuleExtension.CHANGE_TOPIC);

    for (ModuleExtension extension : myExtensions) {
      MutableModuleExtension mutableExtension = (MutableModuleExtension)extension;

      ModuleExtension originalExtension = getSourceModel().getExtensionWithoutCheck(extension.getId());
      assert originalExtension != null;
      if (mutableExtension.isModified(originalExtension)) {
        moduleExtensionChangeListener.beforeExtensionChanged(originalExtension, mutableExtension);

        originalExtension.commit(mutableExtension);

        moduleExtensionChangeListener.afterExtensionChanged(originalExtension, mutableExtension);
      }
    }

    getSourceModel().myUnknownModuleExtensions.addAll(myUnknownModuleExtensions);
  }

  @Override
  @NotNull
  public LibraryTable getModuleLibraryTable() {
    return myModuleLibraryTable;
  }

  @Override
  public Project getProject() {
    return myProjectRootManager.getProject();
  }

  @Override
  @NotNull
  public ContentEntry addContentEntry(@NotNull VirtualFile file) {
    return addContentEntry(new ContentEntryImpl(file, this));
  }

  @Override
  @NotNull
  public ContentEntry addContentEntry(@NotNull String url) {
    return addContentEntry(new ContentEntryImpl(url, this));
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  @NotNull
  private ContentEntry addContentEntry(@NotNull ContentEntry e) {
    if (myContent.contains(e)) {
      for (ContentEntry contentEntry : getContentEntries()) {
        if (ContentComparator.INSTANCE.compare(contentEntry, e) == 0) return contentEntry;
      }
    }
    myContent.add(e);
    return e;
  }

  public void writeExternal(@NotNull Element element) {
    List<Element> moduleExtensionElements = new ArrayList<Element>();
    for (ModuleExtension<?> extension : myExtensions) {
      final Element state = extension.getState();
      if (state == null) {
        continue;
      }
      moduleExtensionElements.add(state);
    }

    for (Element unknownModuleExtension : myUnknownModuleExtensions) {
      moduleExtensionElements.add(unknownModuleExtension.clone());
    }
    Collections.sort(moduleExtensionElements, new Comparator<Element>() {
      @Override
      public int compare(Element o1, Element o2) {
        return Comparing.compare(o1.getAttributeValue("id"), o2.getAttributeValue("id"));
      }
    });

    element.addContent(moduleExtensionElements);

    for (ContentEntry contentEntry : getContent()) {
      if (contentEntry instanceof ContentEntryImpl) {
        final Element subElement = new Element(ContentEntryImpl.ELEMENT_NAME);
        ((ContentEntryImpl)contentEntry).writeExternal(subElement);
        element.addContent(subElement);
      }
    }

    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof WritableOrderEntry) {
        try {
          ((WritableOrderEntry)orderEntry).writeExternal(element);
        }
        catch (WriteExternalException e) {
          LOG.error("Failed to write order entry: " + orderEntry, e);
        }
      }
    }
  }

  @Override
  public <T extends OrderEntry> void replaceEntryOfType(@NotNull Class<T> entryClass, @Nullable final T entry) {
    assertWritable();
    for (int i = 0; i < myOrderEntries.size(); i++) {
      OrderEntry orderEntry = myOrderEntries.get(i);
      if (entryClass.isInstance(orderEntry)) {
        myOrderEntries.remove(i);
        if (entry != null) {
          myOrderEntries.add(i, entry);
        }
        return;
      }
    }

    if (entry != null) {
      myOrderEntries.add(0, entry);
    }
  }

  public void assertWritable() {
    LOG.assertTrue(myWritable);
  }

  public boolean isDependsOn(final Module module) {
    for (OrderEntry entry : getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        final Module module1 = ((ModuleOrderEntry)entry).getModule();
        if (module1 == module) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isOrderEntryDisposed() {
    for (OrderEntry entry : myOrderEntries) {
      if (entry instanceof RootModelComponentBase && ((RootModelComponentBase)entry).isDisposed()) return true;
    }
    return false;
  }

  @Override
  protected Set<ContentEntry> getContent() {
    return myContent;
  }

  private static class ContentComparator implements Comparator<ContentEntry> {
    public static final ContentComparator INSTANCE = new ContentComparator();

    @Override
    public int compare(@NotNull final ContentEntry o1, @NotNull final ContentEntry o2) {
      return o1.getUrl().compareTo(o2.getUrl());
    }
  }

  @Override
  @NotNull
  public Module getModule() {
    return myModuleRootManager.getModule();
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean isChanged() {
    if (!myWritable) return false;

    for (ModuleExtension<?> extension : myExtensions) {
      MutableModuleExtension mutableExtension = (MutableModuleExtension)extension;
      ModuleExtension<?> originalExtension = getSourceModel().getExtensionWithoutCheck(extension.getId());
      assert originalExtension != null;
      if (mutableExtension.isModified(originalExtension)) {
        return true;
      }
    }

    return areOrderEntriesChanged() || areContentEntriesChanged();
  }

  private boolean areContentEntriesChanged() {
    return ArrayUtil.lexicographicCompare(getContentEntries(), getSourceModel().getContentEntries()) != 0;
  }

  private boolean areOrderEntriesChanged() {
    OrderEntry[] orderEntries = getOrderEntries();
    OrderEntry[] sourceOrderEntries = getSourceModel().getOrderEntries();
    if (orderEntries.length != sourceOrderEntries.length) return true;
    for (int i = 0; i < orderEntries.length; i++) {
      OrderEntry orderEntry = orderEntries[i];
      OrderEntry sourceOrderEntry = sourceOrderEntries[i];
      if (!orderEntriesEquals(orderEntry, sourceOrderEntry)) {
        return true;
      }
    }
    return false;
  }

  private static boolean orderEntriesEquals(@NotNull OrderEntry orderEntry1, @NotNull OrderEntry orderEntry2) {
    if (!((OrderEntryBaseImpl)orderEntry1).sameType(orderEntry2)) return false;
    if (orderEntry1 instanceof SdkOrderEntry) {
      if (!(orderEntry2 instanceof SdkOrderEntry)) return false;

      if (orderEntry1 instanceof ModuleExtensionWithSdkOrderEntry && orderEntry2 instanceof ModuleExtensionWithSdkOrderEntry) {
        String name1 = ((ModuleExtensionWithSdkOrderEntry)orderEntry1).getSdkName();
        String name2 = ((ModuleExtensionWithSdkOrderEntry)orderEntry2).getSdkName();
        if (!Comparing.strEqual(name1, name2)) {
          return false;
        }
      }
    }
    if (orderEntry1 instanceof ExportableOrderEntry) {
      if (!(((ExportableOrderEntry)orderEntry1).isExported() == ((ExportableOrderEntry)orderEntry2).isExported())) {
        return false;
      }
      if (!(((ExportableOrderEntry)orderEntry1).getScope() == ((ExportableOrderEntry)orderEntry2).getScope())) {
        return false;
      }
    }
    if (orderEntry1 instanceof ModuleOrderEntry) {
      LOG.assertTrue(orderEntry2 instanceof ModuleOrderEntry);
      ModuleOrderEntryImpl entry1 = (ModuleOrderEntryImpl)orderEntry1;
      ModuleOrderEntryImpl entry2 = (ModuleOrderEntryImpl)orderEntry2;
      return entry1.isProductionOnTestDependency() == entry2.isProductionOnTestDependency() &&
             Comparing.equal(entry1.getModuleName(), entry2.getModuleName());
    }

    if (orderEntry1 instanceof LibraryOrderEntry) {
      LOG.assertTrue(orderEntry2 instanceof LibraryOrderEntry);
      LibraryOrderEntry libraryOrderEntry1 = (LibraryOrderEntry)orderEntry1;
      LibraryOrderEntry libraryOrderEntry2 = (LibraryOrderEntry)orderEntry2;
      boolean equal = Comparing.equal(libraryOrderEntry1.getLibraryName(), libraryOrderEntry2.getLibraryName()) &&
                      Comparing.equal(libraryOrderEntry1.getLibraryLevel(), libraryOrderEntry2.getLibraryLevel());
      if (!equal) return false;

      Library library1 = libraryOrderEntry1.getLibrary();
      Library library2 = libraryOrderEntry2.getLibrary();
      if (library1 != null && library2 != null) {
        if (!Arrays.equals(((LibraryEx)library1).getExcludedRootUrls(), ((LibraryEx)library2).getExcludedRootUrls())) {
          return false;
        }
      }
    }

    final OrderRootType[] allTypes = OrderRootType.getAllTypes();
    for (OrderRootType type : allTypes) {
      final String[] orderedRootUrls1 = orderEntry1.getUrls(type);
      final String[] orderedRootUrls2 = orderEntry2.getUrls(type);
      if (!Arrays.equals(orderedRootUrls1, orderedRootUrls2)) {
        return false;
      }
    }
    return true;
  }

  void makeExternalChange(@NotNull Runnable runnable) {
    if (myWritable || myDisposed) return;
    myModuleRootManager.makeRootsChange(runnable);
  }

  @Override
  public void dispose() {
    assert !myDisposed;
    Disposer.dispose(myDisposable);
    myExtensions.clear();
    myWritable = false;
    myDisposed = true;
  }

  private class Order extends ArrayList<OrderEntry> {
    @Override
    public void clear() {
      super.clear();
      clearCachedEntries();
    }

    @NotNull
    @Override
    public OrderEntry set(int i, @NotNull OrderEntry orderEntry) {
      super.set(i, orderEntry);
      ((OrderEntryBaseImpl)orderEntry).setIndex(i);
      clearCachedEntries();
      return orderEntry;
    }

    @Override
    public boolean add(@NotNull OrderEntry orderEntry) {
      super.add(orderEntry);
      ((OrderEntryBaseImpl)orderEntry).setIndex(size() - 1);
      clearCachedEntries();
      return true;
    }

    @Override
    public void add(int i, OrderEntry orderEntry) {
      super.add(i, orderEntry);
      clearCachedEntries();
      setIndicies(i);
    }

    @Override
    public OrderEntry remove(int i) {
      OrderEntry entry = super.remove(i);
      setIndicies(i);
      clearCachedEntries();
      return entry;
    }

    @Override
    public boolean remove(Object o) {
      int index = indexOf(o);
      if (index < 0) return false;
      remove(index);
      clearCachedEntries();
      return true;
    }

    @Override
    public boolean addAll(Collection<? extends OrderEntry> collection) {
      int startSize = size();
      boolean result = super.addAll(collection);
      setIndicies(startSize);
      clearCachedEntries();
      return result;
    }

    @Override
    public boolean addAll(int i, Collection<? extends OrderEntry> collection) {
      boolean result = super.addAll(i, collection);
      setIndicies(i);
      clearCachedEntries();
      return result;
    }

    @Override
    public void removeRange(int i, int i1) {
      super.removeRange(i, i1);
      clearCachedEntries();
      setIndicies(i);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> collection) {
      boolean result = super.removeAll(collection);
      setIndicies(0);
      clearCachedEntries();
      return result;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> collection) {
      boolean result = super.retainAll(collection);
      setIndicies(0);
      clearCachedEntries();
      return result;
    }

    private void clearCachedEntries() {
      myCachedOrderEntries = null;
    }

    private void setIndicies(int startIndex) {
      for (int j = startIndex; j < size(); j++) {
        ((OrderEntryBaseImpl)get(j)).setIndex(j);
      }
    }
  }

  private RootModelImpl getSourceModel() {
    assertWritable();
    return myModuleRootManager.getRootModel();
  }

  @Nullable
  @Override
  public <T extends ModuleExtension> T getExtension(Class<T> clazz) {
    for (ModuleExtension<?> extension : myExtensions) {
      if (extension.isEnabled() && clazz.isAssignableFrom(extension.getClass())) {
        //noinspection unchecked
        return (T)extension;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public <T extends ModuleExtension> T getExtension(@NotNull String key) {
    for (ModuleExtension<?> extension : myExtensions) {
      if (extension.isEnabled() && Comparing.equal(extension.getId(), key)) {
        //noinspection unchecked
        return (T)extension;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public <T extends ModuleExtension> T getExtensionWithoutCheck(Class<T> clazz) {
    for (ModuleExtension<?> extension : myExtensions) {
      if (clazz.isAssignableFrom(extension.getClass())) {
        //noinspection unchecked
        return (T)extension;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public <T extends ModuleExtension> T getExtensionWithoutCheck(@NotNull String key) {
    for (ModuleExtension<?> extension : myExtensions) {
      if (Comparing.equal(extension.getId(), key)) {
        //noinspection unchecked
        return (T)extension;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public ModuleExtension[] getExtensions() {
    if (myExtensions.isEmpty()) {
      return ModuleExtension.EMPTY_ARRAY;
    }
    List<ModuleExtension> list = new ArrayList<ModuleExtension>(myExtensions.size());
    for (ModuleExtension<?> extension : myExtensions) {
      if (extension.isEnabled()) {
        list.add(extension);
      }
    }
    return list.toArray(new ModuleExtension[list.size()]);
  }

  void registerOnDispose(@NotNull Disposable disposable) {
    myDisposable.add(disposable);
  }
}
