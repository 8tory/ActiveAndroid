package com.activeandroid.util;

import android.database.Cursor;

import com.activeandroid.Cache;
import com.activeandroid.Model;
import com.novoda.notils.cursor.CursorMarshaller;

public class ModelCursorMarshaller<T> implements CursorMarshaller<T> {
    private Class<? extends Model> type;

    public ModelCursorMarshaller(Class<? extends Model> type) {
        super();
        this.type = type;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T marshall(Cursor cursor) {
        Model entity = null;

        try {
            entity = Cache.getEntity(type, cursor.getLong(cursor.getColumnIndex("Id")));
        } catch (Exception e) {
            //e.printStackTrace(); // Disable this if noise
        }
        try {
            if (entity == null) {
                entity = type.newInstance();
                //android.util.Log.d("ModelCursorMarshaller", "" + type);
                entity.loadFromCursor(cursor);
                //android.util.Log.d("ModelCursorMarshaller", "" + entity);
            }
        } catch (Exception e) {
            Log.e("Failed to process cursor.", e);
            //android.util.Log.e("ModelCursorMarshaller", "" + e);
        }

        return (T) entity;
    }
}