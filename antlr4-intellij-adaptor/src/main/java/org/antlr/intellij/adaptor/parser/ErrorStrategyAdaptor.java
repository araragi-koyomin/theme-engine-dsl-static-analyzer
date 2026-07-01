package org.antlr.intellij.adaptor.parser;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.tree.ErrorNode;

/** Adapt ANTLR's DefaultErrorStrategy so that we add error nodes
 *  for EOF if reached at start of resync's consumeUntil().
 *  Also set start/stop of missing token to always be the current token,
 *  even if that's EOF.
 */
public class ErrorStrategyAdaptor extends DefaultErrorStrategy {
	@Override
	protected void consumeUntil(Parser recognizer, IntervalSet set) {
		Token o = recognizer.getCurrentToken();
		if ( o.getType()==Token.EOF ) {
			ErrorNode errorNode = recognizer.createErrorNode(recognizer.getRuleContext(), o);
			recognizer.getRuleContext().addErrorNode(errorNode);
		}
		super.consumeUntil(recognizer, set);
	}

	/** ANTLR's {@link DefaultErrorStrategy#recoverInline(Parser) recoverInline}
	 *  reports a "missing token" error via {@code reportMissingToken} and
	 *  returns a <em>conjured</em> token (tokenIndex &lt; 0) without throwing,
	 *  but the generated rule code discards that returned token, so the parse
	 *  tree ends up with no node for the error. The PSI converter only surfaces
	 *  errors that have a corresponding terminal/error node, so such a rule
	 *  (e.g. {@code varName} when given {@code @} then EOF) would produce an
	 *  empty, seemingly-valid node instead of a "missing {ID, VAR_ID}" error.
	 *
	 *  <p>To fix that, attach an {@link ErrorNode} for the conjured token to
	 *  the current rule context; {@code ANTLRParseTreeToPSIConverter.visitErrorNode}
	 *  already knows how to render conjured tokens as PSI error markers.</p>
	 */
	@Override
	public Token recoverInline(Parser recognizer) throws RecognitionException {
		Token t = super.recoverInline(recognizer);
		if ( t != null && t.getTokenIndex() < 0 ) {
			ErrorNode errorNode = recognizer.createErrorNode(recognizer.getRuleContext(), t);
			recognizer.getRuleContext().addErrorNode(errorNode);
		}
		return t;
	}

	/** By default ANTLR makes the start/stop -1/-1 for invalid tokens
	 *  which is reasonable but here we want to highlight the
	 *  current position indicating that is where we lack a token.
	 *  if no input, highlight at position 0.
	 */
	protected Token getMissingSymbol(Parser recognizer) {
		Token missingSymbol = super.getMissingSymbol(recognizer);
		// alter the default missing symbol.
		if ( missingSymbol instanceof CommonToken) {
			int start, stop;
			Token current = recognizer.getCurrentToken();
			start = current.getStartIndex();
			stop = current.getStopIndex();
			((CommonToken) missingSymbol).setStartIndex(start);
			((CommonToken) missingSymbol).setStopIndex(stop);
		}
		return missingSymbol;
	}
}
