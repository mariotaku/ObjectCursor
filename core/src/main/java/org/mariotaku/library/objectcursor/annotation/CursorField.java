package org.mariotaku.library.objectcursor.annotation;

import org.mariotaku.library.objectcursor.converter.CursorFieldConverter;
import org.mariotaku.library.objectcursor.converter.EmptyCursorFieldConverter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by mariotaku on 15/11/27.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface CursorField {
    String value();

    String indexFieldName() default "";

    boolean excludeWrite() default false;

    Class<? extends CursorFieldConverter> converter() default EmptyCursorFieldConverter.class;

    String type() default AUTO;

    boolean useGetter() default true;

    boolean useSetter() default true;

    String AUTO = "[AUTO]";
    String TEXT = "TEXT";
    String TEXT_NOT_NULL = "TEXT NOT NULL";
    String INTEGER = "INTEGER";
    String FLOAT = "FLOAT";
    String BLOB = "BLOB";
}
