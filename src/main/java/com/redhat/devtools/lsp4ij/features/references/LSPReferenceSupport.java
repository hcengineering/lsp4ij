/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and reference
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.references;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import com.redhat.devtools.lsp4ij.usages.LocationData;
import org.eclipse.lsp4j.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LSP reference support which collect:
 *
 * <ul>
 *      <li>textDocument/reference</li>
 *  </ul>
 */
public class LSPReferenceSupport extends AbstractLSPDocumentFeatureSupport<LSPReferenceParams, List<LocationData>> {

    private Integer previousOffset;

    public LSPReferenceSupport(@NotNull PsiFile file) {
        super(file);
    }

    public CompletableFuture<List<LocationData>> getReferences(LSPReferenceParams params) {
        int offset = params.getOffset();
        if (previousOffset != null && !previousOffset.equals(offset)) {
            super.cancel();
        }
        previousOffset = offset;
        return super.getFeatureData(params);
    }

    @Override
    protected CompletableFuture<List<LocationData>> doLoad(LSPReferenceParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return collectReferences(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<LocationData>> collectReferences(@NotNull PsiFile file,
                                                                                @NotNull LSPReferenceParams params,
                                                                                @NotNull CancellationSupport cancellationSupport) {
        return getLanguageServers(file,
                f -> f.getReferencesFeature().isEnabled(file),
                f -> f.getReferencesFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have reference capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    // Collect list of textDocument/reference future for each language servers
                    List<CompletableFuture<List<LocationData>>> referencesPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getReferenceFor(params, file, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/reference future in one future which return the list of reference ranges
                    return CompletableFutures.mergeInOneFuture(referencesPerServerFutures, cancellationSupport);
                });
    }

    private static CompletableFuture<List<LocationData>> getReferenceFor(@NotNull LSPReferenceParams params,
                                                                     @NotNull PsiFile file,
                                                                     @NotNull LanguageServerItem languageServer,
                                                                     @NotNull CancellationSupport cancellationSupport) {
        // Update textDocument Uri with custom file Uri if needed
        updateTextDocumentUri(params.getTextDocument(), file, languageServer);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .references(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_REFERENCES)
                .thenApplyAsync(locations -> {
                    if (locations == null) {
                        // textDocument/reference may return null
                        return Collections.emptyList();
                    }
                    return locations
                            .stream()
                            .map(l -> new LocationData(new Location(l.getUri(), l.getRange()), languageServer))
                            .toList();
                });
    }

}
