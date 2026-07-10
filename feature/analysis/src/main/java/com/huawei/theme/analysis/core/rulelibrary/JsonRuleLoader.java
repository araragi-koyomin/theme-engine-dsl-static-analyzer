package com.huawei.theme.analysis.core.rulelibrary;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import com.huawei.theme.analysis.core.expression.FunctionSignatureLibrary;
import com.huawei.theme.analysis.core.rulelibrary.model.AttrTypeSpec;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.rulelibrary.model.DslGlobalVar;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleConstraint;
import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;
import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;
import com.huawei.theme.analysis.core.shared.diagnostic.adapter.DiagnosticSeverityAdapter;

/**
 * JSON规则库加载器，从指定目录加载规则数据并构建RuleRepository所需的三个Map。
 *
 * <p>职责边界：JsonRuleLoader只负责加载（文件扫描+JSON反序列化+null安全normalize），
 * DefaultRuleRepository只负责查询。两者通过Map参数连接，实现职责分离。</p>
 *
 * <p>加载逻辑：递归扫描elements/和commands/目录下所有.json文件→GSON反序列化为DslElementRule；
 * 读取global_vars.json→反序列化为DslGlobalVar列表；读取rule_sources.json→反序列化为RuleSource列表。</p>
 *
 * <p>null安全策略：JSON中null字段在normalize阶段统一替换为空集合/空映射，
 * 避免下游模块NPE风险。如constraints为null→Collections.emptyList()。</p>
 */
public class JsonRuleLoader {
    /** 元素规则JSON文件子目录名 */
    private static final String ELEMENTS_DIR = "elements";
    /** 命令规则JSON文件子目录名 */
    private static final String COMMANDS_DIR = "commands";
    /** 全局变量JSON文件名 */
    private static final String GLOBAL_VARS_FILE = "global_vars.json";
    /** 规则来源映射JSON文件名 */
    private static final String RULE_SOURCES_FILE = "rule_sources.json";

    /** GSON实例，注册了DiagnosticSeverityAdapter处理severity字段映射 */
    private final Gson gson;

    /**
     * 构造JsonRuleLoader，初始化GSON实例。
     */
    public JsonRuleLoader() {
        this.gson = createGson();
    }

