package sirius.nlp.tokenfilter;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.hunspell.HunspellStemFilterFactory;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.analysis.util.ClasspathResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoader;
import sirius.kernel.commons.Strings;
import sirius.nlp.tokenstream.SingleTermTokenStream;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class GermanStemmingTokenFilter extends TokenFilter {

    private final CharTermAttribute termAttr = addAttribute(CharTermAttribute.class);
    private final KeywordAttribute keyWordAttr = addAttribute(KeywordAttribute.class);

    private HunspellStemFilterFactory hunspellStemFactory;
    private ExtractPrimaryWordTokenFilterFactory extractPrimaryWordFactory;

    /**
     * Construct a token stream filtering the given input.
     *
     * @param input
     */
    public GermanStemmingTokenFilter(TokenStream input, String longestOnly, String ignoreCase) {
        super(input);
        ResourceLoader loader = new ClasspathResourceLoader(); // TODO filesystemloader

        Map<String, String> hunspellArgs = new HashMap<>();
        hunspellArgs.put("dictionary", "hunspell/de_DE_frami.dic"); // TODO config value
        hunspellArgs.put("affix", "hunspell/de_DE_frami.aff"); // TODO config value
        hunspellArgs.put("longestOnly", longestOnly);
        hunspellArgs.put("ignoreCase", ignoreCase);
        hunspellStemFactory = new HunspellStemFilterFactory(hunspellArgs);
        try {
            hunspellStemFactory.inform(loader);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Map<String, String> extractPrimaryWordArgs = new HashMap<>();
        extractPrimaryWordArgs.put("dictionary", "wordlist/de/wordlist.txt");
        extractPrimaryWordArgs.put("hyphenator", "hyph_de.xml");
        extractPrimaryWordArgs.put("onlyLongestMatch", "false");

        extractPrimaryWordFactory = new ExtractPrimaryWordTokenFilterFactory(extractPrimaryWordArgs);
        try {
            extractPrimaryWordFactory.inform(loader);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!input.incrementToken()) {
            return false;
        }

        if (!keyWordAttr.isKeyword()) {
            return stem();
        }

        return true;
    }

    private boolean stem() throws IOException {
        String tokenToStem = this.termAttr.toString();

        try (TokenStream hunspellStream = hunspellStemFactory.create(new SingleTermTokenStream(termAttr))) {
            final CharTermAttribute hunspellTermAttr = hunspellStream.addAttribute(CharTermAttribute.class);
            hunspellStream.reset();

            if (!hunspellStream.incrementToken()) {
                return false;
            }

            String stemmed = hunspellTermAttr.toString();

            if (Strings.equalIgnoreCase(stemmed, tokenToStem)) {
                // if the token didn't change during stemming, we try to extract the primary word and stem this term instead
                if (!tryStemPrimaryWord()) {
                    return false;
                }
            } else {
                // write the stem to the term attribute
                this.termAttr.copyBuffer(hunspellTermAttr.buffer(), 0, hunspellTermAttr.length());
            }

            hunspellStream.end();
        }

        return true;
    }

    private boolean tryStemPrimaryWord() throws IOException {
        try (TokenStream extractPrimaryWordStream = extractPrimaryWordFactory.create(new SingleTermTokenStream(termAttr))) {
            final CharTermAttribute primaryWordTermAttr =
                    extractPrimaryWordStream.addAttribute(CharTermAttribute.class);
            extractPrimaryWordStream.reset();
            if (!extractPrimaryWordStream.incrementToken()) {
                return false;
            }

            String primaryWord = primaryWordTermAttr.toString();

            if (Strings.isFilled(primaryWord)) {
                try (TokenStream hunspellStream = hunspellStemFactory.create(new SingleTermTokenStream(
                        primaryWordTermAttr))) {
                    final CharTermAttribute hunspelltermAttr = hunspellStream.addAttribute(CharTermAttribute.class);

                    hunspellStream.reset();
                    hunspellStream.incrementToken();

                    String stemmedPrimaryWord = hunspelltermAttr.toString();

                    if (!Strings.equalIgnoreCase(stemmedPrimaryWord, primaryWord)) {
                        // Success! We could stem the primary word, so we re-attach the stemmed primary word to the original token:
                        // e.g. for the term "Dampfschifffahrtskapitänsmützen" we can stem "mützen" -> "mütze" and can emit the term
                        // "Dampfschifffahrtskapitänsmütze"
                        char[] prefix = Arrays.copyOfRange(this.termAttr.buffer(),
                                                           0,
                                                           this.termAttr.length() - primaryWordTermAttr.length());
                        char[] stemmedPrimary =
                                Arrays.copyOfRange(hunspelltermAttr.buffer(), 0, hunspelltermAttr.length());
                        this.termAttr.setEmpty().append(new String(prefix)).append(new String(stemmedPrimary));
                    }

                    hunspellStream.end();
                }
            }

            extractPrimaryWordStream.end();
        }

        return true;
    }
}