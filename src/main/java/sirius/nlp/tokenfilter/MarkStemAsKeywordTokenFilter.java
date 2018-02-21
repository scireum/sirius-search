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
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import sirius.nlp.attribute.PrimaryWordAttribute;

import java.io.IOException;

public final class MarkStemAsKeywordTokenFilter extends TokenFilter {

    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
    private final KeywordAttribute keywordAttribute = addAttribute(KeywordAttribute.class);
    private final PrimaryWordAttribute primAttribute = addAttribute(PrimaryWordAttribute.class);
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    public MarkStemAsKeywordTokenFilter(TokenStream result) {
        super(result);
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!input.incrementToken()) {
            return false;
        }

        if (MarkTermTokenFilter.ORIGINAL_TOKEN_BEFORE_STEMMING.equals(typeAtt.type())) {
            boolean isSetToKeyword = !equals(termAtt.buffer(), primAttribute.getOriginalToken(), termAtt.length());
            // mark the original term as keyword if the stemmer changed the word
            keywordAttribute.setKeyword(isSetToKeyword);
        } else {
            // mark generated stems as keywords so that they are ignored in following filters
            keywordAttribute.setKeyword(true);
        }

        return true;
    }

    private boolean equals(char[] a, char[] a2, int length) {
        if (a == a2) {
            return true;
        }

        if (a == null || a2 == null) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (a[i] != a2[i]) {
                return false;
            }
        }

        return true;
    }
}
