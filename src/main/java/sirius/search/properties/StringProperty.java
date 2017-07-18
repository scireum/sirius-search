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
import sirius.search.annotations.IndexMode;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Contains a string property. If the field wears an {@link sirius.search.annotations.IndexMode} annotation, the
 * contents of the field will be analyzed and tokenized by ElasticSearch.
 */
public class StringProperty extends Property {

    /**
     * Determines whether this property should be analyzed
     *
     * @see Analyzed
     */
    private final boolean analyzed;

    /**
     * Determines the analyzer for this property
     *
     * @see Analyzed#analyzer()
     */
    private final String analyzer;

    /**
     * Determines the index_options for this property
     *
     * @see Analyzed#indexOptions()
     */
    private final String indexOptions;

    /**
     * Determines whether norms should be enabled for the property
     *
     * @see IndexMode#normsEnabled()
     */
    private final ESOption normsEnabled;

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
        this.normsEnabled = readAnnotationValue(IndexMode.class, IndexMode::normsEnabled, this::isDefaultNormsEnabled);
    }

    public boolean isAnalyzed() {
        return analyzed;
    }

    public String getAnalyzer() {
        return analyzer;
    }

    protected String getIndexOptions() {
        return indexOptions;
    }

    /**
     * Determines whether <tt>norms</tt> should be enabled for this property.
     * <p>
     * Subclasses may override this method to ignore the value from the {@link IndexMode} annotation.
     *
     * @return <tt>true</tt> if <tt>norms</tt> should be enabled for this property, <tt>false</tt> otherwise.
     */
    public ESOption isNormsEnabled() {
        return normsEnabled;
    }

    /**
     * Analyzed string fields do not allow the option "doc_values"
     *
     * @return <tt>true</tt> if <tt>doc_values</tt> should be enabled for this property, <tt>false</tt> otherwise.
     */
    @Override
    public ESOption isDocValuesEnabled() {
        return analyzed ? ESOption.ES_DEFAULT : super.isDocValuesEnabled();
    }

    @Override
    protected ESOption isDefaultIncludeInAll() {
        return ESOption.TRUE;
    }

    /**
     * Determines whether <tt>norms</tt> should be enabled for this property.
     * <p>
     * Subclasses may override this method to set the default value for their specific property type.
     *
     * @return <tt>true</tt> if <tt>norms</tt> should be enabled for this property, <tt>false</tt> otherwise.
     */
    protected ESOption isDefaultNormsEnabled() {
        return ESOption.FALSE;
    }

    @Override
    protected String getMappingType() {
        return analyzed ? "text" : "keyword";
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
        if (isNormsEnabled() != ESOption.ES_DEFAULT) {
            builder.field("norms", isNormsEnabled());
        }
    }
}
