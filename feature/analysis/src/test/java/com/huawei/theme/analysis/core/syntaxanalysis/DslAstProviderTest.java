package com.huawei.theme.analysis.core.syntaxanalysis;

import com.huawei.theme.analysis.core.shared.ast.DslFileNode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DslAstProviderTest {

    @Test
    void dslAstProviderInterfaceExists() {
        DslAstProvider provider = new StubProvider();
        DslFileNode ast = provider.getDslAst("test.xml", "<Lockscreen/>");
        assertNotNull(ast);
    }

    private static class StubProvider implements DslAstProvider {
        @Override
        public DslFileNode getDslAst(String filePath, String content) {
            DslFileNode node = new DslFileNode();
            node.setText("");
            node.setLine(0);
            node.setColumn(0);
            return node;
        }
    }
}
