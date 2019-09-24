// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.remote.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SeparatorWithText;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RunOnTargetComboBox extends ComboBox<RunOnTargetComboBox.Item> {
  public static final Logger LOGGER = Logger.getInstance(RunOnTargetComboBox.class);
  @NotNull private final Project myProject;
  @Nullable private LanguageRuntimeType<?> myDefaultRuntimeType;

  public RunOnTargetComboBox(@NotNull Project project) {
    super();
    setModel(new MyModel());
    myProject = project;
    setRenderer(new MyRenderer());
  }

  public void initModel() {
    MyModel model = (MyModel)getModel();
    model.removeAllElements();
    model.addElement(null);

    Collection<Type<?>> types = new ArrayList<>();
    for (RemoteTargetType<?> type : RemoteTargetType.Companion.getEXTENSION_NAME().getExtensionList()) {
      if (type.providesNewWizard(myProject, myDefaultRuntimeType)) {
        types.add(new Type<>(type));
      }
    }
    if (!types.isEmpty()) {
      model.addElement(new Separator("New Targets"));
      for (Type<?> type : types) {
        model.addElement(type);
      }
    }
  }

  public void setDefaultLanguageRuntimeTime(@Nullable LanguageRuntimeType<?> defaultLanguageRuntimeType) {
    myDefaultRuntimeType = defaultLanguageRuntimeType;
  }

  public void addTarget(@NotNull RemoteTargetConfiguration config, int index) {
    Icon icon = RemoteTargetConfigurationKt.getTargetType(config).getIcon();
    ((MyModel)getModel()).insertElementAt(new Target(config.getDisplayName(), icon), index);
  }

  @Nullable
  public String getSelectedTargetName() {
    return ObjectUtils.doIfCast(getSelectedItem(), Item.class, i -> i.getDisplayName());
  }

  public void addTargets(List<RemoteTargetConfiguration> configs) {
    int index = 1;
    for (RemoteTargetConfiguration config : configs) {
      addTarget(config, index);
      index++;
    }
  }

  public void selectTarget(String configName) {
    if (configName == null) {
      setSelectedItem(null);
      return;
    }
    for (int i = 0; i < getModel().getSize(); i++) {
      Item at = getModel().getElementAt(i);
      if (at instanceof Target && configName.equals(at.getDisplayName())) {
        setSelectedItem(at);
      }
    }
    //todo[remoteServers]: add invalid value
  }

  public static abstract class Item {
    private final String displayName;
    private final Icon icon;


    public Item(String displayName, Icon icon) {
      this.displayName = displayName;
      this.icon = icon;
    }

    public String getDisplayName() {
      return displayName;
    }

    public Icon getIcon() {
      return icon;
    }
  }

  private static class Separator extends Item {
    private Separator(String displayName) {
      super(displayName, null);
    }
  }

  private static class Target extends Item {
    private Target(String name, Icon icon) {
      super(name, icon);
    }
  }

  private static class Type<T extends RemoteTargetConfiguration> extends Item {
    @NotNull
    private final RemoteTargetType<T> type;

    private Type(@NotNull RemoteTargetType<T> type) {
      super(type.getDisplayName(), type.getIcon());
      this.type = type;
    }

    @Nullable
    private Pair<T, List<AbstractWizardStepEx>> createStepsForNewWizard(Project project, LanguageRuntimeType<?> defaultRuntimeType) {
      T config = type.createDefaultConfig();
      List<AbstractWizardStepEx> steps = type.createStepsForNewWizard(project, config, defaultRuntimeType);
      if (steps == null) {
        LOGGER.error("Cannot instantiate remote target wizard");
        return null;
      }
      return Pair.create(config, steps);
    }
  }

  private class MyModel extends DefaultComboBoxModel<RunOnTargetComboBox.Item> {
    @Override
    public void setSelectedItem(Object anObject) {
      if (anObject instanceof Separator) {
        return;
      }
      if (anObject instanceof Type) {
        //noinspection unchecked,rawtypes
        Pair<RemoteTargetConfiguration, List<AbstractWizardStepEx>> wizardData =
          ((Type)anObject).createStepsForNewWizard(myProject, myDefaultRuntimeType);
        if (wizardData != null) {
          RemoteTargetConfiguration newTarget = wizardData.first;
          RemoteTargetWizard wizard = new RemoteTargetWizard(myProject, "New Target", newTarget, wizardData.second);
          if (wizard.showAndGet()) {
            RemoteTargetsManager.getInstance().addTarget(newTarget);
            addTarget(newTarget, 1);
            setSelectedIndex(1);
          }
        }
        return;
      }
      super.setSelectedItem(anObject);
    }
  }

  private static class MyRenderer extends ColoredListCellRenderer<RunOnTargetComboBox.Item> {
    @Override
    public Component getListCellRendererComponent(JList<? extends Item> list, Item value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof Separator) {
        SeparatorWithText separator = new SeparatorWithText();
        separator.setCaption(value.getDisplayName());
        separator.setCaptionCentered(false);
        setFont(getFont().deriveFont(Font.PLAIN));
        return separator;
      }
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends Item> list, Item value, int index, boolean selected, boolean hasFocus) {
      if (value == null) {
        append("Local machine");
        setIcon(AllIcons.Nodes.HomeFolder);
      }
      else {
        append(value.getDisplayName());
        setIcon(value.getIcon());
      }
    }
  }
}
