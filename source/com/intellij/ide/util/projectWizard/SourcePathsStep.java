package com.intellij.ide.util.projectWizard;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.JavaUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 6, 2004
 */
public class SourcePathsStep extends AbstractStepWithProgress<String, List<Pair<String, String>>> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.projectWizard.SourcePathsStep");
  @NonNls private static final String PROGRESS_PANEL = "progress_panel";
  @NonNls private static final String CREATE_SOURCE_PANEL = "create_source";
  @NonNls private static final String CHOOSE_SOURCE_PANEL = "choose_source";
  private static final List<Pair<String,String>> EMPTY_STRING_STRING_ARRAY = Collections.emptyList();
  private final NameLocationStep myNameLocationStep;
  private final JavaModuleBuilder myBuilder;
  private final Icon myIcon;
  private final String myHelpId;
  private JPanel myPanel;
  private JPanel myContentPanel;
  private String myCurrentMode;
  private ElementsChooser<Pair<String,String>> mySourcePathsChooser;
  private String myCurrentContentEntryPath = null;
  private JRadioButton myRbCreateSource;
  private JRadioButton myRbNoSource;
  private JTextField myTfSourceDirectoryName;
  private JTextField myTfFullPath;

  public SourcePathsStep(NameLocationStep nameLocationStep, JavaModuleBuilder builder, Icon icon, @NonNls String helpId) {
    super(IdeBundle.message("prompt.stop.searching.for.sources", ApplicationNamesInfo.getInstance().getProductName()));
    myNameLocationStep = nameLocationStep;
    myBuilder = builder;
    myIcon = icon;
    myHelpId = helpId;
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    myContentPanel = new JPanel(new CardLayout());
    myPanel.add(myContentPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
    myContentPanel.add(createComponentForEmptyRootCase(), CREATE_SOURCE_PANEL);
    myContentPanel.add(createComponentForChooseSources(), CHOOSE_SOURCE_PANEL);

    final JPanel progressPanel = createProgressPanel();

    myContentPanel.add(progressPanel, PROGRESS_PANEL);
  }

  private JComponent createComponentForEmptyRootCase() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final String text = IdeBundle.message("prompt.please.specify.java.sources.directory");

    final JLabel label = new JLabel(text);
    label.setUI(new MultiLineLabelUI());
    panel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(8, 10, 0, 10), 0, 0));

    myRbCreateSource = new JRadioButton(IdeBundle.message("radio.create.source.directory"), true);
    panel.add(myRbCreateSource, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(8, 10, 0, 10), 0, 0));

    myTfSourceDirectoryName = new JTextField(suggestSourceDirectoryName());
    final JLabel srcPathLabel = new JLabel(IdeBundle.message("prompt.enter.relative.path.to.module.content.root", File.separator));
    panel.add(srcPathLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 30, 0, 0), 0, 0));
    final FileChooserDescriptor chooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
    chooserDescriptor.setIsTreeRootVisible(true);
    final FieldPanel fieldPanel = createFieldPanel(myTfSourceDirectoryName, null, new BrowsePathListener(myTfSourceDirectoryName, chooserDescriptor));
    panel.add(fieldPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 30, 0, 10), 0, 0));

    myRbNoSource = new JRadioButton(IdeBundle.message("radio.do.not.create.source.directory"), true);
    panel.add(myRbNoSource, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(8, 10, 0, 10), 0, 0));

    final JLabel fullPathLabel = new JLabel(IdeBundle.message("label.source.directory"));
    panel.add(fullPathLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE, new Insets(8, 10, 0, 10), 0, 0));

    myTfFullPath = new JTextField();
    myTfFullPath.setEditable(false);
    final Insets borderInsets = myTfFullPath.getBorder().getBorderInsets(myTfFullPath);
    myTfFullPath.setBorder(BorderFactory.createEmptyBorder(borderInsets.top, borderInsets.left, borderInsets.bottom, borderInsets.right));
    panel.add(myTfFullPath, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.SOUTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 10, 8, 10), 0, 0));

    ButtonGroup group = new ButtonGroup();
    group.add(myRbCreateSource);
    group.add(myRbNoSource);
    myTfSourceDirectoryName.getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        updateFullPathField();
      }
    });

    myRbCreateSource.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        final boolean enabled = e.getStateChange() == ItemEvent.SELECTED;
        srcPathLabel.setEnabled(enabled);
        fieldPanel.setEnabled(enabled);
        fullPathLabel.setVisible(enabled);
        myTfFullPath.setVisible(enabled);
        if (enabled) {
          myTfSourceDirectoryName.requestFocus();
        }
      }
    });
    return panel;
  }

  @NonNls protected String suggestSourceDirectoryName() {
    return "src";
  }

  private void updateFullPathField() {
    final String sourceDirectoryPath = getSourceDirectoryPath();
    if (sourceDirectoryPath != null) {
      myTfFullPath.setText(sourceDirectoryPath.replace('/', File.separatorChar));
    }
    else {
      myTfFullPath.setText("");
    }
  }

  private JComponent createComponentForChooseSources() {
    final JPanel panel = new JPanel(new GridBagLayout());
    mySourcePathsChooser = new ElementsChooser<Pair<String, String>>(true) {
      public String getItemText(Pair<String, String> pair) {
        if ("".equals(pair.second)) return pair.first;
        return pair.first + " (" + pair.second + ")";
      }
    };
    final String text = IdeBundle.message("label.java.source.files.have.been.found");
    final JLabel label = new JLabel(text);
    label.setUI(new MultiLineLabelUI());
    panel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 10, 0, 10), 0, 0));
    panel.add(mySourcePathsChooser, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(8, 10, 8, 10), 0, 0));

    final JButton markAllButton = new JButton(IdeBundle.message("button.mark.all"));
    panel.add(markAllButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 10, 8, 2), 0, 0));

    final JButton unmarkAllButton = new JButton(IdeBundle.message("button.unmark.all"));
    panel.add(unmarkAllButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 8, 10), 0, 0));

    markAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        mySourcePathsChooser.setAllElementsMarked(true);
      }
    });
    unmarkAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        mySourcePathsChooser.setAllElementsMarked(false);
      }
    });

    return panel;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myRbCreateSource.isSelected()? myTfSourceDirectoryName : mySourcePathsChooser.getComponent();
  }

  public void updateDataModel() {
    List<Pair<String,String>> paths = null;
    if (CHOOSE_SOURCE_PANEL.equals(myCurrentMode)) {
      final List<Pair<String, String>> selectedElements = mySourcePathsChooser.getMarkedElements();
      if (selectedElements.size() > 0) {
        paths = new ArrayList<Pair<String, String>>(selectedElements.size());

        for (final Pair<String, String> path : selectedElements) {
          paths.add(Pair.create(path.first.replace(File.separatorChar, '/'), path.second));
        }
      }
    }
    else {
      if (myRbCreateSource.isSelected()) {
        final String sourceDirectoryPath = getSourceDirectoryPath();
        if (sourceDirectoryPath != null) {
          paths = Collections.singletonList(Pair.create(sourceDirectoryPath, ""));
        }
      }
    }
    if (paths != null) {
      myBuilder.setSourcePaths(paths);
    }
    else {
      myBuilder.setSourcePaths(new ArrayList<Pair<String, String>>());
    }
  }

  public boolean validate() {
    if (!super.validate()) {
      return false;
    }

    if (CREATE_SOURCE_PANEL.equals(myCurrentMode) && myRbCreateSource.isSelected()) {
      final String sourceDirectoryPath = getSourceDirectoryPath();
      final String relativePath = myTfSourceDirectoryName.getText().trim();
      if (relativePath.length() == 0) {
        String text = IdeBundle.message("prompt.relative.path.to.sources.empty", sourceDirectoryPath);
        final int answer = Messages.showDialog(myTfSourceDirectoryName, text, IdeBundle.message("title.mark.source.directory"),
                                               new String[]{IdeBundle.message("action.mark"), IdeBundle.message("action.do.not.mark"),
                                                 CommonBundle.getCancelButtonText()}, 0, Messages.getQuestionIcon());
        if (answer == 2) {
          return false; // cancel
        }
        if (answer == 1) { // don't mark
          myRbNoSource.doClick();
        }
      }
      if (sourceDirectoryPath != null) {
        final File rootDir = new File(myBuilder.getContentEntryPath());
        final File srcDir = new File(sourceDirectoryPath);
        try {
          if (!FileUtil.isAncestor(rootDir, srcDir, false)) {
            Messages.showErrorDialog(myTfSourceDirectoryName,
                                     IdeBundle.message("error.source.directory.should.be.under.module.content.root.directory"),
                                     CommonBundle.getErrorTitle());
            return false;
          }
        }
        catch (IOException e) {
          Messages.showErrorDialog(myTfSourceDirectoryName, e.getMessage(), CommonBundle.getErrorTitle());
          return false;
        }
        srcDir.mkdirs();
      }
    }
    return true;
  }

  @Nullable
  private String getSourceDirectoryPath() {
    final String contentEntryPath = myBuilder.getContentEntryPath();
    final String dirName = myTfSourceDirectoryName.getText().trim().replace(File.separatorChar, '/');
    if (contentEntryPath != null) {
      return dirName.length() > 0? contentEntryPath + "/" + dirName : contentEntryPath;
    }
    return null;
  }

  public void updateStep() {
    final String contentEntryPath = myBuilder.getContentEntryPath();
    if (isContentEntryChanged()) {
      runProgress();
    }
    else {
      updateStepUI(contentEntryPath);
    }
  }

  protected void showProgress() {
    ((CardLayout)myContentPanel.getLayout()).show(myContentPanel, PROGRESS_PANEL);
    myContentPanel.revalidate();
  }

  protected void onFinished(final List<Pair<String, String>> foundPaths, final boolean canceled) {
    if (foundPaths.size() > 0) {
      myCurrentMode = CHOOSE_SOURCE_PANEL;
      mySourcePathsChooser.setElements(foundPaths, true);
    }
    else {
      myCurrentMode = CREATE_SOURCE_PANEL;
      updateFullPathField();
    }
    updateStepUI(canceled ? null : myBuilder.getContentEntryPath());
    if (CHOOSE_SOURCE_PANEL.equals(myCurrentMode)) {
      mySourcePathsChooser.selectElements(foundPaths.subList(0, 1));
    }
    else if (CREATE_SOURCE_PANEL.equals(myCurrentMode)) {
      myTfSourceDirectoryName.selectAll();
    }
  }

  private void updateStepUI(final String contentEntryPath) {
    myCurrentContentEntryPath = contentEntryPath;
    ((CardLayout)myContentPanel.getLayout()).show(myContentPanel, myCurrentMode);
    myContentPanel.revalidate();
  }

  protected boolean isContentEntryChanged() {
    final String contentEntryPath = myBuilder.getContentEntryPath();
    return myCurrentContentEntryPath == null? contentEntryPath != null : !myCurrentContentEntryPath.equals(contentEntryPath);
  }

  protected String getParameter() {
    return myBuilder.getContentEntryPath();
  }

  protected List<Pair<String,String>> calculate(String contentEntryPath) {
    if (contentEntryPath == null) {
      return EMPTY_STRING_STRING_ARRAY;
    }
    final File entryFile = new File(contentEntryPath);
    if (!entryFile.exists()) {
      return EMPTY_STRING_STRING_ARRAY;
    }
    final File[] children = entryFile.listFiles();
    if (children == null || children.length == 0) {
      return EMPTY_STRING_STRING_ARRAY;
    }
    final List<Pair<File,String>> suggestedRoots = JavaUtil.suggestRoots(entryFile);
    final List<Pair<String,String>> paths = new ArrayList<Pair<String, String>>();
    for (final Pair<File, String> suggestedRoot : suggestedRoots) {
      try {
        if (FileUtil.isAncestor(entryFile, suggestedRoot.first, false)) {
          final String path = FileUtil.resolveShortWindowsName(suggestedRoot.first.getPath());
          paths.add(Pair.create(path, suggestedRoot.second));
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return paths;
  }

  protected void setSourceDirectoryName(String name) {
    name = name == null? "" : name.trim();
    myTfSourceDirectoryName.setText(name);
  }

  protected String getProgressText() {
    return IdeBundle.message("progress.searching.for.sources", myBuilder.getContentEntryPath().replace('/', File.separatorChar));
  }

  private class BrowsePathListener extends BrowseFilesListener {
    private final FileChooserDescriptor myChooserDescriptor;
    private final JTextField myField;

    public BrowsePathListener(JTextField textField, final FileChooserDescriptor chooserDescriptor) {
      super(textField, IdeBundle.message("prompt.select.source.directory"), "", chooserDescriptor);
      myChooserDescriptor = chooserDescriptor;
      myField = textField;
    }

    @Nullable
    private VirtualFile getContentEntryDir() {
      final String contentEntryPath = myBuilder.getContentEntryPath();
      if (contentEntryPath != null) {
        return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
          public VirtualFile compute() {
            return LocalFileSystem.getInstance().refreshAndFindFileByPath(contentEntryPath);
          }
        });
      }
      return null;
    }

    public void actionPerformed(ActionEvent e) {
      final VirtualFile contentEntryDir = getContentEntryDir();
      if (contentEntryDir != null) {
        myChooserDescriptor.setRoot(contentEntryDir);
        final String textBefore = myField.getText().trim();
        super.actionPerformed(e);
        if (!textBefore.equals(myField.getText().trim())) {
          final String fullPath = myField.getText().trim().replace(File.separatorChar, '/');
          final VirtualFile fileByPath = LocalFileSystem.getInstance().findFileByPath(fullPath);
          LOG.assertTrue(fileByPath != null);
          myField.setText(VfsUtil.getRelativePath(fileByPath, contentEntryDir, File.separatorChar));
        }
      }
    }
  }

  public boolean isStepVisible() {
    return myNameLocationStep.getContentEntryPath() != null;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public String getHelpId() {
    return myHelpId;
  }
}
