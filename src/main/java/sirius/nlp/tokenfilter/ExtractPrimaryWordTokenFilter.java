/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.nlp.tokenfilter;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.compound.HyphenationCompoundWordTokenFilter;
import org.apache.lucene.analysis.compound.hyphenation.HyphenationTree;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import sirius.nlp.attribute.PrimaryWordAttribute;

/**
 * Tries to extract the semantic word (primary word) of a german compound-word. This is (in most cases) the most right subword
 * for a german compound word. E.g. for "dampfschifffahrtskapitänsmützen" this whould be "mützen"
 * <p>
 * This can be uses to stem a compound without having all existing compound words in a big stemming wordlist.
 */
public final class ExtractPrimaryWordTokenFilter extends HyphenationCompoundWordTokenFilter {

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PrimaryWordAttribute primaryWordAttr = addAttribute(PrimaryWordAttribute.class);
    private final KeywordAttribute keywordAttribute = addAttribute(KeywordAttribute.class);

    public ExtractPrimaryWordTokenFilter(TokenStream input, HyphenationTree hyphenator, CharArraySet dictionary) {
        super(input, hyphenator, dictionary);
    }

    @Override
    protected void decompose() {
        if (!keywordAttribute.isKeyword()) {
            super.decompose();

            if (tokens.size() > 1) {
                // just keep the last word => the primary word
                while (tokens.size() > 1) {
                    tokens.removeFirst();
                }

                primaryWordAttr.setPrimaryWordTokenEmitted(true);
                primaryWordAttr.setOriginalToken(termAtt.buffer(), termAtt.length());
                primaryWordAttr.setPrimaryWordToken(tokens.get(0).txt.toString().toCharArray(),
                                                    tokens.get(0).txt.length());
                termAtt.setEmpty().append(tokens.get(0).txt);
            }

            tokens.clear();
        }
    }
}
