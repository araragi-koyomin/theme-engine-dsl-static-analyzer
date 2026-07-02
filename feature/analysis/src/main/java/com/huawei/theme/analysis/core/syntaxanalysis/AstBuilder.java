package com.huawei.theme.analysis.core.syntaxanalysis;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.huawei.theme.analysis.core.expression.ExpressionParser;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;

public class AstBuilder implements DslAstProvider {

    private static final Pattern XML_DECLARATION =
            Pattern.compile("^\\s*(<\\?xml[^>]*\\?>)");

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
            XMLStreamReader reader = createSecureReader(content);
            SourcePositionMapper mapper = new SourcePositionMapper(content);
            DslElementNode root = buildTree(reader, content, mapper);
            reader.close();
            fileNode.setRootElement(root);
        } catch (XMLStreamException e) {
            Location loc = e.getLocation();
            int line = loc != null ? Math.max(loc.getLineNumber(), 0) : 0;
            int column = loc != null ? Math.max(loc.getColumnNumber() - 1, 0) : 0;
            fileNode.setRootElement(buildErrorNode(e.getMessage(), line, column));
        } catch (Exception e) {
            fileNode.setRootElement(buildErrorNode(e.getMessage(), 0, 0));
        }
        if (fileNode.getRootElement() != null) {
            fileNode.getRootElement().setParent(fileNode);
        }
        return fileNode;
    }

    private static XMLStreamReader createSecureReader(String content) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        return factory.createXMLStreamReader(new StringReader(content));
    }

    private DslElementNode buildTree(XMLStreamReader reader, String content, SourcePositionMapper mapper)
            throws XMLStreamException {
        Deque<DslElementNode> stack = new ArrayDeque<>();
        DslElementNode root = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamReader.START_ELEMENT) {
                DslElementNode node = buildElementNode(reader, content, mapper);
                stack.push(node);
            } else if (event == XMLStreamReader.END_ELEMENT) {
                DslElementNode node = stack.pop();
                node.setSelfClosing(node.getChildElements().isEmpty());
                if (stack.isEmpty()) {
                    root = node;
                } else {
                    DslElementNode parent = stack.peek();
                    node.setParent(parent);
                    List<DslElementNode> children = parent.getChildElements();
                    if (children != null) {
                        children.add(node);
                    }
                }
            }
        }
        return root;
    }

    private DslElementNode buildElementNode(XMLStreamReader reader, String content, SourcePositionMapper mapper) {
        String tagName = reader.getLocalName();
        int hint = reader.getLocation().getCharacterOffset();
        int ltOffset = findTagStart(content, hint, tagName);
        int[] lc = mapper.lineCol(ltOffset);

        DslElementNode node = new DslElementNode();
        node.setTagName(tagName);
        node.setText(tagName);
        node.setLine(lc[0]);
        node.setColumn(lc[1]);
        node.setAttributes(new ArrayList<>());
        node.setChildElements(new ArrayList<>());

        StartTagScanResult scan = scanStartTag(content, ltOffset);
        int[] endLc = (scan.tagEndOffset >= 0)
                ? mapper.lineCol(scan.tagEndOffset + 1)
                : lc;
        node.setEndLine(endLc[0]);
        node.setEndColumn(endLc[1]);

        int attrCount = reader.getAttributeCount();
        for (int i = 0; i < attrCount; i++) {
            String attrValue = reader.getAttributeValue(i);
            String attrName = (i < scan.attrs.size())
                    ? scan.attrs.get(i).name
                    : safeAttrName(reader, i);

            DslAttributeNode attr = new DslAttributeNode();
            attr.setName(attrName);
            attr.setText(attrValue);
            if (i < scan.attrs.size()) {
                AttrPos pos = scan.attrs.get(i);
                int[] nlc = mapper.lineCol(pos.nameOffset);
                int[] vlc = mapper.lineCol(pos.valueOffset);
                int[] nEnd = mapper.lineCol(pos.valueEndOffset + 1);
                int[] vEnd = mapper.lineCol(pos.valueEndOffset);
                attr.setLine(nlc[0]);
                attr.setColumn(nlc[1]);
                attr.setEndLine(nEnd[0]);
                attr.setEndColumn(nEnd[1]);

                DslAttributeValueNode value = buildValueNode(attrValue,
                        vlc[0], vlc[1], vEnd[0], vEnd[1]);
                attachExpression(value, attrValue, tagName, attrName);
                attr.setValue(value);
            } else {
                attr.setLine(lc[0]);
                attr.setColumn(lc[1]);
                attr.setEndLine(lc[0]);
                attr.setEndColumn(lc[1]);

                DslAttributeValueNode value = buildValueNode(attrValue, lc[0], lc[1], lc[0], lc[1]);
                attachExpression(value, attrValue, tagName, attrName);
                attr.setValue(value);
            }
            node.getAttributes().add(attr);
        }
        return node;
    }

    private DslAttributeValueNode buildValueNode(String attrValue, int line, int column, int endLine, int endColumn) {
        DslAttributeValueNode value = new DslAttributeValueNode();
        value.setRawValue(attrValue);
        value.setText(attrValue);
        value.setLine(line);
        value.setColumn(column);
        value.setEndLine(endLine);
        value.setEndColumn(endColumn);
        return value;
    }

    private void attachExpression(DslAttributeValueNode value, String attrValue, String tagName, String attrName) {
        ExpressionAstNode exprNode = null;
        boolean parseAttempted = false;
        if (ruleRepository != null) {
            Optional<AttrTypeSpec> specOpt = ruleRepository.getAttrTypeSpec(tagName, attrName);
            if (specOpt.isPresent() && specOpt.get().isSupportsExpression()) {
                String expressionKind = specOpt.get().getExpressionKind();
                if (ExpressionParser.hasExpressionSyntax(attrValue, attrName)) {
                    parseAttempted = true;
                    exprNode = ExpressionParser.parseExpression(attrValue, expressionKind);
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
    }

    private static String safeAttrName(XMLStreamReader reader, int i) {
        try {
            String n = reader.getAttributeLocalName(i);
            return n != null ? n : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 从StAX报告的偏移(可能off-by-one，如XML声明后首元素)校正到'<'的真实偏移。
     * 在hint附近(前向最多16字符)查找'<'紧跟tagName的位置；未命中则全局indexOf兜底。
     */
    private static int findTagStart(String source, int hint, String tagName) {
        if (hint < 0) {
            return -1;
        }
        for (int i = hint; i < source.length() && i < hint + 16; i++) {
            if (source.charAt(i) == '<' && source.startsWith(tagName, i + 1)) {
                return i;
            }
        }
        int idx = source.indexOf("<" + tagName, hint);
        return idx >= 0 ? idx : hint;
    }

    /**
     * 起始标签扫描器：从'<'偏移开始，解析标签名后各属性的name/value偏移及标签结尾'>'偏移。
     * 引号感知，故属性值内的'>'安全；仅在一个起始标签范围内扫描。
     * tagEndOffset为闭合'>'的偏移（元素区间末尾），扫描失败时为-1。
     * 每个AttrPos的valueEndOffset为属性值闭合引号的偏移。
     */
    private static StartTagScanResult scanStartTag(String source, int ltOffset) {
        if (ltOffset < 0 || ltOffset >= source.length()) {
            return StartTagScanResult.EMPTY;
        }
        List<AttrPos> attrs = new ArrayList<>();
        int tagEndOffset = -1;
        int i = ltOffset + 1;
        while (i < source.length() && isNameChar(source.charAt(i))) {
            i++;
        }
        while (i < source.length()) {
            while (i < source.length() && Character.isWhitespace(source.charAt(i))) {
                i++;
            }
            if (i >= source.length()) {
                break;
            }
            char c = source.charAt(i);
            if (c == '>' || c == '/') {
                if (c == '/') {
                    tagEndOffset = (i + 1 < source.length() && source.charAt(i + 1) == '>')
                            ? i + 1 : i;
                } else {
                    tagEndOffset = i;
                }
                break;
            }
            int nameStart = i;
            while (i < source.length() && isNameChar(source.charAt(i))) {
                i++;
            }
            String name = source.substring(nameStart, i);
            while (i < source.length() && Character.isWhitespace(source.charAt(i))) {
                i++;
            }
            if (i >= source.length() || source.charAt(i) != '=') {
                continue;
            }
            i++;
            while (i < source.length() && Character.isWhitespace(source.charAt(i))) {
                i++;
            }
            if (i >= source.length()) {
                break;
            }
            char quote = source.charAt(i);
            if (quote == '"' || quote == '\'') {
                i++;
                int valueStart = i;
                while (i < source.length() && source.charAt(i) != quote) {
                    i++;
                }
                int valueEndOffset = i;
                attrs.add(new AttrPos(name, nameStart, valueStart, valueEndOffset));
                if (i < source.length()) {
                    i++;
                }
            } else {
                while (i < source.length()
                        && !Character.isWhitespace(source.charAt(i))
                        && source.charAt(i) != '>' && source.charAt(i) != '/') {
                    i++;
                }
            }
        }
        return new StartTagScanResult(attrs, tagEndOffset);
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ':' || c == '.';
    }

    private static DslElementNode buildErrorNode(String message, int line, int column) {
        DslElementNode node = new DslElementNode();
        node.setHasError(true);
        node.setErrorMessage(message);
        node.setLine(line);
        node.setColumn(Math.max(column, 0));
        node.setEndLine(line);
        node.setEndColumn(Math.max(column, 0));
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

    private static final class StartTagScanResult {
        static final StartTagScanResult EMPTY = new StartTagScanResult(Collections.emptyList(), -1);
        final List<AttrPos> attrs;
        final int tagEndOffset;

        StartTagScanResult(List<AttrPos> attrs, int tagEndOffset) {
            this.attrs = attrs;
            this.tagEndOffset = tagEndOffset;
        }
    }

    private static final class AttrPos {
        final String name;
        final int nameOffset;
        final int valueOffset;
        final int valueEndOffset;

        AttrPos(String name, int nameOffset, int valueOffset, int valueEndOffset) {
            this.name = name;
            this.nameOffset = nameOffset;
            this.valueOffset = valueOffset;
            this.valueEndOffset = valueEndOffset;
        }
    }
}
