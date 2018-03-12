/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.nlp.plugin;

import org.apache.lucene.analysis.Analyzer;
import org.elasticsearch.index.analysis.AnalyzerProvider;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.plugins.AnalysisPlugin;
import sirius.nlp.analyzer.de.GermanPrimaryWordOnlyIndexingAnalyzer;

import java.util.HashMap;
import java.util.Map;

public class GermanAnalyzers implements AnalysisPlugin {
    @Override
    public Map<String, AnalysisModule.AnalysisProvider<AnalyzerProvider<? extends Analyzer>>> getAnalyzers() {
        Map<String, AnalysisModule.AnalysisProvider<AnalyzerProvider<? extends Analyzer>>> analyzers = new HashMap<>();
        analyzers.put("german_indexing_analyzer", GermanIndexingAnalyzerProvider::new);
        analyzers.put("german_primary_word_indexing_analyzer", GermanPrimaryWordOnlyIndexingAnalyzerProvider::new);
        return analyzers;
    }
}
