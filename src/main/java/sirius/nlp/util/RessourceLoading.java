/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.nlp.util;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.WordlistLoader;
import org.apache.lucene.analysis.compound.HyphenationCompoundWordTokenFilter;
import org.apache.lucene.analysis.compound.hyphenation.HyphenationTree;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.synonym.SolrSynonymParser;
import org.apache.lucene.analysis.synonym.SynonymMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;

public class RessourceLoading {
    private static volatile HyphenationTree germanHyphen;
    private static volatile CharArraySet germanWordList;
    private static volatile SynonymMap stemExceptions;
    private static volatile SynonymMap synonyms;

    public static HyphenationTree getGermanHyphen() {
        if (germanHyphen == null) {
            synchronized (RessourceLoading.class) {
                try {
                    if (germanHyphen == null) {
                        germanHyphen =
                                HyphenationCompoundWordTokenFilter.getHyphenationTree("src/main/resources/hyph_de.xml");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return germanHyphen;
    }

    public static CharArraySet getGermanWordlist() {
        if (germanWordList == null) {
            synchronized (RessourceLoading.class) {
                try {
                    if (germanWordList == null) {
                        germanWordList = WordlistLoader.getWordSet(new BufferedReader(new FileReader(new File(
                                "src/main/resources/wordlist/de/wordlist.txt"))));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return germanWordList;
    }

    public static SynonymMap getStemExceptions() {
        if (stemExceptions == null) {
            synchronized (RessourceLoading.class) {
                try {
                    if (stemExceptions == null) {
                        SolrSynonymParser solrSynonymParser = new SolrSynonymParser(true, true, new StandardAnalyzer());
                        solrSynonymParser.parse(new BufferedReader(new FileReader(new File(
                                "src/main/resources/stemexceptions.txt"))));
                        stemExceptions = solrSynonymParser.build();
                    }
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }
        }
        return stemExceptions;
    }

    public static SynonymMap getSynonyms() {
        if (synonyms == null) {
            synchronized (RessourceLoading.class) {
                try {
                    if (synonyms == null) {
                        SolrSynonymParser solrSynonymParser = new SolrSynonymParser(true, true, new StandardAnalyzer());
                        solrSynonymParser.parse(new BufferedReader(new FileReader(new File(
                                "src/main/resources/synonyms.txt"))));
                        synonyms = solrSynonymParser.build();
                    }
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }
        }
        return synonyms;
    }
}
