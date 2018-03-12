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
import org.apache.lucene.analysis.compound.HyphenationCompoundWordTokenFilter;
import org.apache.lucene.analysis.compound.hyphenation.HyphenationTree;
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.de.GermanNormalizationFilter;
import org.apache.lucene.analysis.miscellaneous.RemoveDuplicatesTokenFilter;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import sirius.nlp.tokenfilter.CloseGapBetweenNumbersTokenFilter;
import sirius.nlp.tokenfilter.GermanStemmingTokenFilter;
import sirius.nlp.tokenfilter.RemoveLeadingZerosTokenFilter;

import java.io.Reader;

public class GermanIndexingAnalyzer extends StopwordAnalyzerBase {

    private final SynonymMap stemExceptions;
    private final HyphenationTree hyphen;
    private final SynonymMap synonyms;
    private final CharArraySet dictionary;

    public GermanIndexingAnalyzer(SynonymMap stemExceptions,
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
        // decompound words
        result = new HyphenationCompoundWordTokenFilter(result, hyphen, dictionary, 3, 2, 15, false);

        // start stemming
        result = new SynonymGraphFilter(result, stemExceptions, true);
        result = new FlattenGraphFilter(result);
        result = new GermanStemmingTokenFilter(result, "true", "true");

        // TODO: check docs what problems occur doing this while index vs search time
        // inject synonym terms
        result = new SynonymGraphFilter(result, synonyms, true);
        result = new FlattenGraphFilter(result);

        // normalize german umlauts etc.
        result = new GermanNormalizationFilter(result);
        result = new RemoveDuplicatesTokenFilter(result); // TODO
        result = new RemoveLeadingZerosTokenFilter(result); // TODO

        return new TokenStreamComponents(source, result);
    }
}
