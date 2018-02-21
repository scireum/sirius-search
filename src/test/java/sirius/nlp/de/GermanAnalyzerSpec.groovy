/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.nlp.de

import sirius.kernel.BaseSpecification
import sirius.nlp.analyzer.de.GermanIndexingAnalyzer
import sirius.nlp.analyzer.de.GermanSearchAnalyzer


class GermanAnalyzerSpec extends BaseSpecification {

    static GermanIndexingAnalyzer indexingAnalyzer
    static GermanSearchAnalyzer searchAnalyzer

    def setupSpec() {
        indexingAnalyzer = new GermanIndexingAnalyzer()
        searchAnalyzer = new GermanSearchAnalyzer()
    }

    def testSearch(String textForIndexing, String[] searchTexts) {
        AnalyzerEvaluationHelper analyzerEvaluationHelper = AnalyzerEvaluationHelper.withInput(indexingAnalyzer, textForIndexing)
        Arrays.stream(searchTexts).forEach({ searchText -> analyzerEvaluationHelper.canSearchWith(searchAnalyzer, searchText) })
        return analyzerEvaluationHelper.evaluate()
    }

    def "test natural terms"() {
        expect:
        testSearch(textForIndexing, searchTexts)
        where:
        textForIndexing                   | searchTexts
        "Dampfschifffahrtskapitänsmützen" | ["dampfschiff", "kapitänsmütze", "kapitaensmütze", "kapitänsmützen",
                                             "schifffahrtskapitänsmütze", "Dampfschifffahrtskapitänsmützen", "Dampfschifffahrtskapitänsmütze"] as String[]

    }

    def "test terms with separator"() {
        expect:
        testSearch(textForIndexing, searchTexts)
        where:
        textForIndexing   | searchTexts
        "T-Stück"         | ["stück", "stueck", "t-stück", "t stück", "T-stück", "tstück", "t-stueck"] as String[]
        "GroßeXXL-Klappe" | ["xxl klappe", "GroßeXXL-Klappe", "GrosseXXL-Klappe", "GroßeXXL-Klappe", /* TODO "großexxl klappe",*/
                             "große xxl klappe", "XXL-Klappe", "XXL Klappe"] as String[]
    }

    def "test technical terms"() {
        expect:
        testSearch(textForIndexing, searchTexts)
        where:
        textForIndexing | searchTexts
        "VCW204/3-E"    | ["VCW204/3-E", "vcw", "204", "2043", "vcw204", "VCW204", "vcw2043e", "vcw2043E", "3e", "2043E"] as String[]
        "GBH5-40"       | ["gbh", "GBH", "GBH5", "GBH 40", "GBH5-40", "GBH5 40", "gbh5 40", "GBH540"] as String[]
        "RH 370 170"    | ["RH 370 170", "RH370 170", "rh 170"/* TODO , "RH 370170",  "RH370170", "rh370170", "370170"*/] as String[]
        "0.00815"       | ["0.00815", /* TODO "815",*/ "00815", ".00815"] as String[]
    }
}