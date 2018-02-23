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
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import sirius.nlp.util.CharArray;

import java.io.IOException;

/**
 * Removes leading zeros from number tokens, so that a user can query the value without inserting the leading zeros.
 * E.g. a document term of "0007777" can be queried via "7777".
 */
public final class RemoveLeadingZerosTokenFilter extends TokenFilter {

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncAtt = addAttribute(PositionIncrementAttribute.class);
    protected final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);

    private char[] bufferNumericWithoutZeros = new char[16];
    private int bufferNumericWithoutZerosLength = 0;
    private int startOffset = 0;
    private int endOffset = 0;

    /**
     * Construct a token stream filtering the given input.
     *
     * @param input the {@link TokenStream} to consume
     */
    public RemoveLeadingZerosTokenFilter(TokenStream input) {
        super(input);
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (bufferNumericWithoutZerosLength > 0) {
            termAtt.setEmpty().append(new String(bufferNumericWithoutZeros, 0, bufferNumericWithoutZerosLength));
            offsetAtt.setOffset(startOffset, endOffset);
            posIncAtt.setPositionIncrement(0);

            // reset buffer
            bufferNumericWithoutZerosLength = 0;
            return true;
        }

        // the term must start with at least one '0'
        if (termAtt.length() > 0 && termAtt.buffer()[0] == '0') {
            int numberOfStartingZeros = findZerosOffset();
            bufferNumericWithoutZeros = CharArray.assureArrayLength(bufferNumericWithoutZeros, termAtt.length());
            System.arraycopy(termAtt.buffer(),
                             numberOfStartingZeros,
                             bufferNumericWithoutZeros,
                             0,
                             termAtt.length() - numberOfStartingZeros);
            bufferNumericWithoutZerosLength = termAtt.length() - numberOfStartingZeros;
            startOffset = numberOfStartingZeros;
            endOffset = termAtt.length();
        }

        return input.incrementToken();
    }

    private int findZerosOffset() {
        int offset = 0;

        while (offset < termAtt.length() && termAtt.buffer()[offset] == '0') {
            offset++;
        }

        return offset;
    }
}
