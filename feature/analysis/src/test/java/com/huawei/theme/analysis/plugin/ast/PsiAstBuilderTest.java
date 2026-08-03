package com.huawei.theme.analysis.plugin.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightPlatformTestCase;

import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeValueNode;
import com.huawei.theme.analysis.core.shared.ast.DslAttributeNode;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionAstNode;
import com.huawei.theme.analysis.core.shared.ast.ExpressionKind;
import com.huawei.theme.analysis.core.expression.ExpressionNode;

public class PsiAstBuilderTest extends LightPlatformTestCase {

    private RuleRepository repo;
    private final PsiAstBuilder builder = new PsiAstBuilder();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        repo = new JsonRuleLoader().loadFromDirectory(
                System.getProperty("user.dir") + "/src/main/resources/rules");
    }

    private XmlFile xmlFile(String content) {
        Project project = getProject();
        return (XmlFile) PsiFileFactory.getInstance(project)
                .createFileFromText("test.xml", XmlFileType.INSTANCE, content);
    }

    public void testBuildsStructureAndBidirectionalMap() {
        String xml = "<Lockscreen frameRate=\"60\">\n"
                + "  <Var name=\"v\" expression=\"#battery_level\" type=\"number\"/>\n"
                + "  <Group w=\"1080\"><Text color=\"#FFFFFF\"/></Group>\n"
                + "</Lockscreen>";
        XmlFile xmlFile = xmlFile(xml);

        DslAstTree tree = builder.build(xmlFile, repo);
        DslFileNode ast = tree.getAst();
        DslElementNode root = ast.getRootElement();
        assertNotNull(root);
        assertEquals("Lockscreen", root.getTagName());
        assertEquals(2, root.getChildElements().size());
        assertEquals("Var", root.getChildElements().get(0).getTagName());
        assertEquals("Group", root.getChildElements().get(1).getTagName());
        DslElementNode text = root.getChildElements().get(1).getChildElements().get(0);
        assertEquals("Text", text.getTagName());

        assertEquals(4, tree.size());

        Optional<XmlTag> rootTagOpt = tree.getTag(root);
        assertTrue(rootTagOpt.isPresent());
        assertEquals("Lockscreen", rootTagOpt.get().getName());

        Optional<XmlTag> varTagOpt = tree.getTag(root.getChildElements().get(0));
        assertTrue(varTagOpt.isPresent());
        assertEquals("Var", varTagOpt.get().getName());

        XmlTag psiRoot = xmlFile.getRootTag();
        assertNotNull(psiRoot);
        XmlTag psiVar = firstSubTag(psiRoot, "Var");
        assertNotNull(psiVar);
        Optional<DslElementNode> astVarOpt = tree.getNode(psiVar);
        assertTrue(astVarOpt.isPresent());
        assertEquals("Var", astVarOpt.get().getTagName());
        assertSame(root, astVarOpt.get().getParent());
    }

    public void testLineNumbersFromDocument() {
        String xml = "<?xml version=\"1.0\"?>\n"
                + "<Lockscreen>\n"
                + "  <Var name=\"v\"/>\n"
                + "</Lockscreen>";
        XmlFile xmlFile = xmlFile(xml);
        DslAstTree tree = builder.build(xmlFile, repo);

        DslElementNode root = tree.getAst().getRootElement();
        assertEquals(2, root.getLine());
        DslElementNode var = root.getChildElements().get(0);
        assertEquals(3, var.getLine());
    }

    public void testExpressionEmbeddingViaPsi() {
        String xml = "<Lockscreen>\n"
                + "  <Var name=\"v\" expression=\"#battery_level\" type=\"number\"/>\n"
                + "</Lockscreen>";
        XmlFile xmlFile = xmlFile(xml);
        DslAstTree tree = builder.build(xmlFile, repo);
        DslElementNode var = tree.getAst().getRootElement().getChildElements().get(0);
        DslAttributeNode exprAttr = attr(var, "expression");
        DslAttributeValueNode value = exprAttr.getValue();
        assertFalse(value.isLiteral());
        Optional<ExpressionAstNode> opt = value.getExpression();
        assertTrue(opt.isPresent());
        ExpressionNode e = (ExpressionNode) opt.get();
        assertEquals(ExpressionKind.VARIABLE_REF, e.getKind());
        assertEquals("#", e.getPrefix());
        assertEquals("battery_level", e.getVariableName());
        assertEquals(2, e.getLine());
    }

    public void testMalformedUnclosedTagStillProducesStructuredTree() {
        String xml = "<Lockscreen>\n  <Var name=\"v\"/>\n";
        XmlFile xmlFile = xmlFile(xml);
        DslAstTree tree = builder.build(xmlFile, repo);
        DslElementNode root = tree.getAst().getRootElement();
        assertNotNull(root);
        assertEquals("Lockscreen", root.getTagName());
        assertFalse(root.getChildElements().isEmpty());
        DslElementNode var = root.getChildElements().get(0);
        assertEquals("Var", var.getTagName());
        assertTrue(tree.size() >= 2);
        Optional<XmlTag> rootTagOpt = tree.getTag(root);
        assertTrue(rootTagOpt.isPresent());
    }

    private static XmlTag firstSubTag(XmlTag parent, String name) {
        for (XmlTag t : directSubTags(parent)) {
            if (name.equals(t.getName())) {
                return t;
            }
        }
        return null;
    }

    private static List<XmlTag> directSubTags(XmlTag parent) {
        List<XmlTag> out = new ArrayList<>();
        for (PsiElement c : parent.getChildren()) {
            if (c instanceof XmlTag t) {
                out.add(t);
            }
        }
        return out;
    }

    private static DslAttributeNode attr(DslElementNode node, String name) {
        return node.getAttributes().stream()
                .filter(a -> name.equals(a.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("attr not found: " + name));
    }
}
