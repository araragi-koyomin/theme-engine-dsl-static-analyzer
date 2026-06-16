package com.huawei.theme.analysis;

import java.util.Map;

import com.huawei.theme.analysis.file.DslFileIdentifier;
import com.huawei.theme.analysis.file.DslFileMatcher;
import com.huawei.theme.analysis.rule.loader.JsonRuleLoader;
import com.huawei.theme.analysis.rule.model.DslElementRule;
import com.huawei.theme.analysis.rule.model.RuleSource;
import com.huawei.theme.analysis.rule.repository.RuleRepository;
import com.huawei.theme.analysis.rule.repository.RuleRepositoryImpl;

public class DslAnalysisService {
    private static final String RULES_RESOURCE_PATH = "rules/dsl_rules.json";
    private final RuleRepository ruleRepository;
    private final DslFileMatcher fileMatcher;

    public DslAnalysisService() {
        // M2 Core: 加载规则资源并构建规则仓库
        JsonRuleLoader loader = new JsonRuleLoader();
        Map<String, DslElementRule> elementMap = loader.buildElementRuleMap(RULES_RESOURCE_PATH);
        Map<String, RuleSource> sourceMap = loader.buildRuleSourceMap(RULES_RESOURCE_PATH);
        this.ruleRepository = new RuleRepositoryImpl(elementMap, sourceMap);
        // M1 Core: 构建 DSL 文件识别器，依赖规则仓库提供的根元素名列表
        this.fileMatcher = new DslFileIdentifier(ruleRepository);
    }

    public RuleRepository getRuleRepository() {
        return ruleRepository;
    }

    public DslFileMatcher getFileMatcher() {
        return fileMatcher;
    }
}
