// Copyright © 2025 Huly Labs. Use of this source code is governed by the Apache 2.0 license.
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.util.messages.Topic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;

public interface LSPDiagnosticListener {
  @Topic.ProjectLevel
  Topic<LSPDiagnosticListener> TOPIC = new Topic<>(LSPDiagnosticListener.class, Topic.BroadcastDirection.TO_PARENT, true);

  void publishDiagnostics(PublishDiagnosticsParams params);
}
