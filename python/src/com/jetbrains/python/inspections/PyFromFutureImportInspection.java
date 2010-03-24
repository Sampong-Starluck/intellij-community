package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.MoveFromFutureImportQuickFix;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   24.03.2010
 * Time:   19:04:41
 */
public class PyFromFutureImportInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.from.future.import");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "PyFromFutureImportInspection";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyFromImportStatement(PyFromImportStatement node) {
      PyReferenceExpression importSource = node.getImportSource();
      if (importSource != null && "__future__".equals(importSource.getName())) {
        PsiFile file = importSource.getContainingFile();
        if (file instanceof PyFile) {
          final List<PyStatement> statementList = ((PyFile)file).getStatements();
          for (PyStatement statement : statementList) {
            if (statement instanceof PyFromImportStatement) {
              if (statement == node) {
                return;
              }
              PyReferenceExpression source = ((PyFromImportStatement)statement).getImportSource();
              if (source != null && "__future__".equals(source.getName())) {
                continue;
              }
            }
            registerProblem(node, "from __future__ imports must occur at the beginning of the file",
                            new MoveFromFutureImportQuickFix());
            return;
          }
        }
      }
    }
  }
}
