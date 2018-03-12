/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.nlp.de;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import sirius.kernel.health.Exceptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AnalyzerEvaluationHelper {
    private List<String> searchTerms;
    private List<String> prohibitedTerms;
    private String input;

    private AnalyzerEvaluationHelper() {
        this.searchTerms = new ArrayList<>();
        this.prohibitedTerms = new ArrayList<>();
    }

    public void setInput(String input) {
        this.input = input;
    }

    public static AnalyzerEvaluationHelper withInput(Analyzer analyzer, String input) {
        AnalyzerEvaluationHelper analyzerEvaluationHelper = new AnalyzerEvaluationHelper();
        analyzerEvaluationHelper.input = analyzerEvaluationHelper.analyze(analyzer, input);
        return analyzerEvaluationHelper;
    }

    public AnalyzerEvaluationHelper canSearchWith(Analyzer analyzer, String searchTerm) {
        this.searchTerms.add(analyze(analyzer, searchTerm));
        return this;
    }


    public AnalyzerEvaluationHelper cannotSearchWith(Analyzer analyzer, String searchTerm) {
        this.prohibitedTerms.add(analyze(analyzer, searchTerm));
        return this;
    }


    private String analyze(Analyzer analyzer, String text) {
        TokenStream tokenStream = analyzer.tokenStream("test", text);
        CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
        StringBuilder output = new StringBuilder();
        try {
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                output.append(charTermAttribute.toString()).append(" ");
            }
            tokenStream.close();
        } catch (IOException e) {
            Exceptions.handle(e);
        }

        return output.toString();
    }

    public boolean evaluate() {
        String[] inputTerms = input.split(" ");

        for (String searchTerm : searchTerms) {
            for (String subTerm : searchTerm.split(" ")) {
                if (Arrays.stream(inputTerms).noneMatch(inputTerm -> inputTerm.equals(subTerm))) {
                    throw Exceptions.handle()
                                    .withSystemErrorMessage("Cannot search with term '%s' in input '%s", subTerm, input)
                                    .handle();
                }
            }
        }

        for (String prohibitedTerm : prohibitedTerms) {
            for (String subTerm : prohibitedTerm.split(" ")) {
                if (Arrays.stream(inputTerms).anyMatch(inputTerm -> inputTerm.equals(subTerm))) {
                    throw Exceptions.handle()
                                    .withSystemErrorMessage("Should not be able to search with term '%s' in input '%s",
                                                            subTerm,
                                                            input)
                                    .handle();
                }
            }
        }

        return true;
    }
}
