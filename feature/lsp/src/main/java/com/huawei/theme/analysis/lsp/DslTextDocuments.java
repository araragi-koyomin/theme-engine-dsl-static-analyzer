package com.huawei.theme.analysis.lsp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of open document texts, keyed by document URI.
 * Thread-safe; supports full-sync document updates.
 */
final class DslTextDocuments {

    private final Map<String, String> docs = new ConcurrentHashMap<>();

    void open(String uri, String text) {
        docs.put(uri, text);
    }

    void update(String uri, String text) {
        docs.put(uri, text);
    }

    void close(String uri) {
        docs.remove(uri);
    }

    String get(String uri) {
        return docs.get(uri);
    }
}
