/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.nlp.tokenfilter;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import sirius.nlp.util.CharArray;

import java.io.IOException;
import java.util.LinkedList;

/**
 * A {@link TokenFilter} which is able to close the whitespace-"gap" between numbers, so that they can be queried
 * later if the user doesn't input enough whitespace
 * <p>
 * Example: a document contains "12 34 56" and the user wants to query via "1234" or "3456". In this case we have to
 * remove the whitespace between adjacent number pairs
 */
public final class CloseGapBetweenNumbersTokenFilter extends TokenFilter {

    private final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    private LinkedList<CharSequence> buffer;
    private int position = 0;
    private boolean useBuffer;

    /**
     * Construct a token stream filtering the given input.
     *
     * @param input the {@link TokenStream} to consume
     */
    public CloseGapBetweenNumbersTokenFilter(TokenStream input) {
        super(input);
        buffer = new LinkedList<>();
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (position != 0 && buffer.size() - 1 == position) {
            // we have outputed all combinations for the current buffer, e.g. if the buffer contained ["12", "34", "56"]
            // we have generated "123456" and "3456". Now remove the last element and generate more combinations
            // if possible, in this example "1234".
            position = 0;
            buffer.removeLast();
        }

        // output buffered combinations if the last call to this method read a non-digit token
        if (buffer.size() > 1 && useBuffer) {
            return outputCombinations();
        }

        if (!input.incrementToken()) {
            if (buffer.size() > 1) {
                return outputCombinations();
            }
            return false;
        }

        if (CharArray.containsOnlyDigitsOrSpecialChar(termAtt.buffer(), termAtt.length())) {
            buffer.add(new String(termAtt.buffer(), 0, termAtt.length()));
        } else {
            buffer.clear();
            position = 0;
            useBuffer = false;
        }

        return true;
    }

    public boolean outputCombinations() {
        StringBuilder output = new StringBuilder();
        for (int i = position; i < buffer.size(); i++) {
            output.append(buffer.get(i));
        }
        termAtt.setEmpty().append(output);
        posIncAtt.setPositionIncrement(0); // TODO offsets?
        position++;
        return true;
    }
}
