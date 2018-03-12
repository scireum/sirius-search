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

/**
 * Tries to extract the semantic word (primary word) of a german compound-word. This is (in most cases) the most right subword
 * for a german compound word. E.g. for "dampfschifffahrtskapitänsmützen" this whould be "mützen"
 * <p>
 * This can be uses to stem a compound without having all existing compound words in a big stemming wordlist.
 */
public final class ExtractPrimaryWordTokenFilter extends HyphenationCompoundWordTokenFilter {

    private final KeywordAttribute keywordAttribute = addAttribute(KeywordAttribute.class);

    private boolean onlyLongestPrimaryWord;

    public ExtractPrimaryWordTokenFilter(TokenStream input,
                                         HyphenationTree hyphenator,
                                         CharArraySet dictionary,
                                         boolean onlyLongestMatch,
                                         boolean onlyLongestPrimaryWord) {
        super(input, hyphenator, dictionary, 3, 3, 20, onlyLongestMatch);
        this.onlyLongestPrimaryWord = onlyLongestPrimaryWord;
    }

    @Override
    protected void decompose() {
        if (!keywordAttribute.isKeyword()) { // TODO keyword check necessary?
            super.decompose();

            if (tokens.size() > 1) {
                if (onlyLongestPrimaryWord) {
                    // we want to have the longest primary word => find the first token which is a suffix of the termAttribute
                    for (CompoundToken token : tokens) {
                        if (new String(termAtt.buffer(), 0, termAtt.length()).endsWith(token.txt.toString())) {
                            termAtt.setEmpty().append(token.txt);
                            break;
                        }
                    }
                } else {
                    // we don't want the longest primary word => just pick the last one which is the shortest primary word
                    while (tokens.size() > 1) {
                        tokens.removeFirst();
                    }

                    termAtt.setEmpty().append(tokens.get(0).txt);
                }
            }

            tokens.clear();
        }
    }
}
