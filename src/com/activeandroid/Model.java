package com.activeandroid;

/*
 * Copyright (C) 2010 Michael Pardo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;

import com.activeandroid.annotation.Column;
import com.activeandroid.content.ContentProvider;
import com.activeandroid.query.Delete;
import com.activeandroid.query.Select;
import com.activeandroid.serializer.TypeSerializer;
import com.activeandroid.util.Log;
import com.activeandroid.util.ReflectionUtils;
import com.activeandroid.util.SQLiteUtils;
import com.novoda.notils.cursor.CursorList;

import android.database.ContentObserver;
import android.net.Uri;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings("unchecked")
public abstract class Model implements com.novoda.notils.cursor.SimpleCursorList.MarshallerListener<Model> {
	public static final String FIELD_ID = "Id";
	public static final String ID = FIELD_ID;

	//////////////////////////////////////////////////////////////////////////////////////
	// PRIVATE MEMBERS
	//////////////////////////////////////////////////////////////////////////////////////

	@Column(name = FIELD_ID)
	private Long mId = null;

	private Long mSpecificId = null;

	private boolean mReplace = false;

	private TableInfo mTableInfo;

	private boolean enable = true;

	private ReentrantLock mLock;

	private boolean mDeleted = false;

	//////////////////////////////////////////////////////////////////////////////////////
	// CONSTRUCTORS
	//////////////////////////////////////////////////////////////////////////////////////

	public Model() {
		mTableInfo = Cache.getTableInfo(getClass());
	}

	//////////////////////////////////////////////////////////////////////////////////////
	// PUBLIC METHODS
	//////////////////////////////////////////////////////////////////////////////////////

	public final Long getId() {
		return mId;
	}

	public final Long getSpecificId() {
		return mSpecificId;
	}

	public final void setSpecificId(Long id, boolean replace) {
		mReplace = replace;
		mSpecificId = id;
	}

	public final void setSpecificId(Long id) {
		setSpecificId(id, false);
	}

	public final void setReplace(boolean replace) {
		mReplace = replace;
	}

	public final boolean isEnabled() {
		return enable;
	}

	public final void setEnabled(boolean enable) {
		this.enable = enable;
	}

	public final void enable() {
		setEnabled(true);
	}

	public final void disable() {
		setEnabled(false);
	}

	// super me: super.delete();
	public void delete() {
		//delete(mTableInfo.getType(), mId);
		SQLiteUtils.delete(mTableInfo.getTableName(), "Id=?", new String[] { getId().toString() });
		Cache.removeEntity(this);

		Cache.getContext().getContentResolver()
				.notifyChange(ContentProvider.createUri(mTableInfo.getType(), mId), null);
	}

	/**
	 * reset()
	 * insertable()
	 * setInsert()
	 * setInsertion()
	 * refill()
	 * reference()
	 * reflect()
	 * refer()
	 * reproduce()
	 * duplicate()
	 * direct()
	 * direct()
	 * autoIncrement()
	 */
	public final void autoIncrement() {
		setReplace(false);
		mId = null;
	}

	/** @deprecated */
	public final <T extends Model> CursorList<T> rawQuery(String sql, String[] selectionArgs) {
		return SQLiteUtils.rawQuery(mTableInfo.getType(), sql, selectionArgs);
	}

	/** @deprecated */
	public final <T extends Model> T rawQuerySingle(String sql, String[] selectionArgs) {
		return (T) SQLiteUtils.rawQuerySingle(mTableInfo.getType(), sql, selectionArgs);
	}

	@SuppressLint("NewApi")
	public ContentValues toContentValues() {
		final ContentValues values = new ContentValues();

		for (Field field : mTableInfo.getFields()) {
			final String fieldName = mTableInfo.getColumnName(field);
			Class<?> fieldType = field.getType();

			if (mTableInfo.isReadOnlyColumn(fieldName))
				continue;

			field.setAccessible(true);

			try {
				Object value = field.get(this);

				if (value != null) {
					final TypeSerializer typeSerializer = Cache.getParserForType(fieldType);
					if (typeSerializer != null) {
						// serialize data
						value = typeSerializer.serialize(value);
						// set new object type
						if (value != null) {
							fieldType = value.getClass();
							// check that the serializer returned what it promised
							if (!fieldType.equals(typeSerializer.getSerializedType())) {
								Log.w(String.format("TypeSerializer returned wrong type: expected a %s but got a %s",
										typeSerializer.getSerializedType(), fieldType));
							}
						}
					}
				}

				// TODO: Find a smarter way to do this? This if block is necessary because we
				// can't know the type until runtime.
				if (value == null) {
					values.putNull(fieldName);
				}
				else if (fieldType.equals(Byte.class) || fieldType.equals(byte.class)) {
					values.put(fieldName, (Byte) value);
				}
				else if (fieldType.equals(Short.class) || fieldType.equals(short.class)) {
					values.put(fieldName, (Short) value);
				}
				else if (fieldType.equals(Integer.class) || fieldType.equals(int.class)) {
					values.put(fieldName, (Integer) value);
				}
				else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) {
					values.put(fieldName, (Long) value);
				}
				else if (fieldType.equals(Float.class) || fieldType.equals(float.class)) {
					values.put(fieldName, (Float) value);
				}
				else if (fieldType.equals(Double.class) || fieldType.equals(double.class)) {
					values.put(fieldName, (Double) value);
				}
				else if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) {
					values.put(fieldName, (Boolean) value);
				}
				else if (fieldType.equals(Character.class) || fieldType.equals(char.class)) {
					values.put(fieldName, value.toString());
				}
				else if (fieldType.equals(String.class)) {
					values.put(fieldName, value.toString());
				}
				else if (fieldType.equals(Byte[].class) || fieldType.equals(byte[].class)) {
					values.put(fieldName, (byte[]) value);
				}
				else if (ReflectionUtils.isModel(fieldType)) {
					values.put(fieldName, ((Model) value).getId());
				}
				else if (ReflectionUtils.isSubclassOf(fieldType, Enum.class)) {
					values.put(fieldName, ((Enum<?>) value).name());
				}
			}
			catch (IllegalArgumentException e) {
				Log.e(e.getClass().getName(), e);
			}
			catch (IllegalAccessException e) {
				Log.e(e.getClass().getName(), e);
			}
		}

		return values;
	}

	// super me: super.save();
	@SuppressLint("NewApi")
	public Long save() {
		if (!enable) return mId;
		final ContentValues values = toContentValues();

		// TODO optimize the following code snippet
		if (mSpecificId != null && mReplace) { // replace
			mId = mSpecificId;
			values.put("Id", mId);
			if (!ActiveAndroid.inContentProvider()) {
				SQLiteUtils.replace(mTableInfo.getTableName(), null, values);
			} else {
				Model m = load(mTableInfo.getType(), mId);
				if (m == null) {
					Cache.getContext().getContentResolver().insert(ContentProvider.createUri(mTableInfo.getType(), null), values);
				} else {
					Cache.getContext().getContentResolver().update(ContentProvider.createUri(mTableInfo.getType(), null), values, "Id=" + mId, null);
				}
			}
		} else if (mId == null) { // insert
			if (mSpecificId != null && !mReplace) {
				mId = mSpecificId;
				values.put("Id", mId);
				if (!ActiveAndroid.inContentProvider()) {
					SQLiteUtils.insert(mTableInfo.getTableName(), null, values);
				} else {
					Cache.getContext().getContentResolver().insert(ContentProvider.createUri(mTableInfo.getType(), null), values);
				}
			} else {
				if (!ActiveAndroid.inContentProvider()) {
					mId = SQLiteUtils.insert(mTableInfo.getTableName(), null, values);
				} else {
					Uri uri = Cache.getContext().getContentResolver().insert(ContentProvider.createUri(mTableInfo.getType(), null), values);
					if (uri != null) mId = android.content.ContentUris.parseId(uri);
				}
			}
		} else { // update for mId
			if (mDeleted) {
				return new Long(-1);
			}

			if (mSpecificId != null && !mReplace) {
				if (!ActiveAndroid.inContentProvider()) {
					delete();
					mId = mSpecificId;
					values.put("Id", mId);
					SQLiteUtils.insert(mTableInfo.getTableName(), null, values);
				} else {
					// FIXME for inContentProvider
					delete();
					mId = mSpecificId;
					values.put("Id", mId);
					Cache.getContext().getContentResolver().insert(ContentProvider.createUri(mTableInfo.getType(), null), values);
				}
			} else {
			if (!ActiveAndroid.inContentProvider()) {
				SQLiteUtils.update(mTableInfo.getTableName(), values, "Id=" + mId, null);
			} else {
				Cache.getContext().getContentResolver().update(ContentProvider.createUri(mTableInfo.getType(), null), values, "Id=" + mId, null);
			}
			}
		}

		if ((mId != -1) && (Cache.getEntity(getClass(), mId) != null)) {
			Cache.addEntity(this);
		}

		Cache.getContext().getContentResolver()
				.notifyChange(ContentProvider.createUri(mTableInfo.getType(), mId), null);
		return mId;
	}

	// Convenience methods

	public static void delete(Class<? extends Model> type, long id) {
		new Delete().from(type).where("Id=?", id).execute();
	}

	/** @deprecated */
	public static <T extends Model> CursorList<T> rawQuery(Class<? extends Model> type, String sql, String[] selectionArgs) {
		return SQLiteUtils.rawQuery(type, sql, selectionArgs);
	}

	/** @deprecated */
	public static <T extends Model> T rawQuerySingle(Class<? extends Model> type, String sql, String[] selectionArgs) {
		return (T) SQLiteUtils.rawQuerySingle(type, sql, selectionArgs);
	}

	private static <T extends Model> T loadByActiveAndroid(Class<T> type, long id) {
		return (T) new Select().from(type).where("Id=?", id).executeSingle();
	}

	private static <T extends Model> T loadByContentProvider(Class<T> type, long id) {
		String[] projection = {};
		for (Field field : Cache.getTableInfo(type).getFields()) {
			final String fieldName = Cache.getTableInfo(type).getColumnName(field);
			java.util.Arrays.fill(projection, fieldName);
		}
		Cursor c = Cache.getContext().getContentResolver().query(ContentProvider.createUri(type, null), projection, "Id=" + id, null, null);
		Model entity = null;

		try {
			Constructor<?> entityConstructor = type.getConstructor();

			if (c != null && c.moveToFirst()) {
				entity = (T) entityConstructor.newInstance();
				entity.loadFromCursor(c);
			}
		}
		catch (Exception e) {
			Log.e("Failed to process cursor.", e);
		}

		return (T) entity;
	}

	public static <T extends Model> T load(Class<T> type, long id) {
		Model entity = Cache.getEntity(type, id);
		if (entity != null) return (T) entity;
		if (ActiveAndroid.inContentProvider()) return loadByContentProvider(type, id);
		else return loadByActiveAndroid(type, id);
	}

	// Model population

	public final void lock() {
		if (((mId == null) || (mId == -1)) && (mSpecificId != null)) {
			return;
		}

		mLock = Cache.getModelLock(this);
		mLock.lock();
		load();
	}

	public final void unlock() {
		if (mLock != null) {
			mLock.unlock();
			mLock = null;
		}
	}

	public final void load() {
		if ((mId == null) || (mId == -1)) {
			return;
		}

		Model model = Model.load(getClass(), mId);
		if (model == null) {
			mDeleted = true;
			return;
		}

		if (model == this) {
			return;
		}

		for (Field field : mTableInfo.getFields()) {
			try {
				field.setAccessible(true);
				field.set(this, field.get(model));
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
	}

	public final int loadFromCursor(Cursor cursor) {
		int sizeOfColumnNotFound = 0;

		for (Field field : mTableInfo.getFields()) {
			final String fieldName = mTableInfo.getColumnName(field);
			Class<?> fieldType = field.getType();
			final int columnIndex = cursor.getColumnIndex(fieldName);

			if (columnIndex < 0) {
				sizeOfColumnNotFound++;
				continue;
			}

			field.setAccessible(true);

			try {
				boolean columnIsNull = cursor.isNull(columnIndex);
				TypeSerializer typeSerializer = Cache.getParserForType(fieldType);
				Object value = null;

				if (typeSerializer != null) {
					fieldType = typeSerializer.getSerializedType();
				}

				// TODO: Find a smarter way to do this? This if block is necessary because we
				// can't know the type until runtime.
				if (columnIsNull) {
					field = null;
				}
				else if (fieldType.equals(Byte.class) || fieldType.equals(byte.class)) {
					value = cursor.getInt(columnIndex);
				}
				else if (fieldType.equals(Short.class) || fieldType.equals(short.class)) {
					value = cursor.getInt(columnIndex);
				}
				else if (fieldType.equals(Integer.class) || fieldType.equals(int.class)) {
					value = cursor.getInt(columnIndex);
				}
				else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) {
					value = cursor.getLong(columnIndex);
				}
				else if (fieldType.equals(Float.class) || fieldType.equals(float.class)) {
					value = cursor.getFloat(columnIndex);
				}
				else if (fieldType.equals(Double.class) || fieldType.equals(double.class)) {
					value = cursor.getDouble(columnIndex);
				}
				else if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) {
					value = cursor.getInt(columnIndex) != 0;
				}
				else if (fieldType.equals(Character.class) || fieldType.equals(char.class)) {
					value = cursor.getString(columnIndex).charAt(0);
				}
				else if (fieldType.equals(String.class)) {
					value = cursor.getString(columnIndex);
				}
				else if (fieldType.equals(Byte[].class) || fieldType.equals(byte[].class)) {
					value = cursor.getBlob(columnIndex);
				}
				else if (ReflectionUtils.isModel(fieldType)) {
					final long entityId = cursor.getLong(columnIndex);
					final Class<? extends Model> entityType = (Class<? extends Model>) fieldType;

					value = Model.load(entityType, entityId);
				}
				else if (ReflectionUtils.isSubclassOf(fieldType, Enum.class)) {
					@SuppressWarnings("rawtypes")
					final Class<? extends Enum> enumType = (Class<? extends Enum>) fieldType;
					value = Enum.valueOf(enumType, cursor.getString(columnIndex));
				}

				// Use a deserializer if one is available
				if (typeSerializer != null && !columnIsNull) {
					value = typeSerializer.deserialize(value);
				}

				// Set the field value
				if (value != null) {
					field.set(this, value);
				}
			}
			catch (IllegalArgumentException e) {
				Log.e(e.getClass().getName(), e);
			}
			catch (IllegalAccessException e) {
				Log.e(e.getClass().getName(), e);
			}
			catch (SecurityException e) {
				Log.e(e.getClass().getName(), e);
			}
		}

		return sizeOfColumnNotFound;
	}

	public static void registerContentObserver(Class<? extends Model> type, boolean notifyForDescendents, ContentObserver observer) {
		Cache.getContext().getContentResolver().registerContentObserver(
				ContentProvider.createUri(type, null),
				notifyForDescendents, observer);
	}

	//////////////////////////////////////////////////////////////////////////////////////
	// PROTECTED METHODS
	//////////////////////////////////////////////////////////////////////////////////////

	protected final <T extends Model> CursorList<T> getMany(Class<T> type, String foreignKey) {
		return new Select().from(type).where(Cache.getTableName(type) + "." + foreignKey + "=?", getId()).execute();
	}

	//////////////////////////////////////////////////////////////////////////////////////
	// OVERRIDEN METHODS
	//////////////////////////////////////////////////////////////////////////////////////

	@Override
	public String toString() {
		return mTableInfo.getTableName() + "@" + getId();
	}

	@Override
	public boolean equals(Object obj) {
		final Model other = (Model) obj;

		return this.mId != null && (this.mTableInfo.getTableName().equals(other.mTableInfo.getTableName()))
				&& (this.mId.equals(other.mId));
	}

	@Override
	public boolean onMarshall() {
		return false;
	}

	@Override
	public boolean onMarshall(Model entity) {
		return false;
	}
}
