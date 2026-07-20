package com.huawei.theme.analysis.plugin.lsp;

/**
 * Marker object carried as a completion {@code LookupElement}'s object so the
 * {@link ThemeDslLspHoverProvider} can recognize lookup items whose
 * documentation was supplied by the LSP server (in
 * {@code CompletionItem.documentation}) and surface it in IntelliJ's
 * completion documentation panel.
 */
final class DslLookupDoc {

    final String label;
    final String markup;
    final int sortPriority; // 0=highest (required/default), 4=lowest (function)

    DslLookupDoc(String label, String markup) {
        this(label, markup, 5);
    }

    DslLookupDoc(String label, String markup, int sortPriority) {
        this.label = label;
        this.markup = markup;
        this.sortPriority = sortPriority;
    }
}
