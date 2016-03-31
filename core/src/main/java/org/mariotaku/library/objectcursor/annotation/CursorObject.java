package org.mariotaku.library.objectcursor.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by mariotaku on 15/11/27.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface CursorObject {

    boolean cursorIndices() default true;

    boolean valuesCreator() default false;

    boolean tableInfo() default false;

}
