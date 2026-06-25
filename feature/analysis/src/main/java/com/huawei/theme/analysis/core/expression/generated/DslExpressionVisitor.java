// Generated from C:/Users/30991/theme-engine-dsl-static-analyzer/feature/analysis/src/main/java/com/huawei/theme/analysis/core/expression/grammar/DslExpression.g4 by ANTLR 4.13.1
package com.huawei.theme.analysis.core.expression.generated;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link DslExpressionParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface DslExpressionVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link DslExpressionParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression(DslExpressionParser.ExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link DslExpressionParser#additiveExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAdditiveExpr(DslExpressionParser.AdditiveExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link DslExpressionParser#multiplicativeExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMultiplicativeExpr(DslExpressionParser.MultiplicativeExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link DslExpressionParser#primaryExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPrimaryExpr(DslExpressionParser.PrimaryExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link DslExpressionParser#functionCall}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionCall(DslExpressionParser.FunctionCallContext ctx);
	/**
	 * Visit a parse tree produced by {@link DslExpressionParser#variableRef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariableRef(DslExpressionParser.VariableRefContext ctx);
	/**
	 * Visit a parse tree produced by {@link DslExpressionParser#varName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarName(DslExpressionParser.VarNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link DslExpressionParser#literal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLiteral(DslExpressionParser.LiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link DslExpressionParser#exprList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExprList(DslExpressionParser.ExprListContext ctx);
}