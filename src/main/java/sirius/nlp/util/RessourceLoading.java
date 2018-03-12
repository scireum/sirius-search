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

    public static HyphenationTree getGermanHyphen(String filePath) {
        if (germanHyphen == null) {
            synchronized (RessourceLoading.class) {
                try {
                    if (germanHyphen == null) {
                        germanHyphen = HyphenationCompoundWordTokenFilter.getHyphenationTree(filePath);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return germanHyphen;
    }

    public static CharArraySet getGermanWordList(String filePath) {
        if (germanWordList == null) {
            synchronized (RessourceLoading.class) {
                try {
                    if (germanWordList == null) {
                        germanWordList =
                                WordlistLoader.getWordSet(new BufferedReader(new FileReader(new File(filePath))));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return germanWordList;
    }

    public static SynonymMap getGermanStemExceptions(String filePath) {
        if (germanStemExceptions == null) {
            synchronized (RessourceLoading.class) {
                try {
                    if (germanStemExceptions == null) {
                        germanStemExceptions = loadFile(filePath);
                    }
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }
        }
        return germanStemExceptions;
    }

    public static SynonymMap getGermanSynonyms(String filePath) {
        if (germanSynonyms == null) {
            synchronized (RessourceLoading.class) {
                try {
                    if (germanSynonyms == null) {
                        germanSynonyms = loadFile(filePath);
                    }
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }
        }
        return germanSynonyms;
    }

    private static SynonymMap loadFile(String filePath) throws IOException, ParseException {
        SolrSynonymParser solrSynonymParser = new SolrSynonymParser(true, true, new StandardAnalyzer());
        solrSynonymParser.parse(new BufferedReader(new FileReader(new File(filePath))));
        return solrSynonymParser.build();
    }
}
