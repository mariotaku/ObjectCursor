package org.mariotaku.objectcursor.processor;

/**
 * Created by mariotaku on 16/3/31.
 */
public class UnsupportedFieldTypeException extends IllegalArgumentException {
    public UnsupportedFieldTypeException() {
        super();
    }

    public UnsupportedFieldTypeException(String s) {
        super(s);
    }

    public UnsupportedFieldTypeException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsupportedFieldTypeException(Throwable cause) {
        super(cause);
    }
}
