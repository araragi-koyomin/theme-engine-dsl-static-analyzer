package com.huawei.theme.analysis.core.semanticanalysis.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.huawei.theme.analysis.core.shared.ast.DslElementNode;
import com.huawei.theme.analysis.core.shared.type.DslNumberType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolTableTest {

    @Test
    void symbolTableWithDeclarationsAndReferences() {
        DslElementNode astNode = new DslElementNode();
        astNode.setTagName("Var");
        astNode.setAttributes(List.of());
        astNode.setChildElements(List.of());
        astNode.setSelfClosing(false);
        astNode.setHasError(false);
        astNode.setText("<Var>");
        astNode.setLine(5);
        astNode.setColumn(0);

        VarDeclaration decl = VarDeclaration.builder()
                .name("steps_value")
                .type(new DslNumberType())
                .expression("#steps")
                .isConstAttr(false)
                .astNode(astNode)
                .build();

        VarReference ref = VarReference.builder()
                .name("steps_value")
                .kind(ReferenceKind.NUMERIC)
                .astNode(astNode)
                .build();

        Map<String, VarDeclaration> declarations = new HashMap<>();
        declarations.put("steps_value", decl);

        SymbolTable table = SymbolTable.builder()
                .declarations(declarations)
                .references(List.of(ref))
                .build();

        assertTrue(table.getDeclarations().containsKey("steps_value"));
        assertEquals("number", table.getDeclarations().get("steps_value").getType().getName());
        assertEquals(1, table.getReferences().size());
        assertEquals(ReferenceKind.NUMERIC, table.getReferences().get(0).getKind());
    }
}
