package org.mariotaku.library.objectcursor.converter;

import android.content.ContentValues;
import android.database.Cursor;

import java.lang.reflect.ParameterizedType;

/**
 * Created by mariotaku on 15/11/27.
 */
public interface CursorFieldConverter<T> {

    T parseField(Cursor cursor, int columnIndex, ParameterizedType fieldType);

    void writeField(ContentValues values, T object, String columnName, ParameterizedType fieldType);

}
