package com.redhat.devtools.lsp4ij.server.capabilities;

import com.google.gson.JsonObject;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.features.files.PathPatternMatcher;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentRegistrationOptions;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public abstract class TextDocumentServerCapabilityRegistry<T extends TextDocumentRegistrationOptions> {

    private final @NotNull LSPClientFeatures clientFeatures;
    private @Nullable ServerCapabilities serverCapabilities;

    private final List<T> dynamicCapabilities;

    public TextDocumentServerCapabilityRegistry(@NotNull LSPClientFeatures clientFeatures) {
        this.clientFeatures = clientFeatures;
        this.dynamicCapabilities = new ArrayList<>();
    }

    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        this.serverCapabilities = serverCapabilities;
        this.dynamicCapabilities.clear();
    }

    public @Nullable ServerCapabilities getServerCapabilities() {
        return serverCapabilities;
    }

    @Nullable
    public T registerCapability(@NotNull JsonObject registerOptions) {
        T t = create(registerOptions);
        if (t != null) {
            dynamicCapabilities.add(t);
        }
        return t;
    }

    @Nullable
    protected abstract T create(@NotNull JsonObject registerOptions);

    public void unregisterCapability(Object options) {
        dynamicCapabilities.remove(options);
    }

    protected boolean isSupported(@NotNull PsiFile file,
                                  @NotNull Predicate<@NotNull ServerCapabilities> matchServerCapabilities) {
        return isSupported(file, matchServerCapabilities, null);
    }

    protected boolean isSupported(@NotNull PsiFile file,
                                  @NotNull Predicate<@NotNull ServerCapabilities> matchServerCapabilities,
                                  @Nullable Predicate<@NotNull T> matchOption) {
        var serverCapabilities = getServerCapabilities();
        if (serverCapabilities != null && matchServerCapabilities.test(serverCapabilities)) {
            return true;
        }

        if (dynamicCapabilities.isEmpty()) {
            return false;
        }

        boolean languageIdGet = false;
        String languageId = null;
        URI fileUri = null;
        String scheme = null;
        for (var option : dynamicCapabilities) {
            // Match documentSelector?
            var filters = ((ExtendedDocumentSelector.DocumentFilersProvider) option).getFilters();
            if (filters.isEmpty()) {
                return matchOption != null ? matchOption.test(option) : true;
            }
            for (var filter : filters) {
                boolean hasLanguage = !StringUtils.isEmpty(filter.getLanguage());
                boolean hasScheme = !StringUtils.isEmpty(filter.getScheme());
                boolean hasPattern = !StringUtils.isEmpty(filter.getPattern());

                boolean matchDocumentSelector = false;
                // Matches language?
                if (hasLanguage) {
                    if (!languageIdGet) {
                        languageId = clientFeatures.getServerDefinition().getLanguageIdOrNull(file);
                        languageIdGet = true;
                    }
                    matchDocumentSelector = (languageId == null && !hasScheme && !hasPattern) // to be compatible with LSP4IJ < 0.7.0, when languageId is not defined in the mapping, we consider that it matches the documentSelector
                                            || filter.getLanguage().equals(languageId);
                }

                if (!matchDocumentSelector) {
                    // Matches scheme?
                    if (hasScheme) {
                        if (fileUri == null) {
                            fileUri = LSPIJUtils.toUri(file); // TODO: move this file Uri into LSP client features to customize the file Uri
                        }
                        if (scheme == null) {
                            scheme = fileUri.getScheme();
                        }
                        matchDocumentSelector = filter.getScheme().equals(scheme);
                    }

                    if (!matchDocumentSelector) {

                        // Matches pattern?
                        if (hasPattern) {
                            PathPatternMatcher patternMatcher = filter.getPathPattern();
                            if (fileUri == null) {
                                fileUri = LSPIJUtils.toUri(file); // TODO: move this file Uri into LSP client features to customize the file Uri
                            }
                            matchDocumentSelector = patternMatcher.matches(fileUri);
                        }
                    }
                }

                if (matchDocumentSelector) {
                    if (matchOption == null) {
                        return true;
                    }
                    if (matchOption.test(option)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean hasCapability(final Either<Boolean, ?> eitherCapability) {
        if (eitherCapability == null) {
            return false;
        }
        return eitherCapability.isRight() || hasCapability(eitherCapability.getLeft());
    }

    public static boolean hasCapability(Boolean capability) {
        return capability != null && capability;
    }

}