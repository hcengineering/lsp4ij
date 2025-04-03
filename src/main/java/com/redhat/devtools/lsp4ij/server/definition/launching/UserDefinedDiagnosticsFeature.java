package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.redhat.devtools.lsp4ij.client.features.LSPDiagnosticFeature;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;

import java.util.List;

public class UserDefinedDiagnosticsFeature extends LSPDiagnosticFeature {
    @Override
    public List<String> getDiskSources() {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getClientFeatures().getServerDefinition();
        ClientConfigurationSettings clientConfiguration = serverDefinition.getLanguageServerClientConfiguration();
        return clientConfiguration != null ? clientConfiguration.diagnostics.diskSources : super.getDiskSources();
    }
}
