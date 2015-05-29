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
package com.intellij.ide.extensionResources;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.scratch.RootType;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchFileService.Option;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;

public class ExtensionsRootType extends RootType {
  public static final String EXTENSIONS_PATH = "extensions";
  public static final String BACKUP_FILE_EXTENSION = "old";

  static final Logger LOG = Logger.getInstance(ExtensionsRootType.class);
  private static final String HASH_ALGORITHM = "MD5";

  ExtensionsRootType() {
    super(EXTENSIONS_PATH, "Extensions");
  }

  @NotNull
  public static ExtensionsRootType getInstance() {
    return findByClass(ExtensionsRootType.class);
  }

  @Nullable
  public PluginId getOwnerPluginId(@Nullable VirtualFile virtualFile) {
    VirtualFile file = getPluginResourcesDirectory(virtualFile);
    return file != null ? PluginId.findId(file.getName()) : null;
  }

  @Nullable
  public String getPath(@Nullable VirtualFile file) {
    VirtualFile pluginResourcesDir = getPluginResourcesDirectory(file);
    PluginId pluginId = getOwnerPluginId(pluginResourcesDir);
    return pluginResourcesDir != null && pluginId != null ? VfsUtilCore.getRelativePath(file, pluginResourcesDir) : null;
  }

  @Nullable
  public VirtualFile findExtension(@NotNull PluginId pluginId, @NotNull String path, boolean createIfMissing) throws IOException {
    return findFile(null, pluginId.getIdString() + "/" + path, createIfMissing ? Option.create_if_missing : Option.existing_only);
  }

  @Nullable
  public VirtualFile findExtensionsDirectory(@NotNull PluginId pluginId, @NotNull String path, boolean createIfMissing) throws IOException {
    String resourceDirPath = getPath(pluginId, path);
    LocalFileSystem fs = LocalFileSystem.getInstance();
    VirtualFile file = fs.refreshAndFindFileByPath(resourceDirPath);
    if (file == null && createIfMissing) {
      return VfsUtil.createDirectories(resourceDirPath);
    }
    return file != null && file.isDirectory() ? file : null;
  }

  public void extractBundledExtensions(@NotNull PluginId pluginId, @NotNull String path) throws IOException {
    VirtualFile resourcesDirectory = findExtensionsDirectory(pluginId, path, true);
    if (resourcesDirectory == null) return;

    IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    ClassLoader classLoader = plugin != null ? plugin.getPluginClassLoader() : null;
    if (classLoader == null) return;

    Application application = ApplicationManager.getApplication();

    Enumeration<URL> bundledResources = classLoader.getResources(EXTENSIONS_PATH + "/" + path);
    while (bundledResources.hasMoreElements()) {
      URL bundledResourceDirUrl = bundledResources.nextElement();
      VirtualFile bundledResourcesDir = VfsUtil.findFileByURL(bundledResourceDirUrl);
      if (!bundledResourcesDir.isDirectory()) continue;

      AccessToken token = application.acquireWriteActionLock(ExtensionsRootType.class);
      try {
        FileDocumentManager.getInstance().saveAllDocuments();
        extractResources(bundledResourcesDir, resourcesDirectory);
      }
      finally {
        token.finish();
      }
    }
  }

  @Nullable
  @Override
  public String substituteName(@NotNull Project project, @NotNull VirtualFile file) {
    try {
      VirtualFile resourcesDir = getPluginResourcesDirectory(file);
      if (file.equals(resourcesDir)) {
        String name = getPluginResourcesRootName(resourcesDir);
        if (name != null) {
          return name;
        }
      }
    }
    catch (IOException ignore) {
    }
    return super.substituteName(project, file);
  }

  @Nullable
  private String getPluginResourcesRootName(VirtualFile resourcesDir) throws IOException {
    PluginId ownerPluginId = getOwnerPluginId(resourcesDir);
    if (ownerPluginId == null) return null;

    if ("com.intellij".equals(ownerPluginId.getIdString())) {
      return PlatformUtils.getPlatformPrefix();
    }

    IdeaPluginDescriptor plugin = PluginManager.getPlugin(ownerPluginId);
    if (plugin != null) {
      return plugin.getName();
    }

    return null;
  }

  @Nullable
  private VirtualFile getPluginResourcesDirectory(@Nullable VirtualFile virtualFile) {
    if (virtualFile == null) return null;

    VirtualFile root = getRootDirectory();
    if (root == null) return null;

    VirtualFile parent = virtualFile;
    VirtualFile file = virtualFile;
    while (parent != null && !root.equals(parent)) {
      file = parent;
      parent = file.getParent();
    }
    return parent != null && file.isDirectory() ? file : null;
  }

  @Nullable
  private VirtualFile getRootDirectory() {
    try {
      return VfsUtil.createDirectories(ScratchFileService.getInstance().getRootPath(this));
    }
    catch (IOException e) {
      LOG.warn("Cannot initialize extension resources root directory", e);
    }
    return null;
  }

  @NotNull
  private String getPath(@NotNull PluginId pluginId, @NotNull String path) {
    return ScratchFileService.getInstance().getRootPath(this) + "/" + pluginId.getIdString() + "/" + path;
  }

  private static void extractResources(@NotNull VirtualFile from, @NotNull VirtualFile to) throws IOException {
    @SuppressWarnings("UnsafeVfsRecursion") VirtualFile[] fromChildren = from.getChildren();
    for (VirtualFile fromChild : fromChildren) {
      if (fromChild.is(VFileProperty.SYMLINK) || fromChild.is(VFileProperty.SPECIAL)) continue;

      VirtualFile toChild = to.findChild(fromChild.getName());
      if (toChild != null && fromChild.isDirectory() != toChild.isDirectory()) {
        renameToBackupCopy(toChild);
        toChild = null;
      }

      if (fromChild.isDirectory()) {
        if (toChild == null) {
          toChild = to.createChildDirectory(ExtensionsRootType.class, fromChild.getName());
        }
        extractResources(fromChild, toChild);
      }
      else {
        if (toChild != null) {
          String fromHash = hash(fromChild);
          String toHash = hash(toChild);
          boolean upToDate = fromHash != null && toHash != null && StringUtil.equals(fromHash, toHash);
          if (upToDate) {
            continue;
          }
          else {
            renameToBackupCopy(toChild);
          }
        }
        toChild = to.createChildData(ExtensionsRootType.class, fromChild.getName());
        toChild.setBinaryContent(fromChild.contentsToByteArray());
      }
    }
  }

  @Nullable
  private static String hash(@NotNull VirtualFile file) throws IOException {
    try {
      MessageDigest md5 = MessageDigest.getInstance(HASH_ALGORITHM);
      StringBuilder sb = new StringBuilder();
      byte[] digest = md5.digest(file.contentsToByteArray());
      for (byte b : digest) {
        sb.append(Integer.toHexString(b));
      }
      return sb.toString();
    }
    catch (NoSuchAlgorithmException e) {
      LOG.error("Hash algorithm " + HASH_ALGORITHM + " is not supported." + e);
      return null;
    }
  }

  private static void renameToBackupCopy(@NotNull VirtualFile virtualFile) throws IOException {
    VirtualFile parent = virtualFile.getParent();
    int i = 0;
    String newName = virtualFile.getName() + "." + BACKUP_FILE_EXTENSION;
    while (parent.findChild(newName) != null) {
      newName = virtualFile.getName() + "." + BACKUP_FILE_EXTENSION + "_" + i;
      i++;
    }
    virtualFile.rename(ExtensionsRootType.class, newName);
  }
}
