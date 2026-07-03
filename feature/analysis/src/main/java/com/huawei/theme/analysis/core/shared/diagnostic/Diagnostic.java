package com.huawei.theme.analysis.core.shared.diagnostic;

import java.util.Collections;
import java.util.List;

import com.huawei.theme.analysis.core.shared.ast.DslAstNode;
import com.huawei.theme.analysis.core.shared.ast.SourceRange;
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
    int endLine;
    int endColumn;
    /**
     * 错误发生的位置，可以用于Idea的定位
     */
    DslAstNode astNode;

    @Builder.Default
    List<String> suggestedFixes = Collections.emptyList();
    String ruleDocUrl;

    /**
     * 返回该诊断的源码区间。start闭end开，行1-based列0-based。
     */
    public SourceRange getRange() {
        return new SourceRange(line, column, endLine, endColumn);
    }

    public static class DiagnosticBuilder {


        /**
         * 这也会自动生成line、column、endLine、endColumn
         *
         * @param node
         * @return this
         */
        public DiagnosticBuilder astNode(DslAstNode node) {
            this.line = node.getLine();
            this.column = node.getColumn();
            this.endLine = node.getEndLine();
            this.endColumn = node.getEndColumn();
            this.astNode = node;
            return this; // 返回 this 以支持链式调用
        }

        /**
         * 从AST节点设置诊断的完整区间(start+end)，但不设置astNode字段。
         * 用于仅需位置区间、不需要节点引用的场景。
         *
         * @param node
         * @return this
         */
        public DiagnosticBuilder positionFrom(DslAstNode node) {
            this.line = node.getLine();
            this.column = node.getColumn();
            this.endLine = node.getEndLine();
            this.endColumn = node.getEndColumn();
            return this;
        }
    }

}
