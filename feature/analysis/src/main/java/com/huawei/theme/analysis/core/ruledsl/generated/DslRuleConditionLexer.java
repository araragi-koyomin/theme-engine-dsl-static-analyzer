// Generated from C:/Users/30991/theme-engine-dsl-static-analyzer/feature/analysis/src/main/java/com/huawei/theme/analysis/core/ruledsl/grammar/DslRuleCondition.g4 by ANTLR 4.13.1
package com.huawei.theme.analysis.core.ruledsl.generated;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class DslRuleConditionLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, NUMBER=6, STRING=7, NULL=8, NOT=9, 
		AND=10, OR=11, IN=12, EQ=13, NEQ=14, GT=15, LT=16, GEQ=17, LEQ=18, ELEMENT_ATTRS_OPEN=19, 
		ELEMENT_TAG_NAME=20, ELEMENT_PARENT_TAG_NAME=21, WS=22;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "T__3", "T__4", "NUMBER", "STRING", "NULL", "NOT", 
			"AND", "OR", "IN", "EQ", "NEQ", "GT", "LT", "GEQ", "LEQ", "ELEMENT_ATTRS_OPEN", 
			"ELEMENT_TAG_NAME", "ELEMENT_PARENT_TAG_NAME", "WS"
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


	public DslRuleConditionLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "DslRuleCondition.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\u0004\u0000\u0016\u00af\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002"+
		"\u0001\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002"+
		"\u0004\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002"+
		"\u0007\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002"+
		"\u000b\u0007\u000b\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e"+
		"\u0002\u000f\u0007\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011"+
		"\u0002\u0012\u0007\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014"+
		"\u0002\u0015\u0007\u0015\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001"+
		"\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004"+
		"\u0001\u0005\u0004\u00059\b\u0005\u000b\u0005\f\u0005:\u0001\u0005\u0001"+
		"\u0005\u0004\u0005?\b\u0005\u000b\u0005\f\u0005@\u0003\u0005C\b\u0005"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0005\u0006I\b\u0006"+
		"\n\u0006\f\u0006L\t\u0006\u0001\u0006\u0001\u0006\u0001\u0007\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0001\u0007\u0001\b\u0001\b\u0001\b\u0001\b\u0001"+
		"\t\u0001\t\u0001\t\u0001\t\u0001\n\u0001\n\u0001\n\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001\r\u0001\u000e"+
		"\u0001\u000e\u0001\u000f\u0001\u000f\u0001\u0010\u0001\u0010\u0001\u0010"+
		"\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0012\u0001\u0012\u0001\u0012"+
		"\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012"+
		"\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012"+
		"\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013"+
		"\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013"+
		"\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0014\u0001\u0014"+
		"\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014"+
		"\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014"+
		"\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014"+
		"\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0015\u0004\u0015\u00aa\b\u0015"+
		"\u000b\u0015\f\u0015\u00ab\u0001\u0015\u0001\u0015\u0000\u0000\u0016\u0001"+
		"\u0001\u0003\u0002\u0005\u0003\u0007\u0004\t\u0005\u000b\u0006\r\u0007"+
		"\u000f\b\u0011\t\u0013\n\u0015\u000b\u0017\f\u0019\r\u001b\u000e\u001d"+
		"\u000f\u001f\u0010!\u0011#\u0012%\u0013\'\u0014)\u0015+\u0016\u0001\u0000"+
		"\u0003\u0001\u000009\u0001\u0000\'\'\u0003\u0000\t\n\r\r  \u00b4\u0000"+
		"\u0001\u0001\u0000\u0000\u0000\u0000\u0003\u0001\u0000\u0000\u0000\u0000"+
		"\u0005\u0001\u0000\u0000\u0000\u0000\u0007\u0001\u0000\u0000\u0000\u0000"+
		"\t\u0001\u0000\u0000\u0000\u0000\u000b\u0001\u0000\u0000\u0000\u0000\r"+
		"\u0001\u0000\u0000\u0000\u0000\u000f\u0001\u0000\u0000\u0000\u0000\u0011"+
		"\u0001\u0000\u0000\u0000\u0000\u0013\u0001\u0000\u0000\u0000\u0000\u0015"+
		"\u0001\u0000\u0000\u0000\u0000\u0017\u0001\u0000\u0000\u0000\u0000\u0019"+
		"\u0001\u0000\u0000\u0000\u0000\u001b\u0001\u0000\u0000\u0000\u0000\u001d"+
		"\u0001\u0000\u0000\u0000\u0000\u001f\u0001\u0000\u0000\u0000\u0000!\u0001"+
		"\u0000\u0000\u0000\u0000#\u0001\u0000\u0000\u0000\u0000%\u0001\u0000\u0000"+
		"\u0000\u0000\'\u0001\u0000\u0000\u0000\u0000)\u0001\u0000\u0000\u0000"+
		"\u0000+\u0001\u0000\u0000\u0000\u0001-\u0001\u0000\u0000\u0000\u0003/"+
		"\u0001\u0000\u0000\u0000\u00051\u0001\u0000\u0000\u0000\u00073\u0001\u0000"+
		"\u0000\u0000\t5\u0001\u0000\u0000\u0000\u000b8\u0001\u0000\u0000\u0000"+
		"\rD\u0001\u0000\u0000\u0000\u000fO\u0001\u0000\u0000\u0000\u0011T\u0001"+
		"\u0000\u0000\u0000\u0013X\u0001\u0000\u0000\u0000\u0015\\\u0001\u0000"+
		"\u0000\u0000\u0017_\u0001\u0000\u0000\u0000\u0019b\u0001\u0000\u0000\u0000"+
		"\u001be\u0001\u0000\u0000\u0000\u001dh\u0001\u0000\u0000\u0000\u001fj"+
		"\u0001\u0000\u0000\u0000!l\u0001\u0000\u0000\u0000#o\u0001\u0000\u0000"+
		"\u0000%r\u0001\u0000\u0000\u0000\'\u0081\u0001\u0000\u0000\u0000)\u0091"+
		"\u0001\u0000\u0000\u0000+\u00a9\u0001\u0000\u0000\u0000-.\u0005(\u0000"+
		"\u0000.\u0002\u0001\u0000\u0000\u0000/0\u0005)\u0000\u00000\u0004\u0001"+
		"\u0000\u0000\u000012\u0005]\u0000\u00002\u0006\u0001\u0000\u0000\u0000"+
		"34\u0005[\u0000\u00004\b\u0001\u0000\u0000\u000056\u0005,\u0000\u0000"+
		"6\n\u0001\u0000\u0000\u000079\u0007\u0000\u0000\u000087\u0001\u0000\u0000"+
		"\u00009:\u0001\u0000\u0000\u0000:8\u0001\u0000\u0000\u0000:;\u0001\u0000"+
		"\u0000\u0000;B\u0001\u0000\u0000\u0000<>\u0005.\u0000\u0000=?\u0007\u0000"+
		"\u0000\u0000>=\u0001\u0000\u0000\u0000?@\u0001\u0000\u0000\u0000@>\u0001"+
		"\u0000\u0000\u0000@A\u0001\u0000\u0000\u0000AC\u0001\u0000\u0000\u0000"+
		"B<\u0001\u0000\u0000\u0000BC\u0001\u0000\u0000\u0000C\f\u0001\u0000\u0000"+
		"\u0000DJ\u0005\'\u0000\u0000EI\b\u0001\u0000\u0000FG\u0005\\\u0000\u0000"+
		"GI\u0005\'\u0000\u0000HE\u0001\u0000\u0000\u0000HF\u0001\u0000\u0000\u0000"+
		"IL\u0001\u0000\u0000\u0000JH\u0001\u0000\u0000\u0000JK\u0001\u0000\u0000"+
		"\u0000KM\u0001\u0000\u0000\u0000LJ\u0001\u0000\u0000\u0000MN\u0005\'\u0000"+
		"\u0000N\u000e\u0001\u0000\u0000\u0000OP\u0005n\u0000\u0000PQ\u0005u\u0000"+
		"\u0000QR\u0005l\u0000\u0000RS\u0005l\u0000\u0000S\u0010\u0001\u0000\u0000"+
		"\u0000TU\u0005N\u0000\u0000UV\u0005O\u0000\u0000VW\u0005T\u0000\u0000"+
		"W\u0012\u0001\u0000\u0000\u0000XY\u0005A\u0000\u0000YZ\u0005N\u0000\u0000"+
		"Z[\u0005D\u0000\u0000[\u0014\u0001\u0000\u0000\u0000\\]\u0005O\u0000\u0000"+
		"]^\u0005R\u0000\u0000^\u0016\u0001\u0000\u0000\u0000_`\u0005I\u0000\u0000"+
		"`a\u0005N\u0000\u0000a\u0018\u0001\u0000\u0000\u0000bc\u0005=\u0000\u0000"+
		"cd\u0005=\u0000\u0000d\u001a\u0001\u0000\u0000\u0000ef\u0005!\u0000\u0000"+
		"fg\u0005=\u0000\u0000g\u001c\u0001\u0000\u0000\u0000hi\u0005>\u0000\u0000"+
		"i\u001e\u0001\u0000\u0000\u0000jk\u0005<\u0000\u0000k \u0001\u0000\u0000"+
		"\u0000lm\u0005>\u0000\u0000mn\u0005=\u0000\u0000n\"\u0001\u0000\u0000"+
		"\u0000op\u0005<\u0000\u0000pq\u0005=\u0000\u0000q$\u0001\u0000\u0000\u0000"+
		"rs\u0005e\u0000\u0000st\u0005l\u0000\u0000tu\u0005e\u0000\u0000uv\u0005"+
		"m\u0000\u0000vw\u0005e\u0000\u0000wx\u0005n\u0000\u0000xy\u0005t\u0000"+
		"\u0000yz\u0005.\u0000\u0000z{\u0005a\u0000\u0000{|\u0005t\u0000\u0000"+
		"|}\u0005t\u0000\u0000}~\u0005r\u0000\u0000~\u007f\u0005s\u0000\u0000\u007f"+
		"\u0080\u0005[\u0000\u0000\u0080&\u0001\u0000\u0000\u0000\u0081\u0082\u0005"+
		"e\u0000\u0000\u0082\u0083\u0005l\u0000\u0000\u0083\u0084\u0005e\u0000"+
		"\u0000\u0084\u0085\u0005m\u0000\u0000\u0085\u0086\u0005e\u0000\u0000\u0086"+
		"\u0087\u0005n\u0000\u0000\u0087\u0088\u0005t\u0000\u0000\u0088\u0089\u0005"+
		".\u0000\u0000\u0089\u008a\u0005t\u0000\u0000\u008a\u008b\u0005a\u0000"+
		"\u0000\u008b\u008c\u0005g\u0000\u0000\u008c\u008d\u0005N\u0000\u0000\u008d"+
		"\u008e\u0005a\u0000\u0000\u008e\u008f\u0005m\u0000\u0000\u008f\u0090\u0005"+
		"e\u0000\u0000\u0090(\u0001\u0000\u0000\u0000\u0091\u0092\u0005e\u0000"+
		"\u0000\u0092\u0093\u0005l\u0000\u0000\u0093\u0094\u0005e\u0000\u0000\u0094"+
		"\u0095\u0005m\u0000\u0000\u0095\u0096\u0005e\u0000\u0000\u0096\u0097\u0005"+
		"n\u0000\u0000\u0097\u0098\u0005t\u0000\u0000\u0098\u0099\u0005.\u0000"+
		"\u0000\u0099\u009a\u0005p\u0000\u0000\u009a\u009b\u0005a\u0000\u0000\u009b"+
		"\u009c\u0005r\u0000\u0000\u009c\u009d\u0005e\u0000\u0000\u009d\u009e\u0005"+
		"n\u0000\u0000\u009e\u009f\u0005t\u0000\u0000\u009f\u00a0\u0005.\u0000"+
		"\u0000\u00a0\u00a1\u0005t\u0000\u0000\u00a1\u00a2\u0005a\u0000\u0000\u00a2"+
		"\u00a3\u0005g\u0000\u0000\u00a3\u00a4\u0005N\u0000\u0000\u00a4\u00a5\u0005"+
		"a\u0000\u0000\u00a5\u00a6\u0005m\u0000\u0000\u00a6\u00a7\u0005e\u0000"+
		"\u0000\u00a7*\u0001\u0000\u0000\u0000\u00a8\u00aa\u0007\u0002\u0000\u0000"+
		"\u00a9\u00a8\u0001\u0000\u0000\u0000\u00aa\u00ab\u0001\u0000\u0000\u0000"+
		"\u00ab\u00a9\u0001\u0000\u0000\u0000\u00ab\u00ac\u0001\u0000\u0000\u0000"+
		"\u00ac\u00ad\u0001\u0000\u0000\u0000\u00ad\u00ae\u0006\u0015\u0000\u0000"+
		"\u00ae,\u0001\u0000\u0000\u0000\u0007\u0000:@BHJ\u00ab\u0001\u0006\u0000"+
		"\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}