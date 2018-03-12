/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.nlp.analyzer.de;

import org.apache.lucene.analysis.Analyzer;
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
import sirius.nlp.tokenfilter.CloseGapBetweenNumbersTokenFilter;
import sirius.nlp.tokenfilter.ExtractPrimaryWordTokenFilter;
import sirius.nlp.tokenfilter.GermanStemmingTokenFilter;
import sirius.nlp.tokenfilter.RemoveLeadingZerosTokenFilter;
import sirius.nlp.util.RessourceLoading;

import java.io.Reader;

public class GermanPrimaryWordOnlyIndexingAnalyzer extends StopwordAnalyzerBase {

    private final CharArraySet exclusionSet;

    public GermanPrimaryWordOnlyIndexingAnalyzer() {
        this(RessourceLoading.getGermanStopWords());
    }

    public GermanPrimaryWordOnlyIndexingAnalyzer(CharArraySet stopwords) {
        this(stopwords, CharArraySet.EMPTY_SET);
    }

    public GermanPrimaryWordOnlyIndexingAnalyzer(CharArraySet stopwords, CharArraySet stemExclusionSet) {
        super(stopwords);
        exclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet));
    }

    @Override
    protected Reader initReader(String fieldName, Reader reader) {
        return new HTMLStripCharFilter(reader);
    }

    @Override
    protected Analyzer.TokenStreamComponents createComponents(String fieldName) {
        int configFlag = WordDelimiterGraphFilter.GENERATE_WORD_PARTS
                         | WordDelimiterGraphFilter.CATENATE_ALL
                         | WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS
                         | WordDelimiterGraphFilter.CATENATE_WORDS
                         | WordDelimiterGraphFilter.CATENATE_NUMBERS
                         | WordDelimiterGraphFilter.PRESERVE_ORIGINAL
                         | WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE
                         | WordDelimiterGraphFilter.SPLIT_ON_NUMERICS
                         | WordDelimiterGraphFilter.STEM_ENGLISH_POSSESSIVE;

        final Tokenizer source = new WhitespaceTokenizer();
        TokenStream result = new CloseGapBetweenNumbersTokenFilter(source);

        // split up terms at special chars, transissions or technical terms and merge them if appropriate
        result = new WordDelimiterGraphFilter(result, configFlag, null);
        result = new FlattenGraphFilter(result);

        result = new LowerCaseFilter(result);
        result = new StopFilter(result, stopwords);
        result = new SetKeywordMarkerFilter(result, exclusionSet); // TODO: needed?
        // decompound words
        result = new ExtractPrimaryWordTokenFilter(result,
                                                   RessourceLoading.getGermanHyphen(),
                                                   RessourceLoading.getGermanWordList(),
                                                   true,
                                                   true);

        // start stemming
        result = new SynonymGraphFilter(result, RessourceLoading.getGermanStemExceptions(), true);
        result = new FlattenGraphFilter(result);
        result = new GermanStemmingTokenFilter(result, "true", "true");

        // TODO: check docs what problems occur doing this while index vs search time
        // inject synonym terms
        result = new SynonymGraphFilter(result, RessourceLoading.getGermanSynonyms(), true);
        result = new FlattenGraphFilter(result);

        // normalize german umlauts etc.
        result = new GermanNormalizationFilter(result);
        result = new RemoveDuplicatesTokenFilter(result); // TODO
        result = new RemoveLeadingZerosTokenFilter(result); // TODO

        return new Analyzer.TokenStreamComponents(source, result);
    }
}
