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

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.huawei.theme.analysis.core.expression.DslExpressionVisitorAdapter;
import com.huawei.theme.analysis.core.expression.ExpressionNode;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionLexer;
import com.huawei.theme.analysis.core.expression.generated.DslExpressionParser;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;

public class AstBuilder implements DslAstProvider {

    private static final Pattern XML_DECLARATION =
            Pattern.compile("^\\s*(<\\?xml[^>]*\\?>)");

    private static final Pattern HEX_COLOR =
            Pattern.compile("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$");

    private final RuleRepository ruleRepository;

    public AstBuilder() {
        this(null);
    }

    public AstBuilder(RuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    @Override
    public DslFileNode getDslAst(String filePath, String content) {
        DslFileNode fileNode = new DslFileNode();
        fileNode.setFilePath(filePath);
        fileNode.setText(content);
        fileNode.setLine(1);
        fileNode.setColumn(0);
        fileNode.setXmlDeclaration(extractXmlDeclaration(content));

        try {
            SAXParser parser = createSecureParser();
            AstContentHandler handler = new AstContentHandler(ruleRepository);
            parser.parse(new InputSource(new StringReader(content)), handler);
            fileNode.setRootElement(handler.getRoot());
        } catch (SAXParseException e) {
            fileNode.setRootElement(buildErrorNode(e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
        } catch (Exception e) {
            fileNode.setRootElement(buildErrorNode(e.getMessage(), 0, 0));
        }
        if (fileNode.getRootElement() != null) {
            fileNode.getRootElement().setParent(fileNode);
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

    static boolean hasExpressionSyntax(String value, String attrName) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (value.indexOf('@') >= 0
                || value.indexOf('\'') >= 0
                || value.indexOf('(') >= 0
                || value.indexOf('{') >= 0
                || value.indexOf('+') >= 0
                || value.indexOf('-') >= 0
                || value.indexOf('*') >= 0
                || value.indexOf('/') >= 0
                || value.indexOf('%') >= 0) {
            return true;
        }
        if (value.indexOf('#') >= 0) {
            return !(isColorAttribute(attrName) && isHexColor(value));
        }
        return false;
    }

    private static boolean isColorAttribute(String attrName) {
        return "color".equals(attrName) || "shadowColor".equals(attrName);
    }

    private static boolean isHexColor(String value) {
        return HEX_COLOR.matcher(value).matches();
    }

    static ExpressionNode parseExpression(String value, String expressionKind) {
        try {
            DslExpressionLexer lexer = new DslExpressionLexer(CharStreams.fromString(value));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            DslExpressionParser parser = new DslExpressionParser(tokens);
            parser.removeErrorListeners();
            parser.setErrorHandler(new org.antlr.v4.runtime.BailErrorStrategy());
            DslExpressionVisitorAdapter adapter = new DslExpressionVisitorAdapter();
            ExpressionNode node;
            if ("string".equals(expressionKind)) {
                node = adapter.visit(parser.stringExpression());
            } else if ("number".equals(expressionKind)) {
                node = adapter.visit(parser.numericExpression());
                tokens.fill();
                if (tokens.index() < tokens.size() - 1) {
                    return null;
                }
            } else {
                node = adapter.visit(parser.expression());
                tokens.fill();
                if (tokens.index() < tokens.size() - 1) {
                    return null;
                }
            }
            if (node == null || containsInvalidUnaryMinusVar(node)) {
                return null;
            }
            return node;
        } catch (Exception e) {
            return null;
        }
    }

    static boolean containsInvalidUnaryMinusVar(ExpressionNode node) {
        if (node == null) {
            return false;
        }
        if (node.getKind() == ExpressionKind.UNARY_EXPR && "-".equals(node.getOperator())
                && node.getChildren() != null && !node.getChildren().isEmpty()) {
            ExpressionNode child = node.getChildren().get(0);
            if ((child.getKind() == ExpressionKind.VARIABLE_REF
                    || child.getKind() == ExpressionKind.ARRAY_ACCESS)
                    && "#".equals(child.getPrefix())) {
                return true;
            }
        }
        if (node.getChildren() != null) {
            for (ExpressionNode c : node.getChildren()) {
                if (containsInvalidUnaryMinusVar(c)) {
                    return true;
                }
            }
        }
        if (node.getIndexExpression() != null && containsInvalidUnaryMinusVar(node.getIndexExpression())) {
            return true;
        }
        return false;
    }

    private static final class AstContentHandler extends DefaultHandler {
        private final RuleRepository ruleRepository;
        private Locator locator;
        private final Deque<DslElementNode> stack = new ArrayDeque<>();
        private DslElementNode root;

        private AstContentHandler(RuleRepository ruleRepository) {
            this.ruleRepository = ruleRepository;
        }

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
                String attrName = attributes.getQName(i);
                String attrValue = attributes.getValue(i);

                DslAttributeNode attr = new DslAttributeNode();
                attr.setName(attrName);
                attr.setText(attrValue);
                attr.setLine(line);
                attr.setColumn(column);

                DslAttributeValueNode value = new DslAttributeValueNode();
                value.setRawValue(attrValue);
                value.setText(attrValue);
                value.setLine(line);
                value.setColumn(column);

                ExpressionAstNode exprNode = null;
                boolean parseAttempted = false;
                if (ruleRepository != null) {
                    Optional<AttrTypeSpec> specOpt = ruleRepository.getAttrTypeSpec(qName, attrName);
                    if (specOpt.isPresent() && specOpt.get().isSupportsExpression()) {
                        String expressionKind = specOpt.get().getExpressionKind();
                        if (hasExpressionSyntax(attrValue, attrName)) {
                            parseAttempted = true;
                            exprNode = parseExpression(attrValue, expressionKind);
                        }
                    }
                }

                if (exprNode != null) {
                    value.setExpression(Optional.of(exprNode));
                    value.setLiteral(false);
                } else if (parseAttempted) {
                    value.setExpression(Optional.empty());
                    value.setLiteral(false);
                } else {
                    value.setExpression(Optional.empty());
                    value.setLiteral(true);
                }
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
                DslElementNode parent = stack.peek();
                node.setParent(parent);
                var children = parent.getChildElements();
                if (children != null) {
                    children.add(node);
                }
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
