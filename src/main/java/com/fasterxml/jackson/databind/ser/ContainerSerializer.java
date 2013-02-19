package com.fasterxml.jackson.databind.ser;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdDelegatingSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;

/**
 * Intermediate base class for serializers used for serializing
 * types that contain element(s) of other types, such as arrays,
 * {@link java.util.Collection}s (<code>Lists</code>, <code>Sets</code>
 * etc) and {@link java.util.Map}s and iterable things
 * ({@link java.util.Iterator}s).
 */
public abstract class ContainerSerializer<T>
    extends StdSerializer<T>
{
    /*
    /**********************************************************
    /* Construction, initialization
    /**********************************************************
     */

    protected ContainerSerializer(Class<T> t) {
        super(t);
    }
    
    /**
     * Alternate constructor that is (alas!) needed to work
     * around kinks of generic type handling
     * 
     * @param t
     */
    protected ContainerSerializer(Class<?> t, boolean dummy) {
        super(t, dummy);
    }

    protected ContainerSerializer(ContainerSerializer<?> src) {
        super(src._handledType, false);
    }
    
    /**
     * Factory(-like) method that can be used to construct a new container
     * serializer that uses specified {@link TypeSerializer} for decorating
     * contained values with additional type information.
     * 
     * @param vts Type serializer to use for contained values; can be null,
     *    in which case 'this' serializer is returned as is
     * @return Serializer instance that uses given type serializer for values if
     *    that is possible (or if not, just 'this' serializer)
     */
    public ContainerSerializer<?> withValueTypeSerializer(TypeSerializer vts) {
        if (vts == null) return this;
        return _withValueTypeSerializer(vts);
    }

    /*
    /**********************************************************
    /* Extended API
    /**********************************************************
     */

    /**
     * Accessor for finding declared (static) element type for
     * type this serializer is used for.
     */
    public abstract JavaType getContentType();

    /**
     * Accessor for serializer used for serializing contents
     * (List and array elements, Map values etc) of the
     * container for which this serializer is used, if it is
     * known statically.
     * Note that for dynamic types this may return null; if so,
     * caller has to instead use {@link #getContentType()} and
     * {@link com.fasterxml.jackson.databind.SerializerProvider#findValueSerializer}.
     */
    public abstract JsonSerializer<?> getContentSerializer();
    
    /*
    /**********************************************************
    /* Abstract methods for sub-classes to implement
    /**********************************************************
     */
    
    /* Overridden as abstract, to force re-implementation; necessary for all
     * collection types.
     */
    @Override
    public abstract boolean isEmpty(T value);

    /**
     * Method called to determine if the given value (of type handled by
     * this serializer) contains exactly one element.
     *<p>
     * Note: although it might seem sensible to instead define something
     * like "getElementCount()" method, this would not work well for
     * containers that do not keep track of size (like linked lists may
     * not).
     */
    public abstract boolean hasSingleElement(T value);
    
    /**
     * Method that needs to be implemented to allow construction of a new
     * serializer object with given {@link TypeSerializer}, used when
     * addition type information is to be embedded.
     */
    protected abstract ContainerSerializer<?> _withValueTypeSerializer(TypeSerializer vts);

    /*
    /**********************************************************
    /* Helper methods for sub-types
    /**********************************************************
     */

    /**
     * Helper method used to encapsulate logic for determining whether there is
     * a property annotation that overrides element type; if so, we can
     * and need to statically find the serializer.
     * 
     * @since 2.1
     */
    protected boolean hasContentTypeAnnotation(SerializerProvider provider,
            BeanProperty property)
    {
        if (property != null) {
            AnnotationIntrospector intr = provider.getAnnotationIntrospector();
            if (intr != null) {
                if (intr.findSerializationContentType(property.getMember(), property.getType()) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Helper method that can be used to see if specified property has annotation
     * indicating that a converter is to be used for contained values.
     * 
     * @param existingSerializer (optional) configured content
     *    serializer if one already exists.
     * 
     * @since 2.2
     */
    protected JsonSerializer<?> findConvertingContentSerializer(SerializerProvider provider,
            BeanProperty prop, JsonSerializer<?> existingSerializer)
        throws JsonMappingException
    {
        final AnnotationIntrospector intr = provider.getAnnotationIntrospector();
        if (intr != null && prop != null) {
            Object convDef = intr.findSerializationContentConverter(prop.getMember());
            if (convDef != null) {
                Converter<Object,Object> conv = provider.converterInstance(prop.getMember(), convDef);
                TypeFactory tf = provider.getTypeFactory();
                JavaType converterType = tf.constructType(conv.getClass());
                JavaType[] params = tf.findTypeParameters(converterType, Converter.class);
                if (params == null || params.length != 2) {
                    throw new JsonMappingException("Could not determine Converter parameterization for "
                            +converterType);
                }
                JavaType delegateType = params[1];
                if (existingSerializer == null) {
                    existingSerializer = provider.findValueSerializer(delegateType, prop);
                }
                return new StdDelegatingSerializer(conv, delegateType, existingSerializer);
            }
        }
        return existingSerializer;
    }
}
