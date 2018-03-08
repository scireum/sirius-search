/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.nlp.tokenstream;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.IOException;

public final class SingleTermTokenStream extends TokenStream {

    private final CharTermAttribute termAttr = addAttribute(CharTermAttribute.class);

    private CharTermAttribute token;

    public SingleTermTokenStream(CharTermAttribute token) {
        this.token = token;
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (token != null) {
            termAttr.copyBuffer(token.buffer(), 0, token.length()); // TODO needed? termAttr is already filled in *most* cases
            token = null;
            return true;
        }

        return false;
    }
}
