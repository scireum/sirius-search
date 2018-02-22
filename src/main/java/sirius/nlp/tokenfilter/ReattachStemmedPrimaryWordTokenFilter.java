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
import sirius.nlp.attribute.PrimaryWordAttribute;

import java.io.IOException;
import java.util.Arrays;

public final class ReattachStemmedPrimaryWordTokenFilter extends TokenFilter {

    private final PrimaryWordAttribute primaryWordAttr = addAttribute(PrimaryWordAttribute.class);
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    public ReattachStemmedPrimaryWordTokenFilter(TokenStream result) {
        super(result);
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!input.incrementToken()) {
            return false;
        }

        if (primaryWordAttr.isPrimaryWordTokenEmitted()) {
            char[] prefix = Arrays.copyOfRange(primaryWordAttr.getOriginalToken(),
                                               0,
                                               primaryWordAttr.getOriginalTokenLength()
                                               - primaryWordAttr.getPrimaryWordTokenLength());
            char[] stemmedPrimary = Arrays.copyOfRange(termAtt.buffer(), 0, termAtt.length());
            termAtt.setEmpty().append(new String(prefix)).append(new String(stemmedPrimary));
        }
        return true;
    }
}
