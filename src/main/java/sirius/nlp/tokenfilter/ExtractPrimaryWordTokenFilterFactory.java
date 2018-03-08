package sirius.nlp.tokenfilter;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.compound.HyphenationCompoundWordTokenFilter;
import org.apache.lucene.analysis.compound.hyphenation.HyphenationTree;
import org.apache.lucene.analysis.util.ResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoaderAware;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.apache.lucene.util.IOUtils;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class ExtractPrimaryWordTokenFilterFactory extends TokenFilterFactory implements ResourceLoaderAware {
    private CharArraySet dictionary;
    private HyphenationTree hyphenator;
    private final String dictFile;
    private final String hypFile;
    private final boolean onlyLongestMatch;

    /**
     * Creates a new HunspellStemFilterFactory
     *
     * @param args
     */
    public ExtractPrimaryWordTokenFilterFactory(Map<String, String> args) {
        super(args);
        dictFile = get(args, "dictionary");
        hypFile = require(args, "hyphenator");
        onlyLongestMatch = getBoolean(args, "onlyLongestMatch", false);
        if (!args.isEmpty()) {
            throw new IllegalArgumentException("Unknown parameters: " + args);
        }
    }

    @Override
    public void inform(ResourceLoader loader) throws IOException {
        InputStream stream = null;
        try {
            dictionary = getWordSet(loader, dictFile, false);
            stream = loader.openResource(hypFile);
            final InputSource is = new InputSource(stream);
            is.setSystemId(hypFile);
            hyphenator = HyphenationCompoundWordTokenFilter.getHyphenationTree(is);
        } finally {
            IOUtils.closeWhileHandlingException(stream);
        }
    }

    @Override
    public TokenFilter create(TokenStream tokenStream) {
        return new ExtractPrimaryWordTokenFilter(tokenStream, hyphenator, dictionary, onlyLongestMatch);
    }
}