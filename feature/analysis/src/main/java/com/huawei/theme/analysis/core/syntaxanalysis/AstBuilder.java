package com.huawei.theme.analysis.core.syntaxanalysis;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

public class AstBuilder implements DslAstProvider {

    private static final Pattern XML_DECLARATION =
            Pattern.compile("^\\s*(<\\?xml[^>]*\\?>)");

    @Override
    public DslFileNode getDslAst(String filePath, String content) {
        DslFileNode fileNode = new DslFileNode();
        fileNode.setText(content);
        fileNode.setLine(1);
        fileNode.setColumn(0);
        fileNode.setXmlDeclaration(extractXmlDeclaration(content));

        try {
            SAXParser parser = createSecureParser();
            AstContentHandler handler = new AstContentHandler();
            parser.parse(new InputSource(new StringReader(content)), handler);
            fileNode.setRootElement(handler.getRoot());
        } catch (SAXParseException e) {
            fileNode.setRootElement(buildErrorNode(e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
        } catch (Exception e) {
            fileNode.setRootElement(buildErrorNode(e.getMessage(), 0, 0));
        }
        return fileNode;
    }

    private static SAXParser createSecureParser() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureIfSupported(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newSAXParser();
    }

    private static void setFeatureIfSupported(SAXParserFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception ignored) {
        }
    }

    private static DslElementNode buildErrorNode(String message, int line, int column) {
        DslElementNode node = new DslElementNode();
        node.setHasError(true);
        node.setErrorMessage(message);
        node.setLine(line);
        node.setColumn(Math.max(column, 0));
        node.setAttributes(new ArrayList<>());
        node.setChildElements(new ArrayList<>());
        return node;
    }

    private static String extractXmlDeclaration(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = XML_DECLARATION.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static final class AstContentHandler extends DefaultHandler {
        private Locator locator;
        private final Deque<DslElementNode> stack = new ArrayDeque<>();
        private DslElementNode root;

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            DslElementNode node = new DslElementNode();
            node.setTagName(qName);
            node.setText(qName);
            int line = currentLine();
            int column = currentColumn();
            node.setLine(line);
            node.setColumn(column);
            node.setAttributes(new ArrayList<>());
            node.setChildElements(new ArrayList<>());

            for (int i = 0; i < attributes.getLength(); i++) {
                DslAttributeNode attr = new DslAttributeNode();
                attr.setName(attributes.getQName(i));
                attr.setText(attributes.getValue(i));
                attr.setLine(line);
                attr.setColumn(column);

                DslAttributeValueNode value = new DslAttributeValueNode();
                value.setRawValue(attributes.getValue(i));
                value.setText(attributes.getValue(i));
                value.setLiteral(true);
                value.setExpression(Optional.empty());
                value.setLine(line);
                value.setColumn(column);
                attr.setValue(value);

                node.getAttributes().add(attr);
            }

            stack.push(node);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            DslElementNode node = stack.pop();
            node.setSelfClosing(node.getChildElements().isEmpty());
            if (stack.isEmpty()) {
                root = node;
            } else {
                stack.peek().getChildElements().add(node);
            }
        }

        DslElementNode getRoot() {
            return root;
        }

        private int currentLine() {
            return locator != null ? Math.max(locator.getLineNumber(), 0) : 0;
        }

        private int currentColumn() {
            if (locator == null) {
                return 0;
            }
            int col = locator.getColumnNumber();
            return col > 0 ? col - 1 : 0;
        }
    }
}
