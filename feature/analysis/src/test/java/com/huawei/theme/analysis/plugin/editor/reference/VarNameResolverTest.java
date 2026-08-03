package com.huawei.theme.analysis.plugin.editor.reference;

import java.util.ArrayList;
import java.util.List;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightPlatformTestCase;

import com.huawei.theme.analysis.plugin.editor.varname.VarNameElement;

public class VarNameResolverTest extends LightPlatformTestCase {

    private XmlFile xmlFile(String content) {
        Project project = getProject();
        return (XmlFile) PsiFileFactory.getInstance(project)
                .createFileFromText("test.xml", XmlFileType.INSTANCE, content);
    }

    /**
     * LightPlatformTestCase does not load the plugin's {@code multiHostInjector} extension,
     * so the {@link VarNameElement} injection is not materialized in-test. {@code resolveDeclaration}
     * therefore falls back to the declaring attribute value. Asserting that fallback lands on the
     * <em>correct</em> host element + attribute verifies the AST SymbolTable lookup, the PSI↔AST map,
     * and the {@code hostAttrName} ("name" for &lt;Var&gt;) path. The VarNameElement injection itself
     * is platform-wired and exercised at IDE runtime by {@link DslVariableReference}/{@link DslVariableRefElement}.
     */
    public void testResolveUserVarViaSymbolTable() {
        String xml = "<Lockscreen>\n"
                + "  <Var name=\"v\" type=\"number\" expression=\"1\"/>\n"
                + "  <Text x=\"#v\"/>\n"
                + "</Lockscreen>";
        XmlFile xmlFile = xmlFile(xml);
        XmlTag root = xmlFile.getRootTag();
        assertNotNull(root);
        XmlTag text = firstSubTag(root, "Text");
        assertNotNull(text);

        PsiElement target = VarNameResolver.resolveDeclaration(getProject(), xmlFile, text, "v");
        assertNotNull("user <Var> 'v' must resolve via SymbolTable+map", target);
        XmlAttribute attr = PsiTreeUtil.getParentOfType((XmlAttributeValue) target, XmlAttribute.class);
        assertNotNull(attr);
        assertEquals("name", attr.getName());
        XmlTag declTag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
        assertNotNull(declTag);
        assertEquals("Var", declTag.getName());
        assertEquals("v", declTag.getAttributeValue("name"));
    }

    public void testResolveIndexFlagLocalViaSymbolTable() {
        String xml = "<Lockscreen>\n"
                + "  <Array indexFlag=\"i\" frequency=\"3\">\n"
                + "    <Text x=\"#i\"/>\n"
                + "  </Array>\n"
                + "</Lockscreen>";
        XmlFile xmlFile = xmlFile(xml);
        XmlTag root = xmlFile.getRootTag();
        assertNotNull(root);
        XmlTag array = firstSubTag(root, "Array");
        assertNotNull(array);
        XmlTag text = firstSubTag(array, "Text");
        assertNotNull(text);

        PsiElement target = VarNameResolver.resolveDeclaration(getProject(), xmlFile, text, "i");
        assertNotNull("indexFlag local 'i' must resolve via SymbolTable+map (oversight fix)", target);
        XmlAttribute attr = PsiTreeUtil.getParentOfType((XmlAttributeValue) target, XmlAttribute.class);
        assertNotNull(attr);
        assertEquals("indexFlag", attr.getName());
        XmlTag declTag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
        assertNotNull(declTag);
        assertEquals("Array", declTag.getName());
        assertEquals("i", declTag.getAttributeValue("indexFlag"));
    }

    public void testResolveReturnsNullForUnknownVar() {
        String xml = "<Lockscreen>\n"
                + "  <Var name=\"v\" type=\"number\" expression=\"1\"/>\n"
                + "  <Text x=\"#nope\"/>\n"
                + "</Lockscreen>";
        XmlFile xmlFile = xmlFile(xml);
        XmlTag root = xmlFile.getRootTag();
        XmlTag text = firstSubTag(root, "Text");
        PsiElement target = VarNameResolver.resolveDeclaration(getProject(), xmlFile, text, "nope");
        assertNull("unknown variable resolves to null (soft)", target);
    }

    public void testResolveReturnsNullForPresetGlobal() {
        String xml = "<Lockscreen>\n"
                + "  <Text x=\"#battery_level\"/>\n"
                + "</Lockscreen>";
        XmlFile xmlFile = xmlFile(xml);
        XmlTag root = xmlFile.getRootTag();
        XmlTag text = firstSubTag(root, "Text");
        PsiElement target = VarNameResolver.resolveDeclaration(getProject(), xmlFile, text, "battery_level");
        assertNull("preset global has no PSI declaration; resolve returns null (no squiggle)", target);
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
}
