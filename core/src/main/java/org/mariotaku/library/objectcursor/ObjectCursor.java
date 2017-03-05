/*
 *                 Twidere - Twitter client for Android
 *
 *  Copyright (C) 2012-2015 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.library.objectcursor;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.util.SparseArray;

import java.io.Closeable;
import java.io.IOException;
import java.util.AbstractList;

/**
 * Created by mariotaku on 15/7/5.
 */
public class ObjectCursor<E> extends AbstractList<E> implements Closeable {

    private final Cursor mCursor;
    private final CursorIndices<E> mIndices;
    private final SparseArray<E> mCache;

    public ObjectCursor(Cursor cursor, CursorIndices<E> indies) {
        mCursor = cursor;
        mIndices = indies;
        mCache = new SparseArray<>();
    }

    @Override
    public E get(final int location) {
        ensureCursor();
        synchronized (this) {
            final int idxOfCache = mCache.indexOfKey(location);
            if (idxOfCache >= 0) return mCache.valueAt(idxOfCache);
            if (mCursor.moveToPosition(location)) {
                final E object;
                try {
                    object = get(mCursor, mIndices);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                mCache.put(location, object);
                return object;
            }
            throw new CursorIndexOutOfBoundsException("length=" + mCursor.getCount() + "; index=" + location);
        }
    }

    private void ensureCursor() {
        synchronized (this) {
            if (mCursor.isClosed()) throw new IllegalStateException("Cursor is closed");
        }
    }

    protected E get(final Cursor cursor, final CursorIndices<E> indices) throws IOException {
        return indices.newObject(cursor);
    }

    @Override
    public int size() {
        synchronized (this) {
            return mCursor.getCount();
        }
    }

    @Override
    public E set(int index, E element) {
        mCache.put(index, element);
        return element;
    }

    public boolean isClosed() {
        synchronized (this) {
            return mCursor.isClosed();
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            mCursor.close();
        }
    }

    public CursorIndices<E> getIndices() {
        return mIndices;
    }

    public Cursor getCursor() {
        ensureCursor();
        synchronized (this) {
            return mCursor;
        }
    }

    public static <T> CursorIndices<T> indicesFrom(Cursor cursor, Class<T> cls) {
        try {
            //noinspection unchecked
            Class<CursorIndices<T>> indicesClass = (Class<CursorIndices<T>>)
                    Class.forName(cls.getName() + CursorIndices.CURSOR_INDICES_SUFFIX);
            return indicesClass.getConstructor(Cursor.class).newInstance(cursor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> ValuesCreator<T> valuesCreatorFrom(Class<T> cls) {
        try {
            Class<?> creatorClass = Class.forName(cls.getName() + ValuesCreator.VALUES_CREATOR_SUFFIX);
            //noinspection unchecked
            return (ValuesCreator<T>) creatorClass.getDeclaredField("INSTANCE").get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public interface CursorIndices<T> {

        String CURSOR_INDICES_SUFFIX = "CursorIndices";

        T newObject(Cursor cursor) throws IOException;

        void parseFields(T instance, Cursor cursor) throws IOException;

        void callBeforeCreated(T instance) throws IOException;

        void callAfterCreated(T instance) throws IOException;

        int get(String columnName);

    }

    public interface ValuesCreator<T> {

        String VALUES_CREATOR_SUFFIX = "ValuesCreator";

        void writeTo(T instance, ContentValues values) throws IOException;

        ContentValues create(T instance) throws IOException;

    }

}
