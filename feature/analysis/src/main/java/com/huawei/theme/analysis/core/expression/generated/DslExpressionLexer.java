// Generated from C:/Users/30991/theme-engine-dsl-static-analyzer/feature/analysis/src/main/java/com/huawei/theme/analysis/core/expression/grammar/DslExpression.g4 by ANTLR 4.13.1
package com.huawei.theme.analysis.core.expression.generated;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class DslExpressionLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, NUMBER=13, STRING=14, ID=15, VAR_ID=16, WS=17;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
			"T__9", "T__10", "T__11", "NUMBER", "STRING", "ID", "VAR_ID", "WS"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'+'", "'-'", "'*'", "'/'", "'%'", "'('", "')'", "'#'", "'['", 
			"']'", "'@'", "','"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, "NUMBER", "STRING", "ID", "VAR_ID", "WS"
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


	public DslExpressionLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "DslExpression.g4"; }

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
		"\u0004\u0000\u0011h\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002\u0001"+
		"\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004"+
		"\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007"+
		"\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b"+
		"\u0007\u000b\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002"+
		"\u000f\u0007\u000f\u0002\u0010\u0007\u0010\u0001\u0000\u0001\u0000\u0001"+
		"\u0001\u0001\u0001\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001"+
		"\u0004\u0001\u0004\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001"+
		"\u0007\u0001\u0007\u0001\b\u0001\b\u0001\t\u0001\t\u0001\n\u0001\n\u0001"+
		"\u000b\u0001\u000b\u0001\f\u0004\f=\b\f\u000b\f\f\f>\u0001\f\u0001\f\u0004"+
		"\fC\b\f\u000b\f\f\fD\u0003\fG\b\f\u0001\r\u0001\r\u0001\r\u0001\r\u0005"+
		"\rM\b\r\n\r\f\rP\t\r\u0001\r\u0001\r\u0001\u000e\u0001\u000e\u0005\u000e"+
		"V\b\u000e\n\u000e\f\u000eY\t\u000e\u0001\u000f\u0001\u000f\u0005\u000f"+
		"]\b\u000f\n\u000f\f\u000f`\t\u000f\u0001\u0010\u0004\u0010c\b\u0010\u000b"+
		"\u0010\f\u0010d\u0001\u0010\u0001\u0010\u0000\u0000\u0011\u0001\u0001"+
		"\u0003\u0002\u0005\u0003\u0007\u0004\t\u0005\u000b\u0006\r\u0007\u000f"+
		"\b\u0011\t\u0013\n\u0015\u000b\u0017\f\u0019\r\u001b\u000e\u001d\u000f"+
		"\u001f\u0010!\u0011\u0001\u0000\u0006\u0001\u000009\u0001\u0000\'\'\u0003"+
		"\u0000AZ__az\u0004\u000009AZ__az\u0005\u0000..09AZ__az\u0003\u0000\t\n"+
		"\r\r  o\u0000\u0001\u0001\u0000\u0000\u0000\u0000\u0003\u0001\u0000\u0000"+
		"\u0000\u0000\u0005\u0001\u0000\u0000\u0000\u0000\u0007\u0001\u0000\u0000"+
		"\u0000\u0000\t\u0001\u0000\u0000\u0000\u0000\u000b\u0001\u0000\u0000\u0000"+
		"\u0000\r\u0001\u0000\u0000\u0000\u0000\u000f\u0001\u0000\u0000\u0000\u0000"+
		"\u0011\u0001\u0000\u0000\u0000\u0000\u0013\u0001\u0000\u0000\u0000\u0000"+
		"\u0015\u0001\u0000\u0000\u0000\u0000\u0017\u0001\u0000\u0000\u0000\u0000"+
		"\u0019\u0001\u0000\u0000\u0000\u0000\u001b\u0001\u0000\u0000\u0000\u0000"+
		"\u001d\u0001\u0000\u0000\u0000\u0000\u001f\u0001\u0000\u0000\u0000\u0000"+
		"!\u0001\u0000\u0000\u0000\u0001#\u0001\u0000\u0000\u0000\u0003%\u0001"+
		"\u0000\u0000\u0000\u0005\'\u0001\u0000\u0000\u0000\u0007)\u0001\u0000"+
		"\u0000\u0000\t+\u0001\u0000\u0000\u0000\u000b-\u0001\u0000\u0000\u0000"+
		"\r/\u0001\u0000\u0000\u0000\u000f1\u0001\u0000\u0000\u0000\u00113\u0001"+
		"\u0000\u0000\u0000\u00135\u0001\u0000\u0000\u0000\u00157\u0001\u0000\u0000"+
		"\u0000\u00179\u0001\u0000\u0000\u0000\u0019<\u0001\u0000\u0000\u0000\u001b"+
		"H\u0001\u0000\u0000\u0000\u001dS\u0001\u0000\u0000\u0000\u001fZ\u0001"+
		"\u0000\u0000\u0000!b\u0001\u0000\u0000\u0000#$\u0005+\u0000\u0000$\u0002"+
		"\u0001\u0000\u0000\u0000%&\u0005-\u0000\u0000&\u0004\u0001\u0000\u0000"+
		"\u0000\'(\u0005*\u0000\u0000(\u0006\u0001\u0000\u0000\u0000)*\u0005/\u0000"+
		"\u0000*\b\u0001\u0000\u0000\u0000+,\u0005%\u0000\u0000,\n\u0001\u0000"+
		"\u0000\u0000-.\u0005(\u0000\u0000.\f\u0001\u0000\u0000\u0000/0\u0005)"+
		"\u0000\u00000\u000e\u0001\u0000\u0000\u000012\u0005#\u0000\u00002\u0010"+
		"\u0001\u0000\u0000\u000034\u0005[\u0000\u00004\u0012\u0001\u0000\u0000"+
		"\u000056\u0005]\u0000\u00006\u0014\u0001\u0000\u0000\u000078\u0005@\u0000"+
		"\u00008\u0016\u0001\u0000\u0000\u00009:\u0005,\u0000\u0000:\u0018\u0001"+
		"\u0000\u0000\u0000;=\u0007\u0000\u0000\u0000<;\u0001\u0000\u0000\u0000"+
		"=>\u0001\u0000\u0000\u0000><\u0001\u0000\u0000\u0000>?\u0001\u0000\u0000"+
		"\u0000?F\u0001\u0000\u0000\u0000@B\u0005.\u0000\u0000AC\u0007\u0000\u0000"+
		"\u0000BA\u0001\u0000\u0000\u0000CD\u0001\u0000\u0000\u0000DB\u0001\u0000"+
		"\u0000\u0000DE\u0001\u0000\u0000\u0000EG\u0001\u0000\u0000\u0000F@\u0001"+
		"\u0000\u0000\u0000FG\u0001\u0000\u0000\u0000G\u001a\u0001\u0000\u0000"+
		"\u0000HN\u0005\'\u0000\u0000IM\b\u0001\u0000\u0000JK\u0005\\\u0000\u0000"+
		"KM\u0005\'\u0000\u0000LI\u0001\u0000\u0000\u0000LJ\u0001\u0000\u0000\u0000"+
		"MP\u0001\u0000\u0000\u0000NL\u0001\u0000\u0000\u0000NO\u0001\u0000\u0000"+
		"\u0000OQ\u0001\u0000\u0000\u0000PN\u0001\u0000\u0000\u0000QR\u0005\'\u0000"+
		"\u0000R\u001c\u0001\u0000\u0000\u0000SW\u0007\u0002\u0000\u0000TV\u0007"+
		"\u0003\u0000\u0000UT\u0001\u0000\u0000\u0000VY\u0001\u0000\u0000\u0000"+
		"WU\u0001\u0000\u0000\u0000WX\u0001\u0000\u0000\u0000X\u001e\u0001\u0000"+
		"\u0000\u0000YW\u0001\u0000\u0000\u0000Z^\u0007\u0002\u0000\u0000[]\u0007"+
		"\u0004\u0000\u0000\\[\u0001\u0000\u0000\u0000]`\u0001\u0000\u0000\u0000"+
		"^\\\u0001\u0000\u0000\u0000^_\u0001\u0000\u0000\u0000_ \u0001\u0000\u0000"+
		"\u0000`^\u0001\u0000\u0000\u0000ac\u0007\u0005\u0000\u0000ba\u0001\u0000"+
		"\u0000\u0000cd\u0001\u0000\u0000\u0000db\u0001\u0000\u0000\u0000de\u0001"+
		"\u0000\u0000\u0000ef\u0001\u0000\u0000\u0000fg\u0006\u0010\u0000\u0000"+
		"g\"\u0001\u0000\u0000\u0000\t\u0000>DFLNW^d\u0001\u0006\u0000\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}