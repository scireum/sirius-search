/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.nlp.attribute;

import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;
import sirius.nlp.util.CharArray;

import java.util.Arrays;

public class PrimaryWordAttributeImpl extends AttributeImpl implements PrimaryWordAttribute {
    private char[] primaryWordToken = new char[16];
    private int primaryWordTokenLength = 0;

    private char[] originalToken = new char[16];
    private int originalTokenLength = 0;

    private boolean primaryWordTokenHasBeenEmitted = false;

    @Override
    public void clear() {
        primaryWordTokenLength = 0;
        originalTokenLength = 0;
        setPrimaryWordTokenEmitted(false);
    }

    /**
     * We just want to create a new array if the length differs.
     */
    @Override
    public void copyTo(AttributeImpl input) {
        PrimaryWordAttributeImpl copyAttributeImpl = (PrimaryWordAttributeImpl) input;
        if (copyAttributeImpl.primaryWordToken.length < primaryWordTokenLength) {
            copyAttributeImpl.primaryWordToken = new char[primaryWordTokenLength];
        }
        System.arraycopy(primaryWordToken, 0, copyAttributeImpl.primaryWordToken, 0, primaryWordTokenLength);

        if (copyAttributeImpl.originalToken.length < originalTokenLength) {
            copyAttributeImpl.originalToken = new char[originalTokenLength];
        }
        System.arraycopy(originalToken, 0, copyAttributeImpl.originalToken, 0, originalTokenLength);

        copyAttributeImpl.primaryWordTokenLength = primaryWordTokenLength;
        copyAttributeImpl.originalTokenLength = originalTokenLength;
        copyAttributeImpl.primaryWordTokenHasBeenEmitted = primaryWordTokenHasBeenEmitted;
    }

    @Override
    public void setOriginalToken(char[] buffer, int length) {
        originalToken = CharArray.assureArrayLength(originalToken, length);
        System.arraycopy(buffer, 0, originalToken, 0, length);
        originalTokenLength = length;
    }

    @Override
    public void setPrimaryWordToken(char[] buffer, int length) {
        primaryWordToken = CharArray.assureArrayLength(primaryWordToken, length);
        System.arraycopy(buffer, 0, primaryWordToken, 0, length);
        primaryWordTokenLength = length;
    }

    @Override
    public char[] getOriginalToken() {
        return originalToken;
    }

    @Override
    public int getOriginalTokenLength() {
        return originalTokenLength;
    }

    public char[] getPrimaryWordToken() {
        return primaryWordToken;
    }

    @Override
    public int getPrimaryWordTokenLength() {
        return primaryWordTokenLength;
    }

    @Override
    public boolean isPrimaryWordTokenEmitted() {
        return primaryWordTokenHasBeenEmitted;
    }

    @Override
    public void setPrimaryWordTokenEmitted(boolean primaryWordTokenEmitted) {
        this.primaryWordTokenHasBeenEmitted = primaryWordTokenEmitted;
    }

    @Override
    public String toString() {
        return "PrimaryWordAttributeImpl [primaryWordToken="
               + Arrays.toString(primaryWordToken)
               + ", primaryWordTokenLength="
               + primaryWordTokenLength
               + ", originalToken="
               + Arrays.toString(originalToken)
               + ", originalTokenLength="
               + originalTokenLength
               + "]";
    }

    @Override
    public void reflectWith(AttributeReflector reflector) {
        reflector.reflect(PrimaryWordAttribute.class, "primaryWordToken", primaryWordToken);
        reflector.reflect(PrimaryWordAttribute.class, "primaryWordLength", primaryWordTokenLength);
        reflector.reflect(PrimaryWordAttribute.class, "originalToken", originalToken);
        reflector.reflect(PrimaryWordAttribute.class, "originalTokenLength", originalTokenLength);
    }
}