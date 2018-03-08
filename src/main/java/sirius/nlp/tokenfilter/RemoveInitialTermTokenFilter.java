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

/**
 * Removes the token, which could be decomposed, from the token stream.
 */
public class RemoveInitialTermTokenFilter extends HyphenationCompoundWordTokenFilter {

    public RemoveInitialTermTokenFilter(TokenStream input, HyphenationTree hyphenator, CharArraySet dictionary) {
        super(input, hyphenator, dictionary);
    }

    public RemoveInitialTermTokenFilter(TokenStream input,
                                        HyphenationTree hyphenator,
                                        CharArraySet dictionary,
                                        int minWordSize,
                                        int minSubwordSize,
                                        int maxSubwordSize,
                                        boolean onlyLongestMatch) {
        super(input, hyphenator, dictionary, minWordSize, minSubwordSize, maxSubwordSize, onlyLongestMatch);
    }

    public RemoveInitialTermTokenFilter(TokenStream input,
                                        HyphenationTree hyphenator,
                                        int minWordSize,
                                        int minSubwordSize,
                                        int maxSubwordSize) {
        super(input, hyphenator, minWordSize, minSubwordSize, maxSubwordSize);
    }

    public RemoveInitialTermTokenFilter(TokenStream input, HyphenationTree hyphenator) {
        super(input, hyphenator);
    }

    @Override
    protected void decompose() {
        super.decompose();

        // if we could decompose the term => don't emit the term to the next stage
        if (!tokens.isEmpty()) {
            termAtt.setEmpty();
        }
    }
}
