/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.nlp.analyzer.de;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.StopwordAnalyzerBase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilter;
import org.apache.lucene.analysis.compound.hyphenation.HyphenationTree;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.de.GermanNormalizationFilter;
import org.apache.lucene.analysis.miscellaneous.RemoveDuplicatesTokenFilter;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import sirius.nlp.tokenfilter.GermanStemmingTokenFilter;
import sirius.nlp.tokenfilter.RemoveEmptyTokensTokenFilter;
import sirius.nlp.tokenfilter.RemoveInitialTermTokenFilter;

import java.io.Reader;

public class GermanSearchAnalyzer extends StopwordAnalyzerBase {

    private final SynonymMap stemExceptions;
    private final HyphenationTree hyphen;
    private final SynonymMap synonyms;
    private final CharArraySet dictionary;

    public GermanSearchAnalyzer(SynonymMap stemExceptions,
                                HyphenationTree hyphen,
                                SynonymMap synonyms,
                                CharArraySet dictionary) {
        super(GermanAnalyzer.getDefaultStopSet());
        this.stemExceptions = stemExceptions;
        this.dictionary = dictionary;
        this.hyphen = hyphen;
        this.synonyms = synonyms;
    }

    @Override
    protected Reader initReader(String fieldName, Reader reader) {
        return new HTMLStripCharFilter(reader);
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        // split up terms at special chars or technical terms and merge them
        int configFlag = WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS
                         | WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE
                         | WordDelimiterGraphFilter.SPLIT_ON_NUMERICS
                         | WordDelimiterGraphFilter.STEM_ENGLISH_POSSESSIVE
                         | WordDelimiterGraphFilter.GENERATE_WORD_PARTS;

        final Tokenizer source = new WhitespaceTokenizer();
        TokenStream result = new WordDelimiterGraphFilter(source, configFlag, null);

        result = new LowerCaseFilter(result);
        result = new StopFilter(result, stopwords);

        // decompound words
        result = new RemoveInitialTermTokenFilter(result, hyphen, dictionary, 3, 2, 15, true);
        result = new RemoveEmptyTokensTokenFilter(result);

        // start stemming
        result = new SynonymGraphFilter(result, stemExceptions, true);
        result = new GermanStemmingTokenFilter(result,
                                               "true",
                                               "true"); // TODO longstOnly mit in kombi mit GermanStemmingFilter checken

        // normalize german umlauts etc.
        result = new GermanNormalizationFilter(result);
        result = new RemoveDuplicatesTokenFilter(result); // TODO

        return new TokenStreamComponents(source, result);
    }
}
