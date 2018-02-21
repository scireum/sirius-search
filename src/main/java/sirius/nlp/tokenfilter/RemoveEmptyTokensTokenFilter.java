/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.nlp.tokenfilter;

import org.apache.lucene.analysis.FilteringTokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.IOException;

public class RemoveEmptyTokensTokenFilter extends FilteringTokenFilter {

    protected final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    /**
     * Create a new {@link FilteringTokenFilter}.
     *
     * @param in the {@link TokenStream} to consume
     */
    public RemoveEmptyTokensTokenFilter(TokenStream in) {
        super(in);
    }

    @Override
    protected boolean accept() throws IOException {
        return termAtt.length() > 0;
    }
}
