package com.huawei.theme.analysis.plugin.rule;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import com.huawei.theme.analysis.core.rulelibrary.JsonRuleLoader;
import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;

/**
 * 应用级服务，加载并缓存内置ThemeDSL规则库。
 *
 * <p>插件打包为JAR后，rules资源位于JAR内部，无法用Files.walk遍历真实文件系统。
 * 本服务使用IntelliJ VFS（{@link VfsUtil#findFileByURL}）透明访问JAR内/本地资源，
 * 读取JSON后委托核心{@link JsonRuleLoader}的Reader重载完成解析+normalize，
 * 避免在插件层重复GSON配置与null安全逻辑。{@link RuleRepository}实例懒加载且全局缓存。</p>
 *
 * <p>资源锚点选取已知文件rules/global_vars.json而非目录，因为JAR不保证目录条目存在；
 * 取其parent即为rules根目录。对解压classes场景（file协议且未入VFS）使用refresh回退。</p>
 */
public class RuleRepositoryService {

    private static final Logger LOG = Logger.getInstance(RuleRepositoryService.class);

    private static final String ANCHOR_RESOURCE = "/rules/global_vars.json";
    private static final String ELEMENTS_DIR = "elements";
    private static final String COMMANDS_DIR = "commands";
    private static final String GLOBAL_VARS_FILE = "global_vars.json";
    private static final String RULE_SOURCES_FILE = "rule_sources.json";

    private volatile RuleRepository repository;

    public static RuleRepositoryService getInstance() {
        return ApplicationManager.getApplication().getService(RuleRepositoryService.class);
    }

    public RuleRepository getRuleRepository() {
        RuleRepository result = repository;
        if (result == null) {
            synchronized (this) {
                result = repository;
                if (result == null) {
                    result = loadRepository();
                    repository = result;
                }
            }
        }
        return result;
    }

    private static RuleRepository loadRepository() {
        VirtualFile rulesDir = findRulesDir();
        JsonRuleLoader loader = new JsonRuleLoader();
        if (rulesDir == null) {
            LOG.warn("ThemeDSL rules resource directory not found: /rules");
            return loader.buildRuleRepository(
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }

        Map<String, DslElementRule> elementRules = new HashMap<>();
        collectElementRules(loader, rulesDir, elementRules);
        Map<String, DslGlobalVar> globalVars = loadGlobalVars(loader, rulesDir);
        Map<String, RuleSource> ruleSources = loadRuleSources(loader, rulesDir);
        return loader.buildRuleRepository(elementRules, globalVars, ruleSources);
    }

    private static VirtualFile findRulesDir() {
        URL anchor = RuleRepositoryService.class.getResource(ANCHOR_RESOURCE);
        if (anchor == null) {
            return null;
        }
        VirtualFile file = VfsUtil.findFileByURL(anchor);
        if (file == null && "file".equals(anchor.getProtocol())) {
            try {
                file = VfsUtil.findFile(Paths.get(anchor.toURI()), true);
            } catch (URISyntaxException | RuntimeException e) {
                LOG.warn("Failed to resolve rules resource: " + anchor, e);
            }
        }
        return file == null ? null : file.getParent();
    }

    private static void collectElementRules(JsonRuleLoader loader, VirtualFile rulesDir,
                                            Map<String, DslElementRule> result) {
        VirtualFile elements = rulesDir.findChild(ELEMENTS_DIR);
        if (elements != null) {
            walkAndLoadElements(loader, elements, result);
        }
        VirtualFile commands = rulesDir.findChild(COMMANDS_DIR);
        if (commands != null) {
            walkAndLoadElements(loader, commands, result);
        }
    }

    private static void walkAndLoadElements(JsonRuleLoader loader, VirtualFile dir,
                                            Map<String, DslElementRule> result) {
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                walkAndLoadElements(loader, child, result);
            } else if (child.getName().endsWith(".json")) {
                try (Reader reader = new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8)) {
                    loader.loadElementRule(reader, result);
                } catch (Exception e) {
                    LOG.warn("Failed to load element rule: " + child.getUrl(), e);
                }
            }
        }
    }

    private static Map<String, DslGlobalVar> loadGlobalVars(JsonRuleLoader loader, VirtualFile rulesDir) {
        VirtualFile file = rulesDir.findChild(GLOBAL_VARS_FILE);
        if (file == null) {
            return Collections.emptyMap();
        }
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return loader.loadGlobalVars(reader);
        } catch (Exception e) {
            LOG.warn("Failed to load global vars: " + file.getUrl(), e);
            return Collections.emptyMap();
        }
    }

    private static Map<String, RuleSource> loadRuleSources(JsonRuleLoader loader, VirtualFile rulesDir) {
        VirtualFile file = rulesDir.findChild(RULE_SOURCES_FILE);
        if (file == null) {
            return Collections.emptyMap();
        }
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return loader.loadRuleSources(reader);
        } catch (Exception e) {
            LOG.warn("Failed to load rule sources: " + file.getUrl(), e);
            return Collections.emptyMap();
        }
    }
}
