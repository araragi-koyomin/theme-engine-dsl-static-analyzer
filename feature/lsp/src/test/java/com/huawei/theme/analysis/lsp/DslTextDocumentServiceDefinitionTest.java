package com.huawei.theme.analysis.lsp;

import java.lang.reflect.Proxy;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.services.LanguageClient;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslTextDocumentServiceDefinitionTest {

    private static final String URI = "file:///test.xml";

    private DslTextDocumentService service;

    @BeforeEach
    void setUp() {
        RuleRepository repo = new RuleRepositoryFactory(null).create();
        service = new DslTextDocumentService(repo);
        LanguageClient stub = (LanguageClient) Proxy.newProxyInstance(
                LanguageClient.class.getClassLoader(),
                new Class<?>[]{LanguageClient.class},
                (p, m, a) -> null);
        service.setClient(stub);
    }

    private void open(String text) {
        DidOpenTextDocumentParams open = new DidOpenTextDocumentParams();
        open.setTextDocument(new TextDocumentItem(URI, "xml", 1, text));
        service.didOpen(open);
    }

    private List<? extends Location> definitionAt(String text, int cursorOffset) throws Exception {
        open(text);
        DefinitionParams params = new DefinitionParams();
        params.setTextDocument(new TextDocumentIdentifier(URI));
        params.setPosition(pos(text, cursorOffset));
        return service.definition(params).get().getLeft();
    }

    private static Position pos(String text, int offset) {
        int line = 0;
        int start = 0;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                start = i + 1;
            }
        }
        return new Position(line, offset - start);
    }

    @Test
    void openDocVariableRefReturnsLocation() throws Exception {
        String text = "<Lockscreen>\n"
                + "  <Var name=\"foo\" expression=\"1\"/>\n"
                + "  <Text name=\"t\" x=\"#foo\"/>\n"
                + "</Lockscreen>";
        int cursor = text.indexOf("#foo") + 2;
        List<? extends Location> locations = definitionAt(text, cursor);
        assertEquals(1, locations.size());
        assertEquals(URI, locations.get(0).getUri());
    }

    @Test
    void unopenedDocReturnsEmpty() throws Exception {
        DefinitionParams params = new DefinitionParams();
        params.setTextDocument(new TextDocumentIdentifier(URI));
        params.setPosition(new Position(2, 12));
        List<? extends Location> locations = service.definition(params).get().getLeft();
        assertTrue(locations.isEmpty());
    }

    @Test
    void cursorOnTagNameReturnsEmpty() throws Exception {
        String text = "<Lockscreen>\n"
                + "  <Var name=\"foo\" expression=\"1\"/>\n"
                + "  <Text name=\"t\" x=\"#foo\"/>\n"
                + "</Lockscreen>";
        int cursor = text.indexOf("<Text") + 2;
        List<? extends Location> locations = definitionAt(text, cursor);
        assertTrue(locations.isEmpty());
    }

    @Test
    void undefinedVarRefReturnsEmpty() throws Exception {
        String text = "<Lockscreen>\n"
                + "  <Var name=\"foo\" expression=\"1\"/>\n"
                + "  <Text name=\"t\" x=\"#bar\"/>\n"
                + "</Lockscreen>";
        int cursor = text.indexOf("#bar") + 2;
        List<? extends Location> locations = definitionAt(text, cursor);
        assertTrue(locations.isEmpty());
    }
}
