/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.entities;

import sirius.search.Entity;
import sirius.search.annotations.Analyzed;
import sirius.search.annotations.IndexMode;
import sirius.search.annotations.Indexed;
import sirius.search.properties.ESOption;

@Indexed(index = "test")
public class QueryEntity extends Entity {

    public static final String CONTENT = "content";

    @Analyzed(analyzer = Analyzed.ANALYZER_WHITESPACE)
    @IndexMode(normsEnabled = ESOption.TRUE)
    private String content;

    public static final String RANKING = "ranking";
    private long ranking;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getRanking() {
        return ranking;
    }

    public void setRanking(long ranking) {
        this.ranking = ranking;
    }
}
