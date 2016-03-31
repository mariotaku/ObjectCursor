package org.mariotaku.objectcursor.processor;

/**
 * Created by mariotaku on 16/3/31.
 */
public class DuplicateColumnException extends IllegalArgumentException {
    public DuplicateColumnException() {
        super();
    }

    public DuplicateColumnException(String s) {
        super(s);
    }

    public DuplicateColumnException(String message, Throwable cause) {
        super(message, cause);
    }

    public DuplicateColumnException(Throwable cause) {
        super(cause);
    }
}
