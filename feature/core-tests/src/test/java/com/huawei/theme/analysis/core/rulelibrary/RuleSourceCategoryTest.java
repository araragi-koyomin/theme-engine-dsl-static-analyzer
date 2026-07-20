package com.huawei.theme.analysis.core.rulelibrary;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.theme.analysis.core.rulelibrary.model.RuleSource;

/**
 * 验证 rule_sources.json 中规则来源条目的 category 字段分类正确。
 *
 * <p>SPEC-2: SYN-EXPR-001/002/003/004/005/006/ANTLR 属于语法类(SYN)，
 * 历史数据误标为 SEM，需修正为 SYN。其余 SEM-/SYN- 前缀条目分类不变。</p>
 */
class RuleSourceCategoryTest {

    private static RuleRepository ruleRepo;

    @BeforeAll
    static void setup() {
        ruleRepo = new JsonRuleLoader().loadFromClasspath(null);
    }

    @Test
    void synExpr001CategoryIsSyn() {
        RuleSource src = ruleRepo.getRuleSource("SYN-EXPR-001").orElseThrow();
        assertEquals("SYN", src.getCategory());
    }

    @Test
    void synExpr002CategoryIsSyn() {
        RuleSource src = ruleRepo.getRuleSource("SYN-EXPR-002").orElseThrow();
        assertEquals("SYN", src.getCategory());
    }

    @Test
    void synExpr003CategoryIsSyn() {
        RuleSource src = ruleRepo.getRuleSource("SYN-EXPR-003").orElseThrow();
        assertEquals("SYN", src.getCategory());
    }

    @Test
    void synExpr004CategoryIsSyn() {
        RuleSource src = ruleRepo.getRuleSource("SYN-EXPR-004").orElseThrow();
        assertEquals("SYN", src.getCategory());
    }

    @Test
    void synExpr005CategoryIsSyn() {
        RuleSource src = ruleRepo.getRuleSource("SYN-EXPR-005").orElseThrow();
        assertEquals("SYN", src.getCategory());
    }

    @Test
    void synExpr006CategoryIsSyn() {
        RuleSource src = ruleRepo.getRuleSource("SYN-EXPR-006").orElseThrow();
        assertEquals("SYN", src.getCategory());
    }

    @Test
    void synExprAntlrCategoryIsSyn() {
        RuleSource src = ruleRepo.getRuleSource("SYN-EXPR-ANTLR").orElseThrow();
        assertEquals("SYN", src.getCategory());
    }

    @Test
    void semType001CategoryRemainsSem() {
        RuleSource src = ruleRepo.getRuleSource("SEM-TYPE-001").orElseThrow();
        assertEquals("SEM", src.getCategory());
    }

    @Test
    void syn001CategoryRemainsSyn() {
        RuleSource src = ruleRepo.getRuleSource("SYN-001").orElseThrow();
        assertEquals("SYN", src.getCategory());
        assertTrue(ruleRepo.getRuleSource("SYN-001").isPresent());
    }
}
