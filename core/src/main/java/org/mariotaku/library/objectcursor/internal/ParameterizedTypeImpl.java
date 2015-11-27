package org.mariotaku.library.objectcursor.internal;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Created by mariotaku on 15/11/27.
 */
public class ParameterizedTypeImpl implements ParameterizedType {
    private final Class<?> raw;
    private final Type useOwner;
    private final Type[] typeArguments;

    /**
     * Constructor
     *
     * @param raw           type
     * @param useOwner      owner type to use, if any
     * @param typeArguments formal type arguments
     */
    public ParameterizedTypeImpl(final Class<?> raw, final Type useOwner, final Type[] typeArguments) {
        this.raw = raw;
        this.useOwner = useOwner;
        this.typeArguments = typeArguments;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Type getRawType() {
        return raw;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Type getOwnerType() {
        return useOwner;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Type[] getActualTypeArguments() {
        return typeArguments.clone();
    }

    public static ParameterizedType get(Class rawType, Class useOwner, Class... typeArguments) {
        return new ParameterizedTypeImpl(rawType, useOwner, typeArguments);
    }

}
