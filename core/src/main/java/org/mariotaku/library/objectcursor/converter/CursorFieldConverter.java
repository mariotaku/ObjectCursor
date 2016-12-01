package org.mariotaku.library.objectcursor.converter;

import android.content.ContentValues;
import android.database.Cursor;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;

/**
 * Created by mariotaku on 15/11/27.
 */
public interface CursorFieldConverter<T> {

    T parseField(Cursor cursor, int columnIndex, ParameterizedType fieldType) throws IOException;

    void writeField(ContentValues values, T object, String columnName, ParameterizedType fieldType) throws IOException;

}
