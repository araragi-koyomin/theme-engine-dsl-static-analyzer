package com.huawei.theme.analysis.core.macro;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextRootResolverTest {

    private final RuleRepository repo = new JsonRuleLoader()
            .loadFromDirectory(System.getProperty("user.dir") + "/src/main/resources/rules");
    private final MacroExpander expander = new MacroExpander(repo);
    private final ContextRootResolver resolver = new ContextRootResolver(expander);

    @Test
    void findsSingleContextRoot() throws Exception {
        Path dir = Files.createTempDirectory("ctx-root-");
        writeFile(dir, "function_greeting.xml", "<Group><Var name='v'/></Group>");
        writeFile(dir, "script.xml",
                "<Lockscreen><Include name='function_greeting.xml' who='World'/></Lockscreen>");
        List<String> roots = resolver.findContextRoots(dir.resolve("function_greeting.xml").toString());
        assertEquals(1, roots.size());
        assertTrue(roots.get(0).replace('\\', '/').endsWith("script.xml"));
    }

    @Test
    void noContextRootWhenNoScriptIncludesIt() throws Exception {
        Path dir = Files.createTempDirectory("ctx-noroot-");
        writeFile(dir, "function_greeting.xml", "<Group/>");
        // a script_*.xml that does NOT include this function
        writeFile(dir, "script_other.xml", "<Lockscreen><Var name='x'/></Lockscreen>");
        List<String> roots = resolver.findContextRoots(dir.resolve("function_greeting.xml").toString());
        assertTrue(roots.isEmpty());
    }

    @Test
    void multipleContextRootsReported() throws Exception {
        Path dir = Files.createTempDirectory("ctx-multi-");
        writeFile(dir, "function_greeting.xml", "<Group/>");
        writeFile(dir, "script_main1.xml",
                "<Lockscreen><Include name='function_greeting.xml'/></Lockscreen>");
        writeFile(dir, "script_main2.xml",
                "<Lockscreen><Include name='function_greeting.xml'/></Lockscreen>");
        List<String> roots = resolver.findContextRoots(dir.resolve("function_greeting.xml").toString());
        assertEquals(2, roots.size());
    }

    @Test
    void findsContextRootThroughNestedFunctionInclude() throws Exception {
        Path dir = Files.createTempDirectory("ctx-nested-");
        writeFile(dir, "function_leaf.xml", "<Group/>");
        writeFile(dir, "function_middle.xml",
                "<Group><Include name='function_leaf.xml'/></Group>");
        writeFile(dir, "script_main.xml",
                "<Lockscreen><Include name='function_middle.xml'/></Lockscreen>");

        List<String> roots = resolver.findContextRoots(dir.resolve("function_leaf.xml").toString());

        assertEquals(1, roots.size());
        assertTrue(roots.get(0).replace('\\', '/').endsWith("script_main.xml"));
    }

    @Test
    void extractsIncludeParams() throws Exception {
        Path dir = Files.createTempDirectory("ctx-params-");
        writeFile(dir, "function_greeting.xml", "<Group/>");
        writeFile(dir, "script_main.xml",
                "<Lockscreen><Include name='function_greeting.xml' who='World' count='3'/></Lockscreen>");
        Map<String, String> params = resolver.extractIncludeParams(
                dir.resolve("script_main.xml").toString(), "function_greeting.xml");
        assertEquals("World", params.get("who"));
        assertEquals("3", params.get("count"));
        assertTrue(params.containsKey("name") == false, "the name attr is not a param");
    }

    private static void writeFile(Path dir, String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
    }
}
