/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search;

import sirius.search.annotations.IndexMode;
import sirius.search.annotations.Indexed;

@Indexed(index = "test")
public class QueryEntity extends Entity{

    public static final String CONTENT = "content";
    @IndexMode(indexMode = IndexMode.MODE_ANALYZED,
            analyzer = IndexMode.ANALYZER_WHITESPACE,
            normEnabled = IndexMode.NORMS_DISABLED)
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
