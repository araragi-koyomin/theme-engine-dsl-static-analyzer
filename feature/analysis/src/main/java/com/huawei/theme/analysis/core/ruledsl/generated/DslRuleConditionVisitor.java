// Generated from C:/Users/30991/theme-engine-dsl-static-analyzer/feature/analysis/src/main/java/com/huawei/theme/analysis/core/ruledsl/grammar/DslRuleCondition.g4 by ANTLR 4.13.1
package com.huawei.theme.analysis.core.ruledsl.generated;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link DslRuleConditionParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface DslRuleConditionVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link DslRuleConditionParser#condition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondition(DslRuleConditionParser.ConditionContext ctx);
	/**
	 * Visit a parse tree produced by {@link DslRuleConditionParser#logicExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogicExpr(DslRuleConditionParser.LogicExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link DslRuleConditionParser#compareExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompareExpr(DslRuleConditionParser.CompareExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link DslRuleConditionParser#valueExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValueExpr(DslRuleConditionParser.ValueExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link DslRuleConditionParser#elementAttr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitElementAttr(DslRuleConditionParser.ElementAttrContext ctx);
	/**
	 * Visit a parse tree produced by {@link DslRuleConditionParser#setLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSetLiteral(DslRuleConditionParser.SetLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link DslRuleConditionParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLiteral(DslRuleConditionParser.LiteralContext ctx);
}