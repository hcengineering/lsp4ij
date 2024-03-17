/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.operations.signatureHelp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.operations.AbstractLSPFeatureSupport;
import com.redhat.devtools.lsp4ij.operations.LSPRequestConstants;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * LSP signature help support which loads and caches signature help by consuming:
 * <ul>
 *     <li>LSP 'textDocument/signatureHelp' requests</li>
 * </ul>
 */
public class LSPSignatureHelpSupport extends AbstractLSPFeatureSupport<SignatureHelpParams, SignatureHelp> {

    public LSPSignatureHelpSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<SignatureHelp> getSignatureHelp(SignatureHelpParams params) {
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<SignatureHelp> doLoad(SignatureHelpParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getSignatureHelp(file.getVirtualFile(), file.getProject(), params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<SignatureHelp> getSignatureHelp(@NotNull VirtualFile file,
                                                                              @NotNull Project project,
                                                                              @NotNull SignatureHelpParams params,
                                                                              @NotNull CancellationSupport cancellationSupport) {

        return LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(file, LanguageServerItem::isSignatureHelpSupported)
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have signature help capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    // Get signature help for the first language server
                    LanguageServerItem languageServer = languageServers.get(0);
                    return getSignatureHelpFor(params, languageServer, cancellationSupport);
                });
    }

    private static CompletableFuture<SignatureHelp> getSignatureHelpFor(@NotNull SignatureHelpParams params,
                                                                        @NotNull LanguageServerItem languageServer,
                                                                        @NotNull CancellationSupport cancellationSupport) {
        return cancellationSupport.execute(languageServer
                .getTextDocumentService()
                .signatureHelp(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_SIGNATURE_HELP);
    }


}
