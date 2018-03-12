/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.nlp.plugin;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractIndexAnalyzerProvider;
import sirius.nlp.analyzer.de.GermanIndexingAnalyzer;
import sirius.nlp.util.RessourceLoading;

public class GermanIndexingAnalyzerProvider extends AbstractIndexAnalyzerProvider<GermanIndexingAnalyzer> {

    private GermanIndexingAnalyzer analyzer;

    /**
     * Constructs a new analyzer component, with the index name and its settings and the analyzer name.
     *
     * @param indexSettings the settings and the name of the index
     * @param name          The analyzer name
     * @param settings
     */
    public GermanIndexingAnalyzerProvider(IndexSettings indexSettings,
                                          Environment environment,
                                          String name,
                                          Settings settings) {
        super(indexSettings, name, settings);
        this.analyzer = new GermanIndexingAnalyzer(RessourceLoading.getGermanStemExceptions(indexSettings.getSettings()
                                                                                                         .get("sirius.analyzer.german-indexing-analyzer.stemming.path")),
                                                   RessourceLoading.getGermanHyphen(indexSettings.getSettings()
                                                                                                 .get("sirius.analyzer.german-indexing-analyzer.hyphen.path")),
                                                   RessourceLoading.getGermanSynonyms(indexSettings.getSettings()
                                                                                                   .get("sirius.analyzer.german-indexing-analyzer.synonyms.path")),
                                                   RessourceLoading.getGermanWordList(indexSettings.getSettings()
                                                                                                   .get("sirius.analyzer.german-indexing-analyzer.dict.path")));
    }

    @Override
    public GermanIndexingAnalyzer get() {
        return this.analyzer;
    }
}
