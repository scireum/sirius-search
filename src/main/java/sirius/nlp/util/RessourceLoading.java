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
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.synonym.SolrSynonymParser;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.util.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;

public class RessourceLoading {
    private static volatile HyphenationTree germanHyphen;
    private static volatile CharArraySet germanWordList;
    private static volatile SynonymMap germanStemExceptions;
    private static volatile SynonymMap germanSynonyms;
    private static volatile CharArraySet germanStopWords;

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

    public static CharArraySet getGermanWordList() {
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

    public static SynonymMap getGermanStemExceptions() {
        if (germanStemExceptions == null) {
            synchronized (RessourceLoading.class) {
                try {
                    if (germanStemExceptions == null) {
                        SolrSynonymParser solrSynonymParser = new SolrSynonymParser(true, true, new StandardAnalyzer());
                        solrSynonymParser.parse(new BufferedReader(new FileReader(new File(
                                "src/main/resources/germanStemexceptions.txt"))));
                        germanStemExceptions = solrSynonymParser.build();
                    }
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }
        }
        return germanStemExceptions;
    }

    public static SynonymMap getGermanSynonyms() {
        if (germanSynonyms == null) {
            synchronized (RessourceLoading.class) {
                try {
                    if (germanSynonyms == null) {
                        SolrSynonymParser solrSynonymParser = new SolrSynonymParser(true, true, new StandardAnalyzer());
                        solrSynonymParser.parse(new BufferedReader(new FileReader(new File(
                                "src/main/resources/germanSynonyms.txt"))));
                        germanSynonyms = solrSynonymParser.build();
                    }
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }
        }
        return germanSynonyms;
    }

    public static CharArraySet getGermanStopWords(){
        if (germanStopWords == null) {
            synchronized (RessourceLoading.class) {
                try {
                    if (germanStopWords == null) {
                        germanStopWords = WordlistLoader.getSnowballWordSet(IOUtils.getDecodingReader(SnowballFilter.class,
                                                                                    "german_stop.txt",
                                                                                    Charset.forName("UTF-8")));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return germanStopWords;
    }
}
