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
import sirius.nlp.analyzer.de.GermanPrimaryWordOnlyIndexingAnalyzer;
import sirius.nlp.util.RessourceLoading;

public class GermanPrimaryWordOnlyIndexingAnalyzerProvider
        extends AbstractIndexAnalyzerProvider<GermanPrimaryWordOnlyIndexingAnalyzer> {
    private GermanPrimaryWordOnlyIndexingAnalyzer analyzer;

    /**
     * Constructs a new analyzer component, with the index name and its settings and the analyzer name.
     *
     * @param indexSettings the settings and the name of the index
     * @param name          The analyzer name
     * @param settings
     */
    public GermanPrimaryWordOnlyIndexingAnalyzerProvider(IndexSettings indexSettings,
                                                         Environment environment,
                                                         String name,
                                                         Settings settings) {
        super(indexSettings, name, settings);
        this.analyzer = new GermanPrimaryWordOnlyIndexingAnalyzer(RessourceLoading.getGermanStemExceptions(indexSettings
                                                                                                                   .getSettings()
                                                                                                                   .get("sirius.analyzer.german-primary-word-analyzer.stemming.path")),
                                                                  RessourceLoading.getGermanHyphen(indexSettings.getSettings()
                                                                                                                .get("sirius.analyzer.german-primary-word-analyzer.hyphen.path")),
                                                                  RessourceLoading.getGermanSynonyms(indexSettings.getSettings()
                                                                                                                  .get("sirius.analyzer.german-primary-word-analyzer.synonyms.path")),
                                                                  RessourceLoading.getGermanWordList(indexSettings.getSettings()
                                                                                                                  .get("sirius.analyzer.german-primary-word-analyzer.dict.path")));
    }

    @Override
    public GermanPrimaryWordOnlyIndexingAnalyzer get() {
        return this.analyzer;
    }
}
