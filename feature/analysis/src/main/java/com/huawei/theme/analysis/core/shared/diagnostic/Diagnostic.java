package com.huawei.theme.analysis.core.shared.diagnostic;

import java.util.Collections;
import java.util.List;

import com.huawei.theme.analysis.core.rulelibrary.model.SuggestedFix;
import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Diagnostic {
    DiagnosticSeverity severity;
    String ruleId;
    String message;
    String filePath;
    int line;
    int column;
    /**
     * 错误发生的位置，可以用于Idea的定位
     */
    DslAstNode astNode;

    @Builder.Default
    List<SuggestedFix> suggestedFixes = Collections.emptyList();
    String ruleDocUrl;

    public static class DiagnosticBuilder {


        /**
         * 这也会自动生成line、column
         *
         * @param node
         * @return this
         */
        public DiagnosticBuilder astNode(DslAstNode node) {
            this.line = node.getLine();
            this.column = node.getColumn();
            this.astNode = node;
            return this; // 返回 this 以支持链式调用
        }
    }

}