    /**
     * 创建GSON实例，注册DiagnosticSeverityAdapter以实现severity枚举↔JSON字符串映射。
     *
     * @return 配置好的GSON实例
     */
    private static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(DiagnosticSeverity.class, new DiagnosticSeverityAdapter())
                .setPrettyPrinting()
                .create();
    }

    /**
     * 从指定目录加载所有元素和命令规则条目。
     *
     * <p>分别扫描elements/和commands/子目录下的所有.json文件，
     * 递归遍历子目录结构（如elements/view/、elements/variable/），
     * 合并为以elementName为key的统一Map。</p>
     *
     * @param rulesDir 规则库根目录路径
     * @return 以elementName为key的规则条目映射
     */
    public Map<String, DslElementRule> loadElementRules(String rulesDir) {
        Map<String, DslElementRule> result = new HashMap<>();
        Path basePath = Path.of(rulesDir);

        loadElementRulesFromDir(basePath.resolve(ELEMENTS_DIR), result);
        loadElementRulesFromDir(basePath.resolve(COMMANDS_DIR), result);

        return result;
    }

    /**
     * 递归扫描指定目录下所有.json文件并加载为DslElementRule。
     *
     * @param dir 目录路径，不存在时直接返回
     * @param result 加载结果收集Map
     */
    private void loadElementRulesFromDir(Path dir, Map<String, DslElementRule> result) {
        if (!Files.exists(dir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> loadSingleElementRule(p, result));
        } catch (IOException e) {
            throw new RuleLoadException("Failed to walk directory: " + dir, e);
        }
    }

    /**
     * 加载单个JSON文件为DslElementRule并加入结果Map。
     *
     * <p>加载成功后调用normalizeElementRule确保null字段安全。
     * elementName为null的规则条目被跳过（数据错误保护）。</p>
     *
     * @param jsonPath JSON文件路径
     * @param result 加载结果收集Map
     */
    private void loadSingleElementRule(Path jsonPath, Map<String, DslElementRule> result) {
        try (FileReader reader = new FileReader(jsonPath.toFile(), StandardCharsets.UTF_8)) {
            DslElementRule rule = gson.fromJson(reader, DslElementRule.class);
            if (rule != null && rule.getElementName() != null) {
                normalizeElementRule(rule);
                result.put(rule.getElementName(), rule);
            }
        } catch (IOException e) {
            throw new RuleLoadException("Failed to read file: " + jsonPath, e);
        } catch (JsonSyntaxException e) {
            throw new RuleLoadException("JSON syntax error in file: " + jsonPath, e);
        }
    }

    /**
     * 对DslElementRule的null字段进行统一替换为空集合/空映射，避免下游模块NPE。
     *
     * @param rule 待normalize的规则条目
     */
    private void normalizeElementRule(DslElementRule rule) {
        if (rule.getRequiredAttrs() == null) {
            rule.setRequiredAttrs(Collections.emptyList());
        }
        if (rule.getOptionalAttrs() == null) {
            rule.setOptionalAttrs(Collections.emptyList());
        }
        if (rule.getConstraints() == null) {
            rule.setConstraints(Collections.emptyList());
        }
        if (rule.getAttrTypes() == null) {
            rule.setAttrTypes(Collections.emptyMap());
        }
        if (rule.getScope() == null) {
            rule.setScope(Collections.emptyMap());
        }
        if (rule.getDeviceSupport() == null) {
            rule.setDeviceSupport(Collections.emptyMap());
        }
        if (rule.getAllowedParents() == null) {
            rule.setAllowedParents(Collections.emptyList());
        }

        normalizeAttrTypes(rule);
        normalizeConstraints(rule);
    }

    /**
     * 对AttrTypeSpec的null字段进行统一替换。
     *
     * @param rule 包含待normalize AttrTypeSpec的规则条目
     */
    private void normalizeAttrTypes(DslElementRule rule) {
        for (AttrTypeSpec spec : rule.getAttrTypes().values()) {
            if (spec.getEnumValues() == null) {
                spec.setEnumValues(Collections.emptyList());
            }
            if (spec.getAliases() == null) {
                spec.setAliases(Collections.emptyList());
            }
        }
    }

    /**
     * 对RuleConstraint的null字段进行统一替换。
     *
     * @param rule 包含待normalize RuleConstraint的规则条目
     */
    private void normalizeConstraints(DslElementRule rule) {
        for (RuleConstraint constraint : rule.getConstraints()) {
            if (constraint.getSuggestedFixes() == null) {
                constraint.setSuggestedFixes(Collections.emptyList());
            }
            for (SuggestedFix fix : constraint.getSuggestedFixes()) {
                if (fix.getType() == null || fix.getType().isEmpty()) {
                    fix.setType("UNKNOWN");
                }
            }
        }
    }

    /**
     * 从global_vars.json加载全局变量条目列表。
     *
     * <p>文件不存在时返回空Map（降级运行，非异常）。
     * 加载成功后对每个DslGlobalVar执行normalizeGlobalVar确保null字段安全。</p>
     *
     * @param rulesDir 规则库根目录路径
     * @return 以变量名为key的全局变量映射
     */
    public Map<String, DslGlobalVar> loadGlobalVars(String rulesDir) {
        Path filePath = Path.of(rulesDir, GLOBAL_VARS_FILE);
        if (!Files.exists(filePath)) {
            return Collections.emptyMap();
        }

        try (FileReader reader = new FileReader(filePath.toFile(), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<DslGlobalVar>>() {}.getType();
            List<DslGlobalVar> vars = gson.fromJson(reader, listType);
            if (vars == null) {
                return Collections.emptyMap();
            }

            Map<String, DslGlobalVar> result = new HashMap<>();
            for (DslGlobalVar var : vars) {
                normalizeGlobalVar(var);
                result.put(var.getName(), var);
            }
            return result;
        } catch (IOException e) {
            throw new RuleLoadException("Failed to read global_vars file: " + filePath, e);
        } catch (JsonSyntaxException e) {
            throw new RuleLoadException("JSON syntax error in global_vars file: " + filePath, e);
        }
    }

    /**
     * 对DslGlobalVar的null字段进行统一替换。
     *
     * @param var 待normalize的全局变量条目
     */
    private void normalizeGlobalVar(DslGlobalVar var) {
        if (var.getConstraints() == null) {
            var.setConstraints(Collections.emptyList());
        }
        for (RuleConstraint constraint : var.getConstraints()) {
            if (constraint.getSuggestedFixes() == null) {
                constraint.setSuggestedFixes(Collections.emptyList());
            }
            for (SuggestedFix fix : constraint.getSuggestedFixes()) {
                if (fix.getType() == null || fix.getType().isEmpty()) {
                    fix.setType("UNKNOWN");
                }
            }
        }
    }

    /**
     * 从rule_sources.json加载规则来源追溯条目。
     *
     * <p>文件不存在时返回空Map。JSON格式为RuleSource列表，
     * 加载后转为以ruleId为key的映射。</p>
     *
     * @param rulesDir 规则库根目录路径
     * @return 以ruleId为key的规则来源映射
     */
    public Map<String, RuleSource> loadRuleSources(String rulesDir) {
        Path filePath = Path.of(rulesDir, RULE_SOURCES_FILE);
        if (!Files.exists(filePath)) {
            return Collections.emptyMap();
        }

        try (FileReader reader = new FileReader(filePath.toFile(), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<RuleSource>>() {}.getType();
            List<RuleSource> sources = gson.fromJson(reader, listType);
            if (sources == null) {
                return Collections.emptyMap();
            }

            return sources.stream()
                    .collect(Collectors.toMap(RuleSource::getRuleId, s -> s));
        } catch (IOException e) {
            throw new RuleLoadException("Failed to read rule_sources file: " + filePath, e);
        } catch (JsonSyntaxException e) {
            throw new RuleLoadException("JSON syntax error in rule_sources file: " + filePath, e);
        }
    }

    /**
     * 从三个Map构建DefaultRuleRepository实例。
     *
     * @param elementRules 元素规则条目映射
     * @param globalVars 全局变量条目映射
     * @param ruleSources 规则来源追溯条目映射
     * @return 构建好的RuleRepository实例
     */
    public RuleRepository buildRuleRepository(
            Map<String, DslElementRule> elementRules,
            Map<String, DslGlobalVar> globalVars,
            Map<String, RuleSource> ruleSources) {
        return buildRuleRepository(elementRules, globalVars, ruleSources, null);
    }

    public RuleRepository buildRuleRepository(
            Map<String, DslElementRule> elementRules,
            Map<String, DslGlobalVar> globalVars,
            Map<String, RuleSource> ruleSources,
            FunctionSignatureLibrary functionLibrary) {
        return new DefaultRuleRepository(elementRules, globalVars, ruleSources, functionLibrary);
    }

    /**
     * 一步加载：从指定目录加载所有数据并构建RuleRepository实例。
     *
     * <p>组合调用loadElementRules+loadGlobalVars+loadRuleSources+buildRuleRepository，
     * 是CLI和IDEA插件初始化时最常用的便捷入口。</p>
     *
     * @param rulesDir 规则库根目录路径
     * @return 构建好的RuleRepository实例
     */
    public RuleRepository loadFromDirectory(String rulesDir) {
        return loadFromDirectory(rulesDir, null);
    }

    public RuleRepository loadFromDirectory(String rulesDir, FunctionSignatureLibrary functionLibrary) {
        Map<String, DslElementRule> elementRules = loadElementRules(rulesDir);
        Map<String, DslGlobalVar> globalVars = loadGlobalVars(rulesDir);
        Map<String, RuleSource> ruleSources = loadRuleSources(rulesDir);
        return buildRuleRepository(elementRules, globalVars, ruleSources, functionLibrary);
    }

    private String getProjectRulesDir() {
        String userDir = System.getProperty("user.dir");
        if (userDir == null) {
            userDir = ".";
        }
        return userDir + "/src/main/resources/rules";
    }

    /**
     * 从classpath（jar内）加载规则数据并构建RuleRepository实例。
     *
     * <p>通过ProtectionDomain定位jar文件位置，使用JarFile枚举
     * rules/elements/和rules/commands/下的所有.json文件，
     * 以及rules/global_vars.json和rules/rule_sources.json。
     * 当ProtectionDomain不可用时（如测试环境），降级到文件系统路径加载。</p>
     *
     * @return 构建好的RuleRepository实例
     */
    public RuleRepository loadFromClasspath() {
        return loadFromClasspath(null);
    }

    /**
     * 从classpath（jar内）加载规则数据，携带函数签名库。
     *
     * @param functionLibrary 函数签名库，可为null
     * @return 构建好的RuleRepository实例
     */
    public RuleRepository loadFromClasspath(FunctionSignatureLibrary functionLibrary) {
        try {
            java.security.ProtectionDomain pd = JsonRuleLoader.class.getProtectionDomain();
            if (pd == null || pd.getCodeSource() == null || pd.getCodeSource().getLocation() == null) {
                return loadFromDirectory(getProjectRulesDir(), functionLibrary);
            }
            File location = new File(pd.getCodeSource().getLocation().toURI());

            if (location.getName().endsWith(".jar")) {
                return loadFromJar(location, functionLibrary);
            } else {
                return loadFromDirectory(location.getAbsolutePath(), functionLibrary);
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuleLoadException("Failed to load rules from classpath", e);
        }
    }

    private RuleRepository loadFromJar(File jarFile, FunctionSignatureLibrary functionLibrary) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            Map<String, DslElementRule> elementRules = new HashMap<>();

            jar.stream()
                    .filter(e -> (e.getName().startsWith("rules/elements/") || e.getName().startsWith("rules/commands/"))
                            && e.getName().endsWith(".json") && !e.isDirectory())
                    .forEach(entry -> {
                        try (InputStreamReader reader = new InputStreamReader(
                                jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                            DslElementRule rule = gson.fromJson(reader, DslElementRule.class);
                            if (rule != null && rule.getElementName() != null) {
                                normalizeElementRule(rule);
                                elementRules.put(rule.getElementName(), rule);
                            }
                        } catch (IOException e) {
                            throw new RuleLoadException("Failed to read classpath entry: " + entry.getName(), e);
                        }
                    });

            Map<String, DslGlobalVar> globalVars = loadGlobalVarsFromJar(jar);
            Map<String, RuleSource> ruleSources = loadRuleSourcesFromJar(jar);

            return buildRuleRepository(elementRules, globalVars, ruleSources, functionLibrary);
        }
    }

    private Map<String, DslGlobalVar> loadGlobalVarsFromJar(JarFile jar) {
        java.util.jar.JarEntry entry = jar.getJarEntry("rules/" + GLOBAL_VARS_FILE);
        if (entry == null) {
            return Collections.emptyMap();
        }

        try (InputStreamReader reader = new InputStreamReader(
                jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<DslGlobalVar>>() {}.getType();
            List<DslGlobalVar> vars = gson.fromJson(reader, listType);
            if (vars == null) {
                return Collections.emptyMap();
            }

            Map<String, DslGlobalVar> result = new HashMap<>();
            for (DslGlobalVar var : vars) {
                normalizeGlobalVar(var);
                result.put(var.getName(), var);
            }
            return result;
        } catch (IOException e) {
            throw new RuleLoadException("Failed to read global_vars from classpath", e);
        } catch (JsonSyntaxException e) {
            throw new RuleLoadException("JSON syntax error in global_vars from classpath", e);
        }
    }

    private Map<String, RuleSource> loadRuleSourcesFromJar(JarFile jar) {
        java.util.jar.JarEntry entry = jar.getJarEntry("rules/" + RULE_SOURCES_FILE);
        if (entry == null) {
            return Collections.emptyMap();
        }

        try (InputStreamReader reader = new InputStreamReader(
                jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<RuleSource>>() {}.getType();
            List<RuleSource> sources = gson.fromJson(reader, listType);
            if (sources == null) {
                return Collections.emptyMap();
            }

            return sources.stream()
                    .collect(Collectors.toMap(RuleSource::getRuleId, s -> s));
        } catch (IOException e) {
            throw new RuleLoadException("Failed to read rule_sources from classpath", e);
        } catch (JsonSyntaxException e) {
            throw new RuleLoadException("JSON syntax error in rule_sources from classpath", e);
        }
    }

    /**
     * 规则库加载异常，用于JSON文件读取失败或格式错误时的运行时异常包装。
     *
     * <p>不使用受检异常（遵循AGENTS.md §4.4"不抛出受检异常，使用运行时异常"），
     * CLI入口捕获后根据场景决定退出码：文件不存在→退出码2，格式错误→退出码2。</p>
     */
    public static class RuleLoadException extends RuntimeException {
        /**
         * 构造加载异常，包含描述信息和原始异常。
         *
         * @param message 异常描述，如"JSON syntax error in file: /path/to/Var.json"
         * @param cause 原始异常（IOException或JsonSyntaxException）
         */
        public RuleLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
