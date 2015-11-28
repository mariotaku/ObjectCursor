package org.mariotaku.library.objectcursor.converter;

import android.content.ContentValues;
import android.database.Cursor;

import java.lang.reflect.ParameterizedType;

/**
 * Created by mariotaku on 15/11/27.
 */
public class EmptyCursorFieldConverter implements CursorFieldConverter {

    @Override
    public Object parseField(Cursor cursor, int columnIndex, ParameterizedType fieldType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeField(ContentValues values, Object object, String columnName, ParameterizedType fieldType) {
        throw new UnsupportedOperationException();
    }
}
