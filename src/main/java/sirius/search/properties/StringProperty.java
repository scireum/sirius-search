/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import org.elasticsearch.common.xcontent.XContentBuilder;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.search.annotations.Analyzed;

import java.io.IOException;
import java.lang.reflect.Field;

import static sirius.search.properties.ESOption.ES_DEFAULT;
import static sirius.search.properties.ESOption.TRUE;

/**
 * Contains a string property. If the field wears an {@link sirius.search.annotations.IndexMode} annotation, the
 * contents of the field will be analyzed and tokenized by ElasticSearch.
 */
public class StringProperty extends Property {

    private final String indexOptions;
    private final boolean analyzed;
    private final String analyzer;

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(Field field) {
            return String.class.equals(field.getType());
        }

        @Override
        public Property create(Field field) {
            return new StringProperty(field);
        }
    }

    /*
     * Instances are only created by the factory or by subclasses
     */
    protected StringProperty(Field field) {
        super(field);

        this.analyzed = field.isAnnotationPresent(Analyzed.class);
        this.analyzer = analyzed ? field.getAnnotation(Analyzed.class).analyzer() : "";
        this.indexOptions = analyzed ? field.getAnnotation(Analyzed.class).indexOptions() : "";
    }

    @Override
    protected String getMappingType() {
        return analyzed ? "text" : "keyword";
    }

    @Override
    protected ESOption isDefaultIncludeInAll() {
        return TRUE;
    }

    protected String getIndexOptions() {
        return indexOptions;
    }

    public boolean isAnalyzed() {
        return analyzed;
    }

    public String getAnalyzer() {
        return analyzer;
    }

    @Override
    public void addMappingProperties(XContentBuilder builder) throws IOException {
        super.addMappingProperties(builder);

        if (Strings.isFilled(getIndexOptions())) {
            builder.field("index_options", getIndexOptions());
        }
        if (isAnalyzed() && Strings.isFilled(getAnalyzer())) {
            builder.field("analyzer", getAnalyzer());
        }
        if (isNormsEnabled() != ES_DEFAULT) {
            builder.field("norms", isNormsEnabled());
        }
    }
}
