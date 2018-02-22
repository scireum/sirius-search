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
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import sirius.kernel.commons.Strings;
import sirius.nlp.attribute.PrimaryWordAttribute;

import java.io.IOException;

public final class MarkOriginalTermTokenFilter extends TokenFilter {

    public static final String ORIGINAL_TOKEN_BEFORE_STEMMING = "ORIGINAL_TOKEN_BEFORE_STEMMING";

    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
    private final PrimaryWordAttribute primAttribute = addAttribute(PrimaryWordAttribute.class);
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    /**
     * Construct a token stream filtering the given input.
     *
     * @param input
     */
    public MarkOriginalTermTokenFilter(TokenStream input) {
        super(input);
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!input.incrementToken()) {
            return false;
        }

        typeAtt.setType(ORIGINAL_TOKEN_BEFORE_STEMMING);
        primAttribute.setOriginalToken(termAtt.buffer(), termAtt.length());

        return true;
    }
}
