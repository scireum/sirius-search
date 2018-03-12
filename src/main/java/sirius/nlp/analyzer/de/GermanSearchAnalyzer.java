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
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.de.GermanNormalizationFilter;
import org.apache.lucene.analysis.miscellaneous.RemoveDuplicatesTokenFilter;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import sirius.nlp.tokenfilter.GermanStemmingTokenFilter;
import sirius.nlp.tokenfilter.RemoveEmptyTokensTokenFilter;
import sirius.nlp.tokenfilter.RemoveInitialTermTokenFilter;
import sirius.nlp.util.RessourceLoading;

import java.io.Reader;

public class GermanSearchAnalyzer extends StopwordAnalyzerBase {

    private final CharArraySet exclusionSet;

    public GermanSearchAnalyzer() {
        this(RessourceLoading.getGermanStopWords());
    }

    public GermanSearchAnalyzer(CharArraySet stopwords) {
        this(stopwords, CharArraySet.EMPTY_SET);
    }

    public GermanSearchAnalyzer(CharArraySet stopwords, CharArraySet stemExclusionSet) {
        super(stopwords);
        exclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet));
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
        result = new FlattenGraphFilter(result);

        result = new LowerCaseFilter(result);
        result = new StopFilter(result, stopwords);
        result = new SetKeywordMarkerFilter(result, exclusionSet); // TODO: needed?

        // decompound words
        result = new RemoveInitialTermTokenFilter(result,
                                                  RessourceLoading.getGermanHyphen(),
                                                  RessourceLoading.getGermanWordList(),
                                                  3,
                                                  2,
                                                  15,
                                                  true);
        result = new RemoveEmptyTokensTokenFilter(result);

        // start stemming
        result = new SynonymGraphFilter(result, RessourceLoading.getGermanStemExceptions(), true);
        result = new GermanStemmingTokenFilter(result,
                                               "true",
                                               "true"); // TODO longstOnly mit in kombi mit GermanStemmingFilter checken

        // normalize german umlauts etc.
        result = new GermanNormalizationFilter(result);
        result = new RemoveDuplicatesTokenFilter(result); // TODO

        return new TokenStreamComponents(source, result);
    }
}
