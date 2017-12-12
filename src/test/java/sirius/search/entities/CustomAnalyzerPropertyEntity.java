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
public class CustomAnalyzerPropertyEntity extends Entity {

    public static final String PREFIXES_CONTENT = "prefixesContent";
    @Analyzed(analyzer = "autocomplete", indexOptions = Analyzed.INDEX_OPTION_POSITIONS)
    @IndexMode(excludeFromSource = true, includeInAll = ESOption.FALSE)
    private String prefixesContent;

    public String getPrefixesContent() {
        return prefixesContent;
    }

    public void setPrefixesContent(String prefixesContent) {
        this.prefixesContent = prefixesContent;
    }
}
