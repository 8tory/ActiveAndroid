package com.activeandroid.util;

import android.database.Cursor;

import com.novoda.notils.cursor.CursorMarshaller;
import com.novoda.notils.cursor.SimpleCursorList;

public class SmartCursorList<T> extends SimpleCursorList<T> {

    public SmartCursorList(Cursor cursor, CursorMarshaller<T> marshaller) {
        super(cursor, marshaller);
    }

    @Override
    protected void finalize() throws Throwable {
        if (!isClosed()) {
            close();
        }
        super.finalize();
    }
}
