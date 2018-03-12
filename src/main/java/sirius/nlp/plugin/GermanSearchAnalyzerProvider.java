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
import sirius.nlp.analyzer.de.GermanSearchAnalyzer;
import sirius.nlp.util.RessourceLoading;

public class GermanSearchAnalyzerProvider extends AbstractIndexAnalyzerProvider<GermanSearchAnalyzer> {

    private GermanSearchAnalyzer analyzer;

    /**
     * Constructs a new analyzer component, with the index name and its settings and the analyzer name.
     *
     * @param indexSettings the settings and the name of the index
     * @param name          The analyzer name
     * @param settings
     */
    public GermanSearchAnalyzerProvider(IndexSettings indexSettings,
                                        Environment environment,
                                        String name,
                                        Settings settings) {
        super(indexSettings, name, settings);
        this.analyzer = new GermanSearchAnalyzer(RessourceLoading.getGermanStemExceptions(indexSettings.getSettings()
                                                                                                       .get("sirius.analyzer.german-search-analyzer.stemming.path")),
                                                 RessourceLoading.getGermanHyphen(indexSettings.getSettings()
                                                                                               .get("sirius.analyzer.german-search-analyzer.hyphen.path")),
                                                 RessourceLoading.getGermanSynonyms(indexSettings.getSettings()
                                                                                                 .get("sirius.analyzer.german-search-analyzer.synonyms.path")),
                                                 RessourceLoading.getGermanWordList(indexSettings.getSettings()
                                                                                                 .get("sirius.analyzer.german-search-analyzer.dict.path")));
    }

    @Override
    public GermanSearchAnalyzer get() {
        return this.analyzer;
    }
}
