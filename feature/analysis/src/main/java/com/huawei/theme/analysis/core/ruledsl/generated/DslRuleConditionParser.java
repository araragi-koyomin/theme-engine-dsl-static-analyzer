// Generated from C:/Users/30991/theme-engine-dsl-static-analyzer/feature/analysis/src/main/java/com/huawei/theme/analysis/core/ruledsl/grammar/DslRuleCondition.g4 by ANTLR 4.13.1
package com.huawei.theme.analysis.core.ruledsl.generated;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class DslRuleConditionParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, NUMBER=6, STRING=7, NULL=8, NOT=9, 
		AND=10, OR=11, IN=12, EQ=13, NEQ=14, GT=15, LT=16, GEQ=17, LEQ=18, ELEMENT_ATTRS_OPEN=19, 
		ELEMENT_TAG_NAME=20, ELEMENT_PARENT_TAG_NAME=21, WS=22;
	public static final int
		RULE_condition = 0, RULE_logicExpr = 1, RULE_compareExpr = 2, RULE_valueExpr = 3, 
		RULE_elementAttr = 4, RULE_setLiteral = 5, RULE_literal = 6;
	private static String[] makeRuleNames() {
		return new String[] {
			"condition", "logicExpr", "compareExpr", "valueExpr", "elementAttr", 
			"setLiteral", "literal"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'('", "')'", "']'", "'['", "','", null, null, "'null'", "'NOT'", 
			"'AND'", "'OR'", "'IN'", "'=='", "'!='", "'>'", "'<'", "'>='", "'<='", 
			"'element.attrs['", "'element.tagName'", "'element.parent.tagName'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, "NUMBER", "STRING", "NULL", "NOT", 
			"AND", "OR", "IN", "EQ", "NEQ", "GT", "LT", "GEQ", "LEQ", "ELEMENT_ATTRS_OPEN", 
			"ELEMENT_TAG_NAME", "ELEMENT_PARENT_TAG_NAME", "WS"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "DslRuleCondition.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public DslRuleConditionParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ConditionContext extends ParserRuleContext {
		public LogicExprContext logicExpr() {
			return getRuleContext(LogicExprContext.class,0);
		}
		public ConditionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_condition; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DslRuleConditionVisitor ) return ((DslRuleConditionVisitor<? extends T>)visitor).visitCondition(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ConditionContext condition() throws RecognitionException {
		ConditionContext _localctx = new ConditionContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_condition);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(14);
			logicExpr(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LogicExprContext extends ParserRuleContext {
		public List<LogicExprContext> logicExpr() {
			return getRuleContexts(LogicExprContext.class);
		}
		public LogicExprContext logicExpr(int i) {
			return getRuleContext(LogicExprContext.class,i);
		}
		public TerminalNode NOT() { return getToken(DslRuleConditionParser.NOT, 0); }
		public CompareExprContext compareExpr() {
			return getRuleContext(CompareExprContext.class,0);
		}
		public TerminalNode AND() { return getToken(DslRuleConditionParser.AND, 0); }
		public TerminalNode OR() { return getToken(DslRuleConditionParser.OR, 0); }
		public LogicExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_logicExpr; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DslRuleConditionVisitor ) return ((DslRuleConditionVisitor<? extends T>)visitor).visitLogicExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LogicExprContext logicExpr() throws RecognitionException {
		return logicExpr(0);
	}

	private LogicExprContext logicExpr(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		LogicExprContext _localctx = new LogicExprContext(_ctx, _parentState);
		LogicExprContext _prevctx = _localctx;
		int _startState = 2;
		enterRecursionRule(_localctx, 2, RULE_logicExpr, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(24);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__0:
				{
				setState(17);
				match(T__0);
				setState(18);
				logicExpr(0);
				setState(19);
				match(T__1);
				}
				break;
			case NOT:
				{
				setState(21);
				match(NOT);
				setState(22);
				logicExpr(2);
				}
				break;
			case NUMBER:
			case STRING:
			case NULL:
			case ELEMENT_ATTRS_OPEN:
			case ELEMENT_TAG_NAME:
			case ELEMENT_PARENT_TAG_NAME:
				{
				setState(23);
				compareExpr();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(34);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,2,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(32);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
					case 1:
						{
						_localctx = new LogicExprContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_logicExpr);
						setState(26);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(27);
						match(AND);
						setState(28);
						logicExpr(5);
						}
						break;
					case 2:
						{
						_localctx = new LogicExprContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_logicExpr);
						setState(29);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(30);
						match(OR);
						setState(31);
						logicExpr(4);
						}
						break;
					}
					} 
				}
				setState(36);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,2,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class CompareExprContext extends ParserRuleContext {
		public List<ValueExprContext> valueExpr() {
			return getRuleContexts(ValueExprContext.class);
		}
		public ValueExprContext valueExpr(int i) {
			return getRuleContext(ValueExprContext.class,i);
		}
		public TerminalNode EQ() { return getToken(DslRuleConditionParser.EQ, 0); }
		public TerminalNode NEQ() { return getToken(DslRuleConditionParser.NEQ, 0); }
		public TerminalNode GT() { return getToken(DslRuleConditionParser.GT, 0); }
		public TerminalNode LT() { return getToken(DslRuleConditionParser.LT, 0); }
		public TerminalNode GEQ() { return getToken(DslRuleConditionParser.GEQ, 0); }
		public TerminalNode LEQ() { return getToken(DslRuleConditionParser.LEQ, 0); }
		public TerminalNode IN() { return getToken(DslRuleConditionParser.IN, 0); }
		public SetLiteralContext setLiteral() {
			return getRuleContext(SetLiteralContext.class,0);
		}
		public TerminalNode NOT() { return getToken(DslRuleConditionParser.NOT, 0); }
		public CompareExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_compareExpr; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DslRuleConditionVisitor ) return ((DslRuleConditionVisitor<? extends T>)visitor).visitCompareExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CompareExprContext compareExpr() throws RecognitionException {
		CompareExprContext _localctx = new CompareExprContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_compareExpr);
		try {
			setState(70);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(37);
				valueExpr();
				setState(38);
				match(EQ);
				setState(39);
				valueExpr();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(41);
				valueExpr();
				setState(42);
				match(NEQ);
				setState(43);
				valueExpr();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(45);
				valueExpr();
				setState(46);
				match(GT);
				setState(47);
				valueExpr();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(49);
				valueExpr();
				setState(50);
				match(LT);
				setState(51);
				valueExpr();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(53);
				valueExpr();
				setState(54);
				match(GEQ);
				setState(55);
				valueExpr();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(57);
				valueExpr();
				setState(58);
				match(LEQ);
				setState(59);
				valueExpr();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(61);
				valueExpr();
				setState(62);
				match(IN);
				setState(63);
				setLiteral();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(65);
				valueExpr();
				setState(66);
				match(NOT);
				setState(67);
				match(IN);
				setState(68);
				setLiteral();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ValueExprContext extends ParserRuleContext {
		public ElementAttrContext elementAttr() {
			return getRuleContext(ElementAttrContext.class,0);
		}
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public TerminalNode NULL() { return getToken(DslRuleConditionParser.NULL, 0); }
		public ValueExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_valueExpr; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DslRuleConditionVisitor ) return ((DslRuleConditionVisitor<? extends T>)visitor).visitValueExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ValueExprContext valueExpr() throws RecognitionException {
		ValueExprContext _localctx = new ValueExprContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_valueExpr);
		try {
			setState(75);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ELEMENT_ATTRS_OPEN:
			case ELEMENT_TAG_NAME:
			case ELEMENT_PARENT_TAG_NAME:
				enterOuterAlt(_localctx, 1);
				{
				setState(72);
				elementAttr();
				}
				break;
			case NUMBER:
			case STRING:
				enterOuterAlt(_localctx, 2);
				{
				setState(73);
				literal();
				}
				break;
			case NULL:
				enterOuterAlt(_localctx, 3);
				{
				setState(74);
				match(NULL);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ElementAttrContext extends ParserRuleContext {
		public TerminalNode ELEMENT_ATTRS_OPEN() { return getToken(DslRuleConditionParser.ELEMENT_ATTRS_OPEN, 0); }
		public TerminalNode STRING() { return getToken(DslRuleConditionParser.STRING, 0); }
		public TerminalNode ELEMENT_TAG_NAME() { return getToken(DslRuleConditionParser.ELEMENT_TAG_NAME, 0); }
		public TerminalNode ELEMENT_PARENT_TAG_NAME() { return getToken(DslRuleConditionParser.ELEMENT_PARENT_TAG_NAME, 0); }
		public ElementAttrContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_elementAttr; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DslRuleConditionVisitor ) return ((DslRuleConditionVisitor<? extends T>)visitor).visitElementAttr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ElementAttrContext elementAttr() throws RecognitionException {
		ElementAttrContext _localctx = new ElementAttrContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_elementAttr);
		try {
			setState(82);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ELEMENT_ATTRS_OPEN:
				enterOuterAlt(_localctx, 1);
				{
				setState(77);
				match(ELEMENT_ATTRS_OPEN);
				setState(78);
				match(STRING);
				setState(79);
				match(T__2);
				}
				break;
			case ELEMENT_TAG_NAME:
				enterOuterAlt(_localctx, 2);
				{
				setState(80);
				match(ELEMENT_TAG_NAME);
				}
				break;
			case ELEMENT_PARENT_TAG_NAME:
				enterOuterAlt(_localctx, 3);
				{
				setState(81);
				match(ELEMENT_PARENT_TAG_NAME);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SetLiteralContext extends ParserRuleContext {
		public List<LiteralContext> literal() {
			return getRuleContexts(LiteralContext.class);
		}
		public LiteralContext literal(int i) {
			return getRuleContext(LiteralContext.class,i);
		}
		public SetLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_setLiteral; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DslRuleConditionVisitor ) return ((DslRuleConditionVisitor<? extends T>)visitor).visitSetLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SetLiteralContext setLiteral() throws RecognitionException {
		SetLiteralContext _localctx = new SetLiteralContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_setLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(84);
			match(T__3);
			setState(85);
			literal();
			setState(90);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__4) {
				{
				{
				setState(86);
				match(T__4);
				setState(87);
				literal();
				}
				}
				setState(92);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(93);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LiteralContext extends ParserRuleContext {
		public TerminalNode NUMBER() { return getToken(DslRuleConditionParser.NUMBER, 0); }
		public TerminalNode STRING() { return getToken(DslRuleConditionParser.STRING, 0); }
		public LiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literal; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof DslRuleConditionVisitor ) return ((DslRuleConditionVisitor<? extends T>)visitor).visitLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LiteralContext literal() throws RecognitionException {
		LiteralContext _localctx = new LiteralContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_literal);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(95);
			_la = _input.LA(1);
			if ( !(_la==NUMBER || _la==STRING) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 1:
			return logicExpr_sempred((LogicExprContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean logicExpr_sempred(LogicExprContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 4);
		case 1:
			return precpred(_ctx, 3);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001\u0016b\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0001\u0000\u0001\u0000\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0003\u0001\u0019\b\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0005\u0001!\b\u0001\n\u0001"+
		"\f\u0001$\t\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0003\u0002G\b"+
		"\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0003\u0003L\b\u0003\u0001"+
		"\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0003\u0004S\b"+
		"\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0005\u0005Y\b"+
		"\u0005\n\u0005\f\u0005\\\t\u0005\u0001\u0005\u0001\u0005\u0001\u0006\u0001"+
		"\u0006\u0001\u0006\u0000\u0001\u0002\u0007\u0000\u0002\u0004\u0006\b\n"+
		"\f\u0000\u0001\u0001\u0000\u0006\u0007j\u0000\u000e\u0001\u0000\u0000"+
		"\u0000\u0002\u0018\u0001\u0000\u0000\u0000\u0004F\u0001\u0000\u0000\u0000"+
		"\u0006K\u0001\u0000\u0000\u0000\bR\u0001\u0000\u0000\u0000\nT\u0001\u0000"+
		"\u0000\u0000\f_\u0001\u0000\u0000\u0000\u000e\u000f\u0003\u0002\u0001"+
		"\u0000\u000f\u0001\u0001\u0000\u0000\u0000\u0010\u0011\u0006\u0001\uffff"+
		"\uffff\u0000\u0011\u0012\u0005\u0001\u0000\u0000\u0012\u0013\u0003\u0002"+
		"\u0001\u0000\u0013\u0014\u0005\u0002\u0000\u0000\u0014\u0019\u0001\u0000"+
		"\u0000\u0000\u0015\u0016\u0005\t\u0000\u0000\u0016\u0019\u0003\u0002\u0001"+
		"\u0002\u0017\u0019\u0003\u0004\u0002\u0000\u0018\u0010\u0001\u0000\u0000"+
		"\u0000\u0018\u0015\u0001\u0000\u0000\u0000\u0018\u0017\u0001\u0000\u0000"+
		"\u0000\u0019\"\u0001\u0000\u0000\u0000\u001a\u001b\n\u0004\u0000\u0000"+
		"\u001b\u001c\u0005\n\u0000\u0000\u001c!\u0003\u0002\u0001\u0005\u001d"+
		"\u001e\n\u0003\u0000\u0000\u001e\u001f\u0005\u000b\u0000\u0000\u001f!"+
		"\u0003\u0002\u0001\u0004 \u001a\u0001\u0000\u0000\u0000 \u001d\u0001\u0000"+
		"\u0000\u0000!$\u0001\u0000\u0000\u0000\" \u0001\u0000\u0000\u0000\"#\u0001"+
		"\u0000\u0000\u0000#\u0003\u0001\u0000\u0000\u0000$\"\u0001\u0000\u0000"+
		"\u0000%&\u0003\u0006\u0003\u0000&\'\u0005\r\u0000\u0000\'(\u0003\u0006"+
		"\u0003\u0000(G\u0001\u0000\u0000\u0000)*\u0003\u0006\u0003\u0000*+\u0005"+
		"\u000e\u0000\u0000+,\u0003\u0006\u0003\u0000,G\u0001\u0000\u0000\u0000"+
		"-.\u0003\u0006\u0003\u0000./\u0005\u000f\u0000\u0000/0\u0003\u0006\u0003"+
		"\u00000G\u0001\u0000\u0000\u000012\u0003\u0006\u0003\u000023\u0005\u0010"+
		"\u0000\u000034\u0003\u0006\u0003\u00004G\u0001\u0000\u0000\u000056\u0003"+
		"\u0006\u0003\u000067\u0005\u0011\u0000\u000078\u0003\u0006\u0003\u0000"+
		"8G\u0001\u0000\u0000\u00009:\u0003\u0006\u0003\u0000:;\u0005\u0012\u0000"+
		"\u0000;<\u0003\u0006\u0003\u0000<G\u0001\u0000\u0000\u0000=>\u0003\u0006"+
		"\u0003\u0000>?\u0005\f\u0000\u0000?@\u0003\n\u0005\u0000@G\u0001\u0000"+
		"\u0000\u0000AB\u0003\u0006\u0003\u0000BC\u0005\t\u0000\u0000CD\u0005\f"+
		"\u0000\u0000DE\u0003\n\u0005\u0000EG\u0001\u0000\u0000\u0000F%\u0001\u0000"+
		"\u0000\u0000F)\u0001\u0000\u0000\u0000F-\u0001\u0000\u0000\u0000F1\u0001"+
		"\u0000\u0000\u0000F5\u0001\u0000\u0000\u0000F9\u0001\u0000\u0000\u0000"+
		"F=\u0001\u0000\u0000\u0000FA\u0001\u0000\u0000\u0000G\u0005\u0001\u0000"+
		"\u0000\u0000HL\u0003\b\u0004\u0000IL\u0003\f\u0006\u0000JL\u0005\b\u0000"+
		"\u0000KH\u0001\u0000\u0000\u0000KI\u0001\u0000\u0000\u0000KJ\u0001\u0000"+
		"\u0000\u0000L\u0007\u0001\u0000\u0000\u0000MN\u0005\u0013\u0000\u0000"+
		"NO\u0005\u0007\u0000\u0000OS\u0005\u0003\u0000\u0000PS\u0005\u0014\u0000"+
		"\u0000QS\u0005\u0015\u0000\u0000RM\u0001\u0000\u0000\u0000RP\u0001\u0000"+
		"\u0000\u0000RQ\u0001\u0000\u0000\u0000S\t\u0001\u0000\u0000\u0000TU\u0005"+
		"\u0004\u0000\u0000UZ\u0003\f\u0006\u0000VW\u0005\u0005\u0000\u0000WY\u0003"+
		"\f\u0006\u0000XV\u0001\u0000\u0000\u0000Y\\\u0001\u0000\u0000\u0000ZX"+
		"\u0001\u0000\u0000\u0000Z[\u0001\u0000\u0000\u0000[]\u0001\u0000\u0000"+
		"\u0000\\Z\u0001\u0000\u0000\u0000]^\u0005\u0003\u0000\u0000^\u000b\u0001"+
		"\u0000\u0000\u0000_`\u0007\u0000\u0000\u0000`\r\u0001\u0000\u0000\u0000"+
		"\u0007\u0018 \"FKRZ";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}