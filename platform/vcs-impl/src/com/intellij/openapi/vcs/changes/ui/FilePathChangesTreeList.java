/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.util.List;

public class FilePathChangesTreeList extends ChangesTreeList<FilePath> {
  private final Project myProject;

  public FilePathChangesTreeList(@NotNull Project project, @NotNull List<FilePath> originalFiles,
                                 boolean showCheckboxes, boolean highlightProblems,
                                 @Nullable Runnable inclusionListener, @Nullable ChangeNodeDecorator nodeDecorator) {
    super(project, originalFiles, showCheckboxes, highlightProblems, inclusionListener, nodeDecorator);
    myProject = project;
  }

  protected DefaultTreeModel buildTreeModel(final List<FilePath> changes, ChangeNodeDecorator changeNodeDecorator) {
    return new TreeModelBuilder(myProject, false).buildModelFromFilePaths(changes);
  }

  protected List<FilePath> getSelectedObjects(final ChangesBrowserNode<FilePath> node) {
    return node.getAllFilePathsUnder();
  }

  @Nullable
  protected FilePath getLeadSelectedObject(final ChangesBrowserNode node) {
    final Object userObject = node.getUserObject();
    if (userObject instanceof FilePath) {
      return (FilePath) userObject;
    }
    return null;
  }
}
