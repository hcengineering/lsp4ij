// Copyright © 2025 Huly Labs. Use of this source code is governed by the Apache 2.0 license.
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.codeInsight.daemon.impl.WolfTheProblemSolverImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;

import java.util.HashSet;

final public class DiagnosticUtils {
  public static void clearDiagnostics(LanguageServerWrapper server) {
    Project project = server.getProject();
    HashSet<VirtualFile> allFiles = new HashSet<>();
    WolfTheProblemSolverImpl wolfTheProblemSolver = (WolfTheProblemSolverImpl)WolfTheProblemSolver.getInstance(project);
    wolfTheProblemSolver.consumeProblemFilesFromExternalSources(allFiles::add);
    ReadAction.nonBlocking(() -> {
      for (VirtualFile file : allFiles) {
        wolfTheProblemSolver.clearProblemsFromExternalSource(file, server);
      }
      return true;
    }).submit(AppExecutorUtil.getAppExecutorService());
  }
}
