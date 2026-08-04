package com.huawei.theme.analysis.plugin.editor.reference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.usageView.UsageInfo;

import com.huawei.theme.analysis.core.semanticanalysis.model.VarDeclaration;
import com.huawei.theme.analysis.plugin.ast.DslAstService;
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

    public void testFunctionReferenceResolvesToMainFileDeclaration() {
        XmlFile main = projectFile("script.xml", "<Lockscreen>"
                + "<Var name='main_value' type='number'/>"
                + "<Include name='function_child.xml'/>"
                + "</Lockscreen>");
        XmlFile child = projectFile("function_child.xml", "<Group><Text x='#main_value'/></Group>");
        XmlTag text = firstSubTag(child.getRootTag(), "Text");

        Set<String> contextPaths = DslAstService.getInstance(getProject()).getContextFilePaths(child);
        assertTrue("context files must contain script main, actual=" + contextPaths,
                contextPaths.contains(main.getVirtualFile().getPath().replace('\\', '/')));

        PsiElement target = VarNameResolver.resolveDeclaration(
                getProject(), child, text, "main_value");

        assertNotNull("function reference must resolve in the including script context", target);
        assertSame(main, hostFileOf(target));
    }

    public void testFunctionAutocompleteSeesMainFileSymbols() {
        projectFile("script_main.xml", "<Lockscreen>"
                + "<Var name='main_value' type='number'/>"
                + "<Include name='function_child.xml'/>"
                + "</Lockscreen>");
        XmlFile child = projectFile("function_child.xml", "<Group><Text x='0'/></Group>");
        XmlTag text = firstSubTag(child.getRootTag(), "Text");

        Set<String> names = VarNameResolver.visibleDeclarations(getProject(), child, text).stream()
                .map(VarDeclaration::getName)
                .collect(Collectors.toSet());

        assertTrue("main-file symbol must be offered in a function file", names.contains("main_value"));
    }

    public void testFunctionSeesMacroGeneratedMainSymbolsAndMainResolvesFunctionSymbol() {
        XmlFile main = projectFile("script.xml", "<Lockscreen>"
                + "<For name='i' from='1' to='3'><Var name='hello_%{i}'/></For>"
                + "<For name='i' from='1' to='3'><Var name='hello_%{i+3}'/></For>"
                + "<For name='i' from='1' to='6'>"
                + "<Var name='hi_%{i}' expression='#hello_%{i}'/></For>"
                + "<Include name='function_1.xml'/>"
                + "<Var name='test' expression='#sub'/>"
                + "</Lockscreen>");
        XmlFile child = projectFile("function_1.xml", "<Var name='sub' expression='#hi_1'/>");

        Set<String> childVisibleNames = visibleNames(child, child.getRootTag());
        assertTrue("function autocomplete must contain macro-generated hi_1", childVisibleNames.contains("hi_1"));
        PsiElement hiTarget = VarNameResolver.resolveDeclaration(
                getProject(), child, child.getRootTag(), "hi_1");
        assertNotNull("function reference #hi_1 must resolve to the main file", hiTarget);
        assertSame(main, hostFileOf(hiTarget));

        XmlTag testVar = main.getRootTag().findSubTags("Var")[0];
        PsiElement subTarget = VarNameResolver.resolveDeclaration(
                getProject(), main, testVar, "sub");
        assertNotNull("main reference #sub must resolve to the function file", subTarget);
        assertSame(child, hostFileOf(subTarget));
    }

    public void testFindUsagesFromMainFindsFunctionReferenceOnPooledThread() throws Exception {
        XmlFile main = projectFile("script_main.xml", "<Lockscreen>"
                + "<Var name='main_value' type='number'/>"
                + "<Include name='function_child.xml'/>"
                + "</Lockscreen>");
        XmlFile child = projectFile("function_child.xml", "<Group><Text x='#main_value'/></Group>");
        XmlTag declaration = firstSubTag(main.getRootTag(), "Var");
        XmlAttributeValue nameValue = declaration.getAttribute("name").getValueElement();
        VarNameElement target = VarNameResolver.findVarNameElement(getProject(), nameValue);
        assertNotNull(target);
        FindUsagesHandler handler = new ThemeDslVarFindUsagesHandlerFactory()
                .createFindUsagesHandler(target, false);
        assertNotNull(handler);
        XmlTag childText = firstSubTag(child.getRootTag(), "Text");
        PsiReference[] childReferences = childText.getAttribute("x").getValueElement().getReferences();
        assertTrue("function expression must expose a host-side variable reference", childReferences.length > 0);
        assertTrue("function reference must resolve to the main declaration before Find Usages",
                Arrays.stream(childReferences)
                        .filter(DslVariableReference.class::isInstance)
                        .map(DslVariableReference.class::cast)
                        .flatMap(reference -> Arrays.stream(reference.multiResolve(false)))
                        .map(result -> result.getElement())
                        .filter(Objects::nonNull)
                        .anyMatch(element -> main.equals(hostFileOf(element))));

        List<UsageInfo> usages = new CopyOnWriteArrayList<>();
        Future<Boolean> search = ApplicationManager.getApplication().executeOnPooledThread(() ->
                handler.processElementUsages(
                        target, usage -> {
                            usages.add(usage);
                            return true;
                        }, new FindUsagesOptions(getProject())));
        boolean completed = search.get(30, TimeUnit.SECONDS);

        assertTrue(completed);
        assertTrue("Find Usages must include the function reference; usages="
                        + usages.stream().map(UsageInfo::getElement).toList(), usages.stream()
                .map(UsageInfo::getElement)
                .filter(Objects::nonNull)
                .anyMatch(element -> child.equals(hostFileOf(element))));
    }

    public void testFindUsagesForMacroDeclarationFindsInterpolatedReferenceOnPooledThread() throws Exception {
        XmlFile main = projectFile("script.xml", "<Lockscreen>"
                + "<For name='i' from='1' to='3'><Var name='hello_%{i}'/></For>"
                + "<For name='i' from='1' to='3'><Var name='hello_%{i+3}'/></For>"
                + "<For name='i' from='1' to='6'>"
                + "<Var name='hi_%{i}' expression='#hello_%{i}'/></For>"
                + "</Lockscreen>");
        XmlTag firstFor = main.getRootTag().findSubTags("For")[0];
        XmlTag declaration = firstSubTag(firstFor, "Var");
        XmlAttributeValue nameValue = declaration.getAttribute("name").getValueElement();
        VarNameElement target = VarNameResolver.findVarNameElement(getProject(), nameValue);
        assertNotNull(target);
        FindUsagesHandler handler = new ThemeDslVarFindUsagesHandlerFactory()
                .createFindUsagesHandler(target, false);
        assertNotNull(handler);

        List<UsageInfo> usages = new CopyOnWriteArrayList<>();
        Future<Boolean> search = ApplicationManager.getApplication().executeOnPooledThread(() ->
                handler.processElementUsages(
                        target, usage -> {
                            usages.add(usage);
                            return true;
                        }, new FindUsagesOptions(getProject())));

        assertTrue(search.get(30, TimeUnit.SECONDS));
        assertTrue("macro declaration Find Usages must include #hello_%{i}", usages.stream()
                .map(UsageInfo::getElement)
                .filter(XmlAttributeValue.class::isInstance)
                .map(XmlAttributeValue.class::cast)
                .anyMatch(value -> "#hello_%{i}".equals(value.getValue())));
    }

    public void testNestedFunctionReferenceResolvesToMainFileDeclaration() {
        XmlFile main = projectFile("script_main.xml", "<Lockscreen>"
                + "<Var name='root_value' type='number'/>"
                + "<Include name='function_middle.xml'/>"
                + "</Lockscreen>");
        projectFile("function_middle.xml", "<Group><Include name='function_leaf.xml'/></Group>");
        XmlFile leaf = projectFile("function_leaf.xml", "<Group><Text x='#root_value'/></Group>");
        XmlTag text = firstSubTag(leaf.getRootTag(), "Text");

        Set<String> contextPaths = DslAstService.getInstance(getProject()).getContextFilePaths(leaf);
        assertEquals(3, contextPaths.size());

        PsiElement target = VarNameResolver.resolveDeclaration(
                getProject(), leaf, text, "root_value");

        assertNotNull("nested function reference must resolve in the script context", target);
        assertSame(main, hostFileOf(target));
    }

    public void testFunctionContextCacheInvalidatesWhenMainFileChanges() {
        XmlFile main = projectFile("script_main.xml",
                "<Lockscreen><Include name='function_child.xml'/></Lockscreen>");
        XmlFile child = projectFile("function_child.xml", "<Group><Text x='0'/></Group>");
        XmlTag text = firstSubTag(child.getRootTag(), "Text");

        Set<String> before = visibleNames(child, text);
        assertFalse(before.contains("added_later"));

        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(main);
        assertNotNull(document);
        ApplicationManager.getApplication().runWriteAction((Runnable) () -> document.setText(
                "<Lockscreen><Var name='added_later' type='number'/>"
                        + "<Include name='function_child.xml'/></Lockscreen>"));
        PsiDocumentManager.getInstance(getProject()).commitDocument(document);

        assertTrue("editing the context root must invalidate the function cache",
                visibleNames(child, text).contains("added_later"));
    }

    public void testFunctionUsesAllIncludingScriptContexts() {
        XmlFile firstMain = projectFile("script_first.xml", "<Lockscreen>"
                + "<Var name='first_value' type='number'/>"
                + "<Include name='function_shared.xml'/>"
                + "</Lockscreen>");
        projectFile("script_second.xml", "<Lockscreen>"
                + "<Var name='second_value' type='number'/>"
                + "<Include name='function_shared.xml'/>"
                + "</Lockscreen>");
        XmlFile child = projectFile("function_shared.xml", "<Group><Text x='#first_value'/></Group>");
        XmlTag text = firstSubTag(child.getRootTag(), "Text");

        Set<String> names = visibleNames(child, text);

        assertEquals(2, DslAstService.getInstance(getProject()).getAnalysisContexts(child).size());
        assertTrue(names.contains("first_value"));
        assertTrue(names.contains("second_value"));
        PsiElement target = VarNameResolver.resolveDeclaration(
                getProject(), child, text, "first_value");
        assertNotNull(target);
        assertSame(firstMain, hostFileOf(target));
    }

    public void testFunctionCompletionCopyUsesPhysicalFileContexts() {
        projectFile("script.xml", "<Lockscreen>"
                + "<Var name='main_value' type='number'/>"
                + "<Include name='function_completion.xml'/>"
                + "</Lockscreen>");
        XmlFile child = projectFile("function_completion.xml", "<Group><Text x='0'/></Group>");
        XmlFile completionCopy = (XmlFile) child.copy();
        XmlTag copyText = firstSubTag(completionCopy.getRootTag(), "Text");

        Set<String> names = visibleNames(completionCopy, copyText);

        assertTrue("completion PSI copy must retain the physical function's contexts", names.contains("main_value"));
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

    private XmlFile projectFile(String name, String content) {
        XmlFile[] result = new XmlFile[1];
        ApplicationManager.getApplication().runWriteAction((Runnable) () -> {
            try {
                VirtualFile sourceRoot = getSourceRoot();
                VirtualFile directory = sourceRoot.findChild("include-context");
                if (directory == null) {
                    directory = sourceRoot.createChildDirectory(this, "include-context");
                }
                VirtualFile file = directory.findChild(name);
                if (file == null) {
                    file = directory.createChildData(this, name);
                }
                VfsUtil.saveText(file, content);
                result[0] = (XmlFile) getPsiManager().findFile(file);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        assertNotNull(result[0]);
        return result[0];
    }

    private Set<String> visibleNames(XmlFile file, XmlTag tag) {
        return VarNameResolver.visibleDeclarations(getProject(), file, tag).stream()
                .map(VarDeclaration::getName)
                .collect(Collectors.toSet());
    }

    private PsiElement hostFileOf(PsiElement element) {
        PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(getProject()).getInjectionHost(element);
        return host != null ? host.getContainingFile() : element.getContainingFile();
    }
}
