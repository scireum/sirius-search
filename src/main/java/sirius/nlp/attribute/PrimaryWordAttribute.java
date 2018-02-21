/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.nlp.attribute;

import org.apache.lucene.util.Attribute;

public interface PrimaryWordAttribute extends Attribute {

    public void setOriginalToken(char[] buffer, int length);

    public char[] getOriginalToken();

    public int getOriginalTokenLength();

    public void setPrimaryWordToken(char[] buffer, int length);

    public char[] getPrimaryWordToken();

    public int getPrimaryWordTokenLength();

    boolean isPrimaryWordTokenEmitted();

    void setPrimaryWordTokenEmitted(boolean primaryWordTokenEmitted);

    void clear();

}
