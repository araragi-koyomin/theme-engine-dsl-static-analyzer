package com.huawei.theme.analysis.core.semanticanalysis.analyzers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.huawei.theme.analysis.core.rulelibrary.RuleRepository;
import com.huawei.theme.analysis.core.rulelibrary.model.DslElementRule;
import com.huawei.theme.analysis.core.semanticanalysis.model.DslContext;
import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.ast.DslFileNode;
import com.huawei.theme.analysis.core.shared.diagnostic.Diagnostic;
import com.huawei.theme.analysis.core.shared.diagnostic.DiagnosticSeverity;

/**
 * 应用位置（作用域）分析器，规则SEM-SCOPE-001。
 *
 * <p>从DSL文件根元素标签确定当前应用位置，查询M2规则库中该元素的scope矩阵，
 * 若不支持当前位置则报告错误。scope为空表示元素无应用位置限制，跳过检查。</p>
 */
public class ScopeAnalyzer extends BaseXmlAnalyzer {

    private static final String RULE_ID = "SEM-SCOPE-001";

    public ScopeAnalyzer() {
        super(RULE_ID, DiagnosticSeverity.ERROR);
    }

    @Override
    protected List<Diagnostic> doAnalyze(DslElementNode elementNode, DslContext context) {
        RuleRepository ruleRepo = context.getRuleRepository();
        String tagName = elementNode.getTagName();
        Optional<DslElementRule> ruleOpt = ruleRepo.getElementRule(tagName);
        if (ruleOpt.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Boolean> scope = ruleOpt.get().getScope();
        if (scope.isEmpty()) {
            return Collections.emptyList();
        }

        DslFileNode rootNode = context.getRootNode();
        if (rootNode == null || rootNode.getRootElement() == null) {
            return Collections.emptyList();
        }
        String currentScope = rootNode.getRootElement().getTagName();
        if (Boolean.TRUE.equals(scope.get(currentScope))) {
            return Collections.emptyList();
        }

        String message = "元素不支持当前应用位置：'" + tagName + "'不允许在'" + currentScope + "'中使用";
        return List.of(createDiagnostic(context, elementNode, message));
    }
}
