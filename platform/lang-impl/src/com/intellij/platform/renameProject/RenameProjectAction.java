package com.intellij.platform.renameProject;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.refactoring.RefactoringBundle;

/**
 * @author lene
 *         Date: 03.10.11
 */
public class RenameProjectAction extends DumbAwareAction {

  public RenameProjectAction() {
    super(RefactoringBundle.message("rename.project.action.title"), RefactoringBundle.message("renames.project"), null);
  }

  private static final Logger LOG = Logger.getInstance("#" + RenameProjectAction.class.getName());

  @Override
  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    e.getPresentation().setEnabled(project != null && !project.isDefault());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    LOG.assertTrue(project instanceof ProjectEx);
    Module module;
    Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length == 1 && project.getName().equals(modules[0].getName())) {
      module = modules[0];
    }
    else {
      module = null;
    }
    Messages.showInputDialog(project, RefactoringBundle.message("enter.new.project.name"), RefactoringBundle.message("rename.project"),
                             Messages.getQuestionIcon(),
                             project.getName(),
                             new RenameProjectHandler.MyInputValidator((ProjectEx)project, module));
  }
}
