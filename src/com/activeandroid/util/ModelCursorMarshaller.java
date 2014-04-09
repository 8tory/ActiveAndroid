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
                int columnSize = entity.loadFromCursor(cursor);
                //android.util.Log.d("ModelCursorMarshaller", "" + entity);
                if (entity.getId() != null && columnSize == 0) {
                    Cache.addEntity(entity);
                }
            }
        }
        /*
		catch (NoSuchMethodException e) {
			throw new RuntimeException(
                "Your model " + type.getName() + " does not define a default " +
                "constructor. The default constructor is required for " +
                "now in ActiveAndroid models, as the process to " +
                "populate the ORM model is : " +
                "1. instantiate default model " +
                "2. populate fields"
            );
        }
        */
        catch (Exception e) {
            Log.e("Failed to process cursor.", e);
            //android.util.Log.e("ModelCursorMarshaller", "" + e);
        }

        return (T) entity;
    }
}
