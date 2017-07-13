/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.properties;

import org.elasticsearch.common.xcontent.XContentBuilder;
import sirius.kernel.commons.Value;
import sirius.kernel.di.Injector;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.search.Entity;
import sirius.search.EntityDescriptor;
import sirius.search.IndexAccess;
import sirius.search.annotations.IndexMode;
import sirius.search.annotations.Indexed;
import sirius.search.annotations.NotNull;
import sirius.search.annotations.Transient;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A property describes how a field is persisted into the database and loaded back.
 * <p>
 * It also takes care of creating an appropriate mapping. Properties are generated by {@link PropertyFactory}
 * instances which generate a property for a given field of a class.
 * <p>
 * Field-specific options are investigated in the following order:
 * <ul>
 * <li>subclasses have overridden the <tt>isXXX()</tt> method</li>
 * <li>fields have the value set in the annotation (!= {@link ESOption#DEFAULT})</li>
 * <li>subclasses have overridden the <tt>isDefaultXXX()</tt> method</li>
 * <li>the default values provided in this class' <tt>isDefaultXXX()</tt> methods</li>
 * </ul>
 */
public abstract class Property {

    /**
     * Contains the underlying field for which the property was created
     */
    private final Field field;

    /**
     * Determines if <tt>null</tt> is accepted as a value for this property
     */
    private final boolean nullAllowed;

    /**
     * Determines whether the property should be exclude from the _source field
     */
    private final boolean excludeFromSource;

    /**
     * Determines whether the property should be stored separately
     */
    private final ESOption stored;

    /**
     * Determines whether the property is indexed (e.g. searchable)
     */
    private final ESOption indexed;

    /**
     * Determines whether the property should be included into the _all field
     */
    protected final ESOption includeInAll;

    /**
     * Determines whether doc_values should be enabled for the property
     */
    protected final ESOption docValuesEnabled;

    /**
     * Determines whether the property is an inner property (e.g. {@link #indexed} and {@link #stored} should not be
     * written to the mapping)
     */
    private boolean innerProperty;

    /**
     * Generates a new property for the given field
     *
     * @param field the underlying field from which the property was created
     */
    protected Property(Field field) {
        this.field = field;
        this.field.setAccessible(true);

        this.nullAllowed =
                !field.getType().isPrimitive() && !field.isAnnotationPresent(NotNull.class) && isDefaultNullAllowed();

        this.excludeFromSource = field.isAnnotationPresent(IndexMode.class) ?
                                 field.getAnnotation(IndexMode.class).excludeFromSource() :
                                 isDefaultExcludeFromSource();

        this.indexed = readAnnotationValue(IndexMode.class, IndexMode::indexed, this::isDefaultIndexed);
        this.stored = readAnnotationValue(IndexMode.class, IndexMode::stored, this::isDefaultStored);
        this.includeInAll = readAnnotationValue(IndexMode.class, IndexMode::includeInAll, this::isDefaultIncludeInAll);
        this.docValuesEnabled =
                readAnnotationValue(IndexMode.class, IndexMode::docValues, this::isDefaultDocValuesEnabled);
    }

    /**
     * Initializes the property (field) of the given entity.
     *
     * @param entity the entity to initialize
     * @throws IllegalAccessException in case of an error when initializing the entity
     */
    public void init(Entity entity) throws IllegalAccessException {
        // Empty by default as this is optional
    }

    /**
     * Provides access to the underlying java field
     *
     * @return the field upon which the property was created
     */
    public Field getField() {
        return field;
    }

    /**
     * Returns a translated title for the property by resolving
     * <tt>SimpleClassName.fieldName</tt> via {@link sirius.kernel.nls.NLS}.
     *
     * @return the translated name or label for this property.
     */
    public String getFieldTitle() {
        return NLS.get(field.getDeclaringClass().getSimpleName() + "." + getName());
    }

    /**
     * Some properties auto-create a value and therefore no setter for the given field should be defined.
     *
     * @return <tt>false</tt> if no setter for this property should be present, <tt>true</tt> otherwise
     */
    public boolean acceptsSetter() {
        return true;
    }

    /**
     * Returns the name of the property
     *
     * @return the name of the property, which is normally just the field name
     */
    public String getName() {
        return field.getName();
    }

    public boolean isInnerProperty() {
        return innerProperty;
    }

    public void setInnerProperty(boolean innerProperty) {
        this.innerProperty = innerProperty;
    }

    /**
     * Determines if <tt>null</tt> values are accepted by this property.
     * <p>
     * Subclasses may override this method to ignore the value from the {@link NotNull} annotation.
     *
     * @return <tt>true</tt> if the property accepts <tt>null</tt> values, <tt>false</tt> otherwise
     */
    public boolean isNullAllowed() {
        return nullAllowed;
    }

    /**
     * Determines whether this property should be excluded from _source field.
     * <p>
     * Subclasses may override this method to ignore the value from the {@link IndexMode} annotation.
     *
     * @return <tt>true</tt> if the property should be excluded from the _source field, <tt>false</tt> otherwise.
     */
    public boolean isExcludeFromSource() {
        return excludeFromSource;
    }

    /**
     * Determines if this property is stored as separate field in its original form.
     * <p>
     * Subclasses may override this method to ignore the value from the {@link IndexMode} annotation.
     *
     * @return <tt>true</tt> if the raw values is stored as extra field in ElasticSearch, <tt>false</tt> otherwise
     */
    protected ESOption isStored() {
        return stored;
    }

    /**
     * Determines if this property is stored as separate field in its original form.
     * <p>
     * Subclasses may override this method to ignore the value from the {@link IndexMode} annotation.
     *
     * @return <tt>true</tt> if the raw value is stored as extra field in ElasticSearch, <tt>false</tt> otherwise
     */
    protected ESOption isIndexed() {
        return indexed;
    }

    /**
     * Determines if this value should not be included in the _all field.
     * <p>
     * Subclasses may override this method to ignore the value from the {@link IndexMode} annotation.
     *
     * @return <tt>true</tt> if the value should not be included in the all field, <tt>false</tt> otherwise.
     */
    protected ESOption isIncludeInAll() {
        return includeInAll;
    }

    /**
     * Determines whether <tt>doc_values</tt> should be enabled for this property.
     * <p>
     * Subclasses may override this method to ignore the value from the {@link IndexMode} annotation.
     *
     * @return <tt>true</tt> if <tt>doc_values</tt> should be enabled for this property, <tt>false</tt> otherwise.
     */
    public ESOption isDocValuesEnabled() {
        return docValuesEnabled;
    }

    /**
     * Determines if <tt>null</tt> values are accepted by this property by default (if the {@link NotNull} annotation is
     * not present).
     * <p>
     * Subclasses may override this method to set the default value for their specific property type.
     *
     * @return <tt>true</tt> if the property accepts <tt>null</tt> values by default, <tt>false</tt> otherwise
     */
    public boolean isDefaultNullAllowed() {
        return true;
    }

    /**
     * Determines whether this property should be excluded from _source field by default (if the {@link IndexMode}
     * annotation is not present).
     * <p>
     * Subclasses may override this method to set the default value for their specific property type.
     *
     * @return <tt>true</tt> if the property should be excluded from the _source field, <tt>false</tt> otherwise.
     */
    public boolean isDefaultExcludeFromSource() {
        return false;
    }

    /**
     * Determines if this property is stored as separate field in its original form by default (if the {@link IndexMode}
     * annotation is not present).
     * <p>
     * Subclasses may override this method to set the default value for their specific property type.
     *
     * @return <tt>true</tt> if the raw values is stored as extra field in ElasticSearch, <tt>false</tt> otherwise
     */
    protected ESOption isDefaultStored() {
        return ESOption.FALSE;
    }

    /**
     * Determines if this property is stored as separate field in its original form by default (if the {@link IndexMode}
     * annotation is not present).
     * <p>
     * Subclasses may override this method to set the default value for their specific property type.
     *
     * @return <tt>true</tt> if the raw values is stored as extra field in ElasticSearch, <tt>false</tt> otherwise
     */
    protected ESOption isDefaultIndexed() {
        return ESOption.TRUE;
    }

    /**
     * Determines whether <tt>norms</tt> should be enabled for this property.
     * <p>
     * Subclasses may override this method to set the default value for their specific property type.
     *
     * @return <tt>true</tt> if the value should not be included in the all field, <tt>false</tt> otherwise.
     */
    protected ESOption isDefaultIncludeInAll() {
        return ESOption.FALSE;
    }

    /**
     * Determines whether <tt>doc_values</tt> should be enabled for this property.
     * <p>
     * Subclasses may override this method to set the default value for their specific property type.
     *
     * @return <tt>true</tt> if <tt>doc_values</tt> should be enabled for this property, <tt>false</tt> otherwise.
     */
    protected ESOption isDefaultDocValuesEnabled() {
        // disable doc_values in not indexed properties by default
        if (isIndexed() == ESOption.FALSE) {
            return ESOption.FALSE;
        }
        return ESOption.ES_DEFAULT;
    }

    /**
     * Returns the data type used in the mapping
     *
     * @return the name of the data type used in the mapping
     */
    protected String getMappingType() {
        return "keyword";
    }

    protected void addMappingProperties(XContentBuilder builder) throws IOException {
        builder.field("type", getMappingType());
        if (!isInnerProperty()) {
            if (isStored() != ESOption.ES_DEFAULT) {
                builder.field("store", isStored());
            }

            if (isIndexed() != ESOption.ES_DEFAULT) {
                builder.field("index", isIndexed());
            }
        }
        if (isIncludeInAll() != ESOption.ES_DEFAULT) {
            builder.field("include_in_all", isIncludeInAll());
        }
        if (isDocValuesEnabled() != ESOption.ES_DEFAULT) {
            builder.field("doc_values", isDocValuesEnabled());
        }
    }

    /**
     * Generates the representation of the entities field value to be stored in the database.
     *
     * @param entity the entity which field value is to be stored
     * @return the storable representation of the value
     */
    public Object writeToSource(Entity entity) {
        try {
            return transformToSource(field.get(entity));
        } catch (IllegalAccessException e) {
            Exceptions.handle(IndexAccess.LOG, e);
            return null;
        }
    }

    /**
     * Transforms the given field value to the representation which is stored in the database.
     *
     * @param o the value to transform
     * @return the storable representation of the value
     */
    protected Object transformToSource(Object o) {
        return o;
    }

    /**
     * Converts the given value back to its original form and stores it as the given entities field value.
     *
     * @param entity the entity to update
     * @param value  the stored value from the database
     */
    public void readFromSource(Entity entity, Object value) {
        try {
            Object val = transformFromSource(value);
            field.set(entity, val);
            entity.setSource(field.getName(), val);
        } catch (IllegalAccessException e) {
            Exceptions.handle(IndexAccess.LOG, e);
        }
    }

    /**
     * Transforms the given field value from the representation which is stored in the database.
     *
     * @param value the value to transform
     * @return the original representation of the value
     */
    protected Object transformFromSource(Object value) {
        return value;
    }

    /**
     * Generates the mapping used by this property
     *
     * @param builder the builder used to generate JSON
     * @throws IOException in case of an io error while generating the mapping
     */
    public final void createMapping(XContentBuilder builder) throws IOException {
        builder.startObject(getName());
        addMappingProperties(builder);
        builder.endObject();
    }

    /**
     * Permits to create an dynamic mapping templates.
     *
     * @param builder the builder used to generate JSON
     * @throws IOException in case of an io error while generating the mapping
     */
    public void createDynamicTemplates(XContentBuilder builder) throws IOException {
        // Empty by default as this is optional
    }

    /**
     * Returns and converts the field value from the given request and writes it into the given entity.
     *
     * @param entity the entity to update
     * @param ctx    the request to read the data from
     */
    public void readFromRequest(Entity entity, WebContext ctx) {
        try {
            if (ctx.get(getName()).isNull()) {
                return;
            }
            field.set(entity, transformFromRequest(getName(), ctx));
        } catch (IllegalAccessException e) {
            Exceptions.handle(IndexAccess.LOG, e);
        }
    }

    /**
     * Extracts and converts the value from the given request.
     *
     * @param name name of the parameter to read
     * @param ctx  the request to read the data from
     * @return the converted value which can be assigned to the field
     */
    protected Object transformFromRequest(String name, WebContext ctx) {
        Value value = ctx.get(name);
        if (value.isEmptyString() && !field.getType().isPrimitive()) {
            return null;
        }
        Object result = value.coerce(field.getType(), null);
        if (result == null) {
            UserContext.setFieldError(name, value.get());
            throw Exceptions.createHandled()
                            .withNLSKey("Property.invalidInput")
                            .set("field", NLS.get(field.getDeclaringClass().getSimpleName() + "." + name))
                            .set("value", value.asString())
                            .handle();
        }

        return result;
    }

    protected <T extends Annotation> ESOption readAnnotationValue(Class<T> annotation,
                                                                  Function<T, ESOption> annotationField,
                                                                  Supplier<ESOption> defaultSupplier) {
        if (!field.isAnnotationPresent(annotation)) {
            return defaultSupplier.get();
        }
        ESOption value = annotationField.apply(field.getAnnotation(annotation));
        return value == ESOption.DEFAULT ? defaultSupplier.get() : value;
    }

    /**
     * Creates mappings for inner fields in "nested" or "object" types
     *
     * @param builder     output
     * @param nestedClass model class that is used as nested object
     * @throws IOException
     */
    protected void addNestedMappingProperties(XContentBuilder builder, Class<?> nestedClass) throws IOException {
        if (nestedClass.isAnnotationPresent(Indexed.class)) {
            for (Property property : new EntityDescriptor(nestedClass).getProperties()) {
                builder.startObject(property.getName());
                property.addMappingProperties(builder);
                builder.endObject();
            }
        } else {
            for (Field innerField : nestedClass.getDeclaredFields()) {
                if (!innerField.isAnnotationPresent(Transient.class) && !Modifier.isStatic(innerField.getModifiers())) {
                    for (PropertyFactory f : Injector.context().getPartCollection(PropertyFactory.class)) {
                        if (f.accepts(innerField)) {
                            Property p = f.create(innerField);
                            p.setInnerProperty(true);
                            p.createMapping(builder);
                            break;
                        }
                    }
                    IndexAccess.LOG.WARN("Cannot create property %s in type %s",
                                         innerField.getName(),
                                         innerField.getDeclaringClass().getSimpleName());
                }
            }
        }
    }
}
