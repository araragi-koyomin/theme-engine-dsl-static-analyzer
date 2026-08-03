package com.huawei.theme.analysis.plugin.editor.reference;

import java.util.ArrayList;
import java.util.List;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

import com.huawei.theme.analysis.core.semanticanalysis.model.VarDeclaration;
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

    public void testResolveExpandedVarFromForBodyViaDemacroedAst() {
        String xml = "<Lockscreen>\n"
                + "  <For name=\"i\" from=\"1\" to=\"3\">\n"
                + "    <Var name=\"index_%{i}\" type=\"number\"/>\n"
                + "  </For>\n"
                + "  <Text x=\"#index_1\"/>\n"
                + "</Lockscreen>";
        XmlFile xmlFile = xmlFile(xml);
        XmlTag root = xmlFile.getRootTag();
        XmlTag text = firstSubTag(root, "Text");
        PsiElement target = VarNameResolver.resolveDeclaration(getProject(), xmlFile, text, "index_1");
        assertNotNull("#index_1 must resolve via demacroed AST + two-hop map", target);
        // Two-hop lands on the ORIGINAL <Var name="index_%{i}"> in the source PSI (raw, pre-interpolation).
        XmlAttribute attr = PsiTreeUtil.getParentOfType((XmlAttributeValue) target, XmlAttribute.class);
        assertNotNull(attr);
        assertEquals("name", attr.getName());
        XmlTag declTag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
        assertNotNull(declTag);
        assertEquals("Var", declTag.getName());
        assertEquals("index_%{i}", declTag.getAttributeValue("name"));
    }

    public void testAutocompleteSeesExpandedForVarNames() {
        String xml = "<Lockscreen>\n"
                + "  <For name=\"i\" from=\"1\" to=\"3\">\n"
                + "    <Var name=\"v_%{i}\" type=\"number\"/>\n"
                + "  </For>\n"
                + "  <Text x=\"0\"/>\n"
                + "</Lockscreen>";
        XmlFile xmlFile = xmlFile(xml);
        XmlTag root = xmlFile.getRootTag();
        XmlTag text = firstSubTag(root, "Text");
        List<VarDeclaration> visible = VarNameResolver.visibleDeclarations(getProject(), xmlFile, text);
        Set<String> names = visible.stream().map(VarDeclaration::getName).collect(Collectors.toSet());
        assertTrue("expanded v_1 visible", names.contains("v_1"));
        assertTrue("expanded v_2 visible", names.contains("v_2"));
        assertTrue("expanded v_3 visible", names.contains("v_3"));
    }

    public void testCursorOnForTagResolvesViaEnclosingScope() {
        // Cursor/reference inside the <For> body itself — scope is the demacroed expansion's scope.
        String xml = "<Lockscreen>\n"
                + "  <For name=\"i\" from=\"1\" to=\"2\">\n"
                + "    <Var name=\"v_%{i}\" type=\"number\"/>\n"
                + "    <Text x=\"#v_1\"/>\n"
                + "  </For>\n"
                + "</Lockscreen>";
        XmlFile xmlFile = xmlFile(xml);
        XmlTag root = xmlFile.getRootTag();
        XmlTag forTag = firstSubTag(root, "For");
        XmlTag text = firstSubTag(forTag, "Text");
        PsiElement target = VarNameResolver.resolveDeclaration(getProject(), xmlFile, text, "v_1");
        assertNotNull("#v_1 inside <For> body must resolve", target);
    }

    public void testRawReferenceInsideForIsInterpolatedToCopyName() {
        // The reference text in source is "#v_%{i}" (raw). resolve must interpolate v_%{i} with the
        // demacroed first-copy's scope {i:1} -> v_1, then look up the demacroed <Var v_1>.
        String xml = "<Lockscreen>\n"
                + "  <For name=\"i\" from=\"1\" to=\"3\">\n"
                + "    <Var name=\"v_%{i}\" type=\"number\"/>\n"
                + "    <Text x=\"#v_%{i}\"/>\n"
                + "  </For>\n"
                + "</Lockscreen>";
        XmlFile xmlFile = xmlFile(xml);
        XmlTag root = xmlFile.getRootTag();
        XmlTag forTag = firstSubTag(root, "For");
        XmlTag text = firstSubTag(forTag, "Text");
        Optional<VarDeclaration> decl = VarNameResolver.lookupDeclaration(getProject(), xmlFile, text, "v_%{i}");
        assertTrue("raw v_%{i} must interpolate to v_1 and resolve", decl.isPresent());
        assertEquals("v_1", decl.get().getName());
    }

    public void testMacroReferenceMultiResolvesToAllCopies() {
        // User manually defines <Var x_1> and <Var x_2> (different locations), then refers to both
        // via #x_%{i} inside a <For>. The single source reference must multi-resolve to BOTH
        // declarations so find-usages from either finds it and jump-to-def offers both.
        String xml = "<Lockscreen>\n"
                + "  <Var name=\"x_1\" type=\"number\"/>\n"
                + "  <Var name=\"x_2\" type=\"number\"/>\n"
                + "  <For name=\"i\" from=\"1\" to=\"2\">\n"
                + "    <Text x=\"#x_%{i}\"/>\n"
                + "  </For>\n"
                + "</Lockscreen>";
        XmlFile xmlFile = xmlFile(xml);
        XmlTag root = xmlFile.getRootTag();
        XmlTag forTag = firstSubTag(root, "For");
        XmlTag text = firstSubTag(forTag, "Text");
        List<PsiElement> targets = VarNameResolver.resolveDeclarationsMulti(getProject(), xmlFile, text, "x_%{i}");
        assertEquals("#x_%{i} must multi-resolve to both x_1 and x_2", 2, targets.size());
        Set<String> declNames = new HashSet<>();
        for (PsiElement t : targets) {
            XmlAttribute attr = PsiTreeUtil.getParentOfType((XmlAttributeValue) t, XmlAttribute.class);
            XmlTag declTag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
            declNames.add(declTag.getAttributeValue("name"));
        }
        assertTrue("x_1 is a resolve target", declNames.contains("x_1"));
        assertTrue("x_2 is a resolve target", declNames.contains("x_2"));
    }

    public void testMacroGeneratedDeclarationsDedupToOneTarget() {
        // <Var name="hello_%{i}"> inside a <For> expands to 3 demacoed <Var hello_1/2/3>, but they all
        // two-hop back to the SAME original <Var name="hello_%{i}"> source. So #hello_%{i} (referenced
        // in another <For>) must multi-resolve to a SINGLE target, not 3 identical ones.
        String xml = "<Lockscreen>\n"
                + "  <For name=\"i\" from=\"1\" to=\"3\">\n"
                + "    <Var name=\"hello_%{i}\"/>\n"
                + "  </For>\n"
                + "  <For name=\"i\" from=\"1\" to=\"3\">\n"
                + "    <Var name=\"hi_%{i}\" expression=\"#hello_%{i}\"/>\n"
                + "  </For>\n"
                + "</Lockscreen>";
        XmlFile xmlFile = xmlFile(xml);
        XmlTag root = xmlFile.getRootTag();
        List<XmlTag> forTags = directSubTags(root).stream().filter(t -> "For".equals(t.getName())).toList();
        XmlTag varHi = firstSubTag(forTags.get(1), "Var");
        List<PsiElement> targets = VarNameResolver.resolveDeclarationsMulti(getProject(), xmlFile, varHi, "hello_%{i}");
        assertEquals("3 demacoed copies of one <Var> must collapse to 1 resolve target", 1, targets.size());
    }

    public void testIplus3ReferenceResolvesToBothMacroSources() {
        // hello_1/2/3 come from <Var name="hello_%{i}"> (first For); hello_4/5/6 come from
        // <Var name="hello_%{i+3}"> (second For). #hello_%{i} in the third For (i=1..6) must resolve
        // to BOTH source declarations, so find-usages from either <Var> finds the reference.
        String xml = "<Lockscreen>\n"
                + "  <For name=\"i\" from=\"1\" to=\"3\">\n"
                + "    <Var name=\"hello_%{i}\"/>\n"
                + "  </For>\n"
                + "  <For name=\"i\" from=\"1\" to=\"3\">\n"
                + "    <Var name=\"hello_%{i+3}\"/>\n"
                + "  </For>\n"
                + "  <For name=\"i\" from=\"1\" to=\"6\">\n"
                + "    <Var name=\"hi_%{i}\" expression=\"#hello_%{i}\"/>\n"
                + "  </For>\n"
                + "</Lockscreen>";
        XmlFile xmlFile = xmlFile(xml);
        XmlTag root = xmlFile.getRootTag();
        List<XmlTag> forTags = directSubTags(root).stream().filter(t -> "For".equals(t.getName())).toList();
        XmlTag varHi = firstSubTag(forTags.get(2), "Var");
        List<PsiElement> targets = VarNameResolver.resolveDeclarationsMulti(getProject(), xmlFile, varHi, "hello_%{i}");
        assertEquals("must resolve to both hello_%{i} and hello_%{i+3} sources", 2, targets.size());
        Set<String> declNameAttrs = new HashSet<>();
        for (PsiElement t : targets) {
            XmlAttribute attr = PsiTreeUtil.getParentOfType((XmlAttributeValue) t, XmlAttribute.class);
            XmlTag declTag = PsiTreeUtil.getParentOfType(attr, XmlTag.class);
            declNameAttrs.add(declTag.getAttributeValue("name"));
        }
        assertTrue("hello_%{i} source is a target", declNameAttrs.contains("hello_%{i}"));
        assertTrue("hello_%{i+3} source is a target", declNameAttrs.contains("hello_%{i+3}"));
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
