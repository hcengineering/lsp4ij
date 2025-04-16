package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.features.LSPDiagnosticFeature;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class UserDefinedDiagnosticsFeature extends LSPDiagnosticFeature {
    @Override
    public List<String> getDiskSources() {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getClientFeatures().getServerDefinition();
        ClientConfigurationSettings clientConfiguration = serverDefinition.getLanguageServerClientConfiguration();
        return clientConfiguration != null ? clientConfiguration.diagnostics.diskSources : super.getDiskSources();
    }

    @Override
    public boolean isDiagnosticEnabled() {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getClientFeatures().getServerDefinition();
        ClientConfigurationSettings clientConfiguration = serverDefinition.getLanguageServerClientConfiguration();
        return clientConfiguration == null || !clientConfiguration.diagnostics.disablePullDiagnostics;
    }

    @Override
    public boolean isDiagnosticSupported(@NotNull PsiFile file) {
        return isDiagnosticEnabled() && super.isDiagnosticSupported(file);
    }

    @Override
    public boolean isDiagnosticSupported(@NotNull VirtualFile file) {
        return isDiagnosticEnabled() && super.isDiagnosticSupported(file);
    }
}
