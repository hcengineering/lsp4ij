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
package com.redhat.devtools.lsp4ij.features.formatting;

import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import static com.redhat.devtools.lsp4ij.LSPIJUtils.applyEdits;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * Abstract class for LSP formatting and range formatting.
 */
public class LSPFormattingSupport extends AbstractLSPDocumentFeatureSupport<LSPFormattingParams, List<? extends TextEdit>> {

    public LSPFormattingSupport(@NotNull PsiFile file) {
        super(file);
    }

    public void format(@NotNull Document document,
                       @Nullable Editor editor,
                       @Nullable TextRange textRange,
                       @NotNull AsyncFormattingRequest formattingRequest) {
        Integer tabSize = editor != null ? LSPIJUtils.getTabSize(editor) : null;
        Boolean insertSpaces = editor != null ? LSPIJUtils.isInsertSpaces(editor) : null;
        LSPFormattingParams params = new LSPFormattingParams(tabSize, insertSpaces, textRange, document);
        CompletableFuture<List<? extends TextEdit>> formatFuture = this.getFeatureData(params);
        try {
            waitUntilDone(formatFuture, getFile());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            handleError(formattingRequest, cause);
            return;
        }
        try {
            List<? extends TextEdit> edits = formatFuture.getNow(null);
            String formatted = edits != null ? applyEdits(editor.getDocument(), edits) : formattingRequest.getDocumentText();
            formattingRequest.onTextReady(formatted);
        } catch (Exception e) {
            handleError(formattingRequest, e);
        }
    }

    private static void handleError(@NotNull AsyncFormattingRequest formattingRequest, Throwable error) {
        if (error instanceof ProcessCanceledException || error instanceof CancellationException) {
            // Ignore the error
            formattingRequest.onTextReady(formattingRequest.getDocumentText());
        } else {
            formattingRequest.onError("LSP formatting error", error.getMessage());
        }
    }

    @Override
    protected CompletableFuture<List<? extends TextEdit>> doLoad(LSPFormattingParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return getFormatting(file.getVirtualFile(), file.getProject(), params, cancellationSupport);
    }

    protected @NotNull CompletableFuture<List<? extends TextEdit>> getFormatting(@NotNull VirtualFile file,
                                                                                 @NotNull Project project,
                                                                                 @NotNull LSPFormattingParams params,
                                                                                 @NotNull CancellationSupport cancellationSupport) {
        boolean isRangeFormatting = params.textRange() != null;
        Predicate<ServerCapabilities> filter = !isRangeFormatting ?
                LanguageServerItem::isDocumentFormattingSupported :
                capability -> (LanguageServerItem.isDocumentRangeFormattingSupported(capability) ||
                        LanguageServerItem.isDocumentFormattingSupported(capability));
        return LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(file, filter)
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have formatting capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }

                    // Get the first language server which supports range formatting (if it requires) or formatting
                    LanguageServerItem languageServer = getFormattingLanguageServer(languageServers, isRangeFormatting);

                    cancellationSupport.checkCanceled();

                    if (isRangeFormatting && languageServer.isDocumentRangeFormattingSupported()) {
                        // Range formatting
                        DocumentRangeFormattingParams lspParams = createDocumentRangeFormattingParams(params.tabSize(), params.insertSpaces(), params.textRange(), params.document());
                        return cancellationSupport.execute(languageServer
                                .getTextDocumentService()
                                .rangeFormatting(lspParams), languageServer, LSPRequestConstants.TEXT_DOCUMENT_RANGE_FORMATTING);
                    }

                    // Full document formatting
                    DocumentFormattingParams lspParams = createDocumentFormattingParams(params.tabSize(), params.insertSpaces());
                    return cancellationSupport.execute(languageServer
                            .getTextDocumentService()
                            .formatting(lspParams), languageServer, LSPRequestConstants.TEXT_DOCUMENT_FORMATTING);
                });
    }

    private static LanguageServerItem getFormattingLanguageServer(List<LanguageServerItem> languageServers, boolean isRangeFormatting) {
        if (isRangeFormatting) {
            // Range formatting, try to get the first language server which have the range formatting capability
            Optional<LanguageServerItem> result = languageServers
                    .stream()
                    .filter(LanguageServerItem::isDocumentRangeFormattingSupported)
                    .findFirst();
            if (result.isPresent()) {
                return result.get();
            }
        }
        // Get the first language server
        return languageServers.get(0);
    }

    private @NotNull DocumentFormattingParams createDocumentFormattingParams(Integer tabSize, Boolean insertSpaces) {
        DocumentFormattingParams params = new DocumentFormattingParams();
        params.setTextDocument(LSPIJUtils.toTextDocumentIdentifier(getFile().getVirtualFile()));
        FormattingOptions options = new FormattingOptions();
        if (tabSize != null) {
            options.setTabSize(tabSize);
        }
        if (insertSpaces != null) {
            options.setInsertSpaces(insertSpaces);
        }
        params.setOptions(options);
        return params;
    }

    private @NotNull DocumentRangeFormattingParams createDocumentRangeFormattingParams(Integer tabSize, Boolean insertSpaces, @NotNull TextRange textRange, Document document) {
        DocumentRangeFormattingParams params = new DocumentRangeFormattingParams();
        params.setTextDocument(LSPIJUtils.toTextDocumentIdentifier(getFile().getVirtualFile()));
        FormattingOptions options = new FormattingOptions();
        if (tabSize != null) {
            options.setTabSize(tabSize);
        }
        if (insertSpaces != null) {
            options.setInsertSpaces(insertSpaces);
        }
        params.setOptions(options);
        if (document != null) {
            Range range = LSPIJUtils.toRange(textRange, document);
            params.setRange(range);
        }
        return params;
    }
}
