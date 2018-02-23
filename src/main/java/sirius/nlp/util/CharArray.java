/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.nlp.util;

public class CharArray {
    public static char[] assureArrayLength(char[] array, int length) {
        if (array.length < length) {
            char[] newArray = new char[length];
            System.arraycopy(array, 0, newArray, 0, array.length);
            return newArray;
        } else {
            return array;
        }
    }

    public static boolean equals(char[] a, char[] a2, int length) {
        if (a == a2) {
            return true;
        }

        if (a == null || a2 == null) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (a[i] != a2[i]) {
                return false;
            }
        }

        return true;
    }

    public static boolean containsOnlyDigitsOrSpecialChar(char[] buffer, int length) {
        for (int i = 0; i < length; i++) {
            if (Character.isAlphabetic(buffer[i])) {
                return false;
            }
        }

        return true;
    }
}
