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
import org.apache.lucene.analysis.WordlistLoader;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilter;
import org.apache.lucene.analysis.compound.HyphenationCompoundWordTokenFilter;
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.de.GermanNormalizationFilter;
import org.apache.lucene.analysis.miscellaneous.RemoveDuplicatesTokenFilter;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.util.IOUtils;
import sirius.nlp.tokenfilter.CloseGapBetweenNumbersTokenFilter;
import sirius.nlp.tokenfilter.GermanStemmingTokenFilter;
import sirius.nlp.tokenfilter.RemoveLeadingZerosTokenFilter;
import sirius.nlp.util.RessourceLoading;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;

public class GermanIndexingAnalyzer extends StopwordAnalyzerBase {

    public static final String DEFAULT_STOPWORD_FILE = "german_stop.txt";

    private final CharArraySet exclusionSet;

    public GermanIndexingAnalyzer() {
        this(GermanIndexingAnalyzer.DefaultSetHolder.DEFAULT_SET);
    }

    public GermanIndexingAnalyzer(CharArraySet stopwords) {
        this(stopwords, CharArraySet.EMPTY_SET);
    }

    public GermanIndexingAnalyzer(CharArraySet stopwords, CharArraySet stemExclusionSet) {
        super(stopwords);
        exclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet));
    }

    @Override
    protected Reader initReader(String fieldName, Reader reader) {
        return new HTMLStripCharFilter(reader);
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
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
        result = new HyphenationCompoundWordTokenFilter(result,
                                                        RessourceLoading.getGermanHyphen(),
                                                        RessourceLoading.getGermanWordlist(),
                                                        3,
                                                        2,
                                                        15,
                                                        false);

        // start stemming
        result = new SynonymGraphFilter(result, RessourceLoading.getStemExceptions(), true);
        result = new FlattenGraphFilter(result);
        result = new GermanStemmingTokenFilter(result, "true", "true");

        // TODO: check docs what problems occur doing this while index vs search time
        // inject synonym terms
        result = new SynonymGraphFilter(result, RessourceLoading.getSynonyms(), true);
        result = new FlattenGraphFilter(result);

        // normalize german umlauts etc.
        result = new GermanNormalizationFilter(result);
        result = new RemoveDuplicatesTokenFilter(result); // TODO
        result = new RemoveLeadingZerosTokenFilter(result); // TODO

        return new TokenStreamComponents(source, result);
    }

    private static class DefaultSetHolder {
        private static final CharArraySet DEFAULT_SET;

        static {
            try {
                DEFAULT_SET = WordlistLoader.getSnowballWordSet(IOUtils.getDecodingReader(SnowballFilter.class,
                                                                                          DEFAULT_STOPWORD_FILE,
                                                                                          Charset.forName("UTF-8")));
            } catch (IOException ex) {
                // default set should always be present as it is part of the
                // distribution (JAR)
                throw new RuntimeException("Unable to load default stopword set");
            }
        }
    }
}
