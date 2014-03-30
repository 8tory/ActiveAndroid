package com.activeandroid.util;

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
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Looper;
import android.text.TextUtils;

import com.activeandroid.ActiveAndroid;
import com.activeandroid.Cache;
import com.activeandroid.Model;
import com.activeandroid.TableInfo;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Column.ConflictAction;
import com.activeandroid.annotation.Table;
import com.activeandroid.serializer.TypeSerializer;
import com.activeandroid.util.Log;
import com.novoda.notils.cursor.CursorList;
import com.novoda.notils.cursor.SimpleCursorList;
import com.novoda.notils.cursor.SmartCursorWrapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SQLiteUtils {
	//////////////////////////////////////////////////////////////////////////////////////
	// ENUMERATIONS
	//////////////////////////////////////////////////////////////////////////////////////

	public enum SQLiteType {
		INTEGER, REAL, TEXT, BLOB
	}

	//////////////////////////////////////////////////////////////////////////////////////
	// PUBLIC CONSTANTS
	//////////////////////////////////////////////////////////////////////////////////////

	@SuppressLint("NewApi")
	public static final boolean FOREIGN_KEYS_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;

	//////////////////////////////////////////////////////////////////////////////////////
	// PRIVATE CONTSANTS
	//////////////////////////////////////////////////////////////////////////////////////

	@SuppressWarnings("serial")
	private static final HashMap<Class<?>, SQLiteType> TYPE_MAP = new HashMap<Class<?>, SQLiteType>() {
		{
			put(byte.class, SQLiteType.INTEGER);
			put(short.class, SQLiteType.INTEGER);
			put(int.class, SQLiteType.INTEGER);
			put(long.class, SQLiteType.INTEGER);
			put(float.class, SQLiteType.REAL);
			put(double.class, SQLiteType.REAL);
			put(boolean.class, SQLiteType.INTEGER);
			put(char.class, SQLiteType.TEXT);
			put(byte[].class, SQLiteType.BLOB);
			put(Byte.class, SQLiteType.INTEGER);
			put(Short.class, SQLiteType.INTEGER);
			put(Integer.class, SQLiteType.INTEGER);
			put(Long.class, SQLiteType.INTEGER);
			put(Float.class, SQLiteType.REAL);
			put(Double.class, SQLiteType.REAL);
			put(Boolean.class, SQLiteType.INTEGER);
			put(Character.class, SQLiteType.TEXT);
			put(String.class, SQLiteType.TEXT);
			put(Byte[].class, SQLiteType.BLOB);
		}
	};

	//////////////////////////////////////////////////////////////////////////////////////
	// PRIVATE MEMBERS
	//////////////////////////////////////////////////////////////////////////////////////

	private static HashMap<String, List<String>> sIndexGroupMap;
	private static HashMap<String, List<String>> sUniqueGroupMap;
	private static HashMap<String, ConflictAction> sOnUniqueConflictsMap;

	private static List<Integer> sNoException = new ArrayList<Integer>();

	//////////////////////////////////////////////////////////////////////////////////////
	// PUBLIC METHODS
	//////////////////////////////////////////////////////////////////////////////////////

	// TODO Merge code snippet of cache transaction
	public static class Yield {
		boolean yielded = false;

		public Yield() {
		}

		public Yield begin() {
			if (Looper.myLooper() == Looper.getMainLooper()) {
				Cache.beginReleaseTransaction();
				yielded = true;
			}

			return this;
		}

		public Yield success() {
			//if (yielded)
			// TODO
			return this;
		}

		public Yield end() {
			if (yielded) {
				Cache.endReleaseTransaction();
				yielded = false;
			}
			return this;
		}
	}

	public static void execSql(String sql) {
		Cache.openDatabase().execSQL(sql);
	}

	public static void execSql(String sql, Object[] bindArgs) {
		Cache.openDatabase().execSQL(sql, bindArgs);
	}

	public static void rename(Class<? extends Model> from, Class<? extends Model> to) {
		drop(to);
		execSql("ALTER TABLE " + Cache.getTableInfo(from).getTableName() + "RENAME TO " + Cache.getTableInfo(to).getTableName());
	}

	public static void drop(Class<? extends Model> type) {
		drop(Cache.getTableInfo(type));
	}

	public static void drop(TableInfo tableInfo) {
		execSql("DROP TABLE IF EXISTS " + tableInfo.getTableName());
	}

	public static <T extends Model> CursorList<T> rawQuery(Class<? extends Model> type, String sql, String[] selectionArgs) {
		CursorList<T> entities;

		Yield yield = new Yield().begin();
		try {
			Cursor cursor = Cache.openDatabase().rawQuery(sql, selectionArgs);
			entities = processCursor(type, cursor);
			yield.success();
		} finally {
			yield.end();
		}

		return entities;
	}

	public static int delete(String tableName, String sql, String[] selectionArgs) {
		int rows;

		Yield yield = new Yield().begin();
		try {
			rows = Cache.openDatabase().delete(tableName, sql, selectionArgs);
			yield.success();
		} finally {
			yield.end();
		}

		return rows;
	}

	public static long insert(String table, String nullColumnHook, ContentValues values) {
		long id = -1;

		Yield yield = new Yield().begin();
		try {
			if (showException())
				id = Cache.openDatabase().insert(table, nullColumnHook, values);
			else
				id = Cache.openDatabase().insertOrThrow(table, nullColumnHook, values);
			yield.success();
		} catch (Exception e) {
		} finally {
			yield.end();
		}

		return id;
	}

	public static long replace(String table, String nullColumnHook, ContentValues values) {
		long id = -1;

		Yield yield = new Yield().begin();
		try {
			if (showException())
				id = Cache.openDatabase().replace(table, nullColumnHook, values);
			else
				id = Cache.openDatabase().replaceOrThrow(table, nullColumnHook, values);
			yield.success();
		} catch (Exception e) {
		} finally {
			yield.end();
		}

		return id;
	}

	public static int update(String table, ContentValues values, String whereClause, String[] where) {
		int rows;

		Yield yield = new Yield().begin();
		try {
			rows = Cache.openDatabase().update(table, values, whereClause, where);
			yield.success();
		} finally {
			yield.end();
		}

		return rows;
	}

	public static Cursor query(String table, String[] projection, String selection, String[] selectionArgs,
			String groupBy, String having, String sortOrder) {
		Cursor cursor;

		Yield yield = new Yield().begin();
		try {
			cursor = Cache.openDatabase().query(table, projection, selection, selectionArgs,
					groupBy, having, sortOrder);
			yield.success();
		} finally {
			yield.end();
		}

		return cursor;
	}

	public static <T extends Model> T rawQuerySingle(Class<? extends Model> type, String sql, String[] selectionArgs) {
		T item = null;

		CursorList<T> entities = rawQuery(type, sql, selectionArgs);
		if (entities.size() > 0) {
			item = entities.get(0);
		}
		entities.close();

		return item;
	}

	// Database creation

	public static String[] createIndexDefinition(TableInfo tableInfo) {
		final ArrayList<String> definitions = new ArrayList<String>();
		sIndexGroupMap = new HashMap<String, List<String>>();

		for (Field field : tableInfo.getFields()) {
			createIndexColumnDefinition(tableInfo, field);
		}

		if (sIndexGroupMap.isEmpty()) {
			return new String[0];
		}

		for (Map.Entry<String, List<String>> entry : sIndexGroupMap.entrySet()) {
			definitions.add(String.format("CREATE INDEX IF NOT EXISTS %s on %s(%s);",
					"index_" + tableInfo.getTableName() + "_" + entry.getKey(),
					tableInfo.getTableName(), TextUtils.join(", ", entry.getValue())));
		}

		return definitions.toArray(new String[definitions.size()]);
	}

	public static String getSchema(SQLiteDatabase db, TableInfo tableInfo) {
		return getSchema(db, tableInfo.getTableName());
	}

	public static String getSchema(SQLiteDatabase db, String tableName) {
		Cursor cursor = db.rawQuery(
				"SELECT sql FROM sqlite_master WHERE type='table' and name='" + tableName + "'", null);
		String schema = null;
		try {
			if (cursor.moveToFirst()) {
				schema = cursor.getString(cursor.getColumnIndex("sql"));
			}
		} finally {
			cursor.close();
		}
		return schema;
	}

	/**
	 * TODO
	 */
	public static String createAlterDefinition(SQLiteDatabase db, TableInfo tableInfo) {
		String fromSql = getSchema(db, tableInfo);
		Log.d("name: " + tableInfo.getTableName());
		if (TextUtils.isEmpty(fromSql)) return null;
		Log.d("fromSql: " + fromSql);
		String toSql = tableInfo.getSchema();
		if (TextUtils.isEmpty(toSql)) return null;
		Log.d("toSql: " + toSql);
		List<String> fromColumns = getColumns(tableInfo, fromSql);
		if (fromColumns == null || fromColumns.isEmpty()) return null;
		List<String> toColumns = getColumns(tableInfo, toSql);
		if (fromColumns == null || fromColumns.isEmpty()) return null;
		Log.d("toCols: " + java.util.Arrays.deepToString(toColumns.toArray()));
		Log.d("fromCols: " + java.util.Arrays.deepToString(fromColumns.toArray()));
		List<String> extraColumns = new ArrayList<String>(toColumns);
		extraColumns.removeAll(fromColumns);
		Log.d("extCols: " + java.util.Arrays.deepToString(extraColumns.toArray()));
		StringBuilder sql = new StringBuilder();
		for (String column : extraColumns) {
			sql.append(String.format("ALTER TABLE %s ADD COLUMN %s; ", tableInfo.getTableName(), column));
		}
		return sql.toString();
	}

	/**
	 * TODO
	 */
	private boolean alterColumnsIfNeed(String table, String toSql, String fromSql) {
		//List<String> toColumns = getColumns(toSql);
		//List<String> fromColumns = getColumns(fromSql);
		return false;
	}

	/*
	private boolean addColumnsIfNeed(String table, String to, String from) {
		try {
			// if change name of column or add new column, or delete
			boolean isAddNewColumn = false;

			if (from.contains(table)) {
				List<String> fromColumns = Arrays.asList(from.
						replace(String.format(
								SimpleConstants.SQL_CREATE_TABLE, table), SimpleConstants.EMPTY).
						replace(SimpleConstants.LAST_BRACKET, SimpleConstants.EMPTY).
						split(SimpleConstants.DIVIDER_WITH_SPACE));

				List<String> toColumns = Arrays.asList(to.
						replace(String.format(
								SimpleConstants.SQL_CREATE_TABLE_IF_NOT_EXIST, table), SimpleConstants.EMPTY).
						replace(SimpleConstants.LAST_BRACKET, SimpleConstants.EMPTY).
						split(SimpleConstants.DIVIDER_WITH_SPACE));

				List<String> extraColumns = new ArrayList<String>(toColumns);
				extraColumns.removeAll(fromColumns);

				if (extraColumns.size() > 0) {

					SQLiteDatabase database = sqLiteSimpleHelper.getWritableDatabase();
					for (String column : extraColumns) {
						database.execSQL(String.format(
									SimpleConstants.SQL_ALTER_TABLE_ADD_COLUMN, table, column));
					}
					database.close();
					isAddNewColumn = true;
				}
			}

			return isAddNewColumn;

		} catch (IndexOutOfBoundsException exception) {
			throw new RuntimeException("Duplicated class on method create(...)");
		}
	}
	*/

	public static List<String> getColumns(TableInfo tableInfo, String schema) {
		return getColumns(tableInfo.getTableName(), schema);
	}

	// TODO
	public static List<String> getColumns(String tableName, String schema) {
		List<String> columns = Arrays.asList(schema
				.replaceAll("CREATE (VIRTUAL )?TABLE (IF NOT EXISTS )?" + tableName, "")
				.replace("(", "")
				.replace(")", "")
				.replace(";", "")
				.split(","));
		List<String> columnsWithoutType = new ArrayList<String>();
		for (String column : columns) {
			columnsWithoutType.add(column.replaceAll("^\\s+", "").replaceAll(" .*", ""));
		}
		return columnsWithoutType;
	}

	// TODO
	public static List<String> alterColumns(String table, String... columns) {
		return null;
	}

	// TODO
	public static List<String> renameColumn(String table, String from, String to) {
		return null;
	}

	/**
	 * rename(Model, Model)
	 */
	public static List<String> renameTable(String from, String to) {
		return null;
	}

	public static void createIndexColumnDefinition(TableInfo tableInfo, Field field) {
		final String name = tableInfo.getColumnName(field);
		final Column column = field.getAnnotation(Column.class);

		if (column.index()) {
			List<String> list = new ArrayList<String>();
			list.add(name);
			sIndexGroupMap.put(name, list);
		}

		String[] groups = column.indexGroups();
		for (String group : groups) {
			if (group.isEmpty())
				continue;

			List<String> list = sIndexGroupMap.get(group);
			if (list == null) {
				list = new ArrayList<String>();
			}

			list.add(name);
			sIndexGroupMap.put(group, list);
		}
	}

	public static ArrayList<String> createUniqueDefinition(TableInfo tableInfo) {
		final ArrayList<String> definitions = new ArrayList<String>();
		sUniqueGroupMap = new HashMap<String, List<String>>();
		sOnUniqueConflictsMap = new HashMap<String, ConflictAction>();

		for (Field field : tableInfo.getFields()) {
			createUniqueColumnDefinition(tableInfo, field);
		}

		if (sUniqueGroupMap.isEmpty()) {
			return definitions;
		}

		Set<String> keySet = sUniqueGroupMap.keySet();
		for (String key : keySet) {
			List<String> group = sUniqueGroupMap.get(key);
			ConflictAction conflictAction = sOnUniqueConflictsMap.get(key);

			definitions.add(String.format("UNIQUE (%s) ON CONFLICT %s",
					TextUtils.join(", ", group), conflictAction.toString()));
		}

		return definitions;
	}

	public static void createUniqueColumnDefinition(TableInfo tableInfo, Field field) {
		final String name = tableInfo.getColumnName(field);
		final Column column = field.getAnnotation(Column.class);

		String[] groups = column.uniqueGroups();
		ConflictAction[] conflictActions = column.onUniqueConflicts();
		if (groups.length != conflictActions.length)
			return;

		for (int i = 0; i < groups.length; i++) {
			String group = groups[i];
			ConflictAction conflictAction = conflictActions[i];

			if (group.isEmpty())
				continue;

			List<String> list = sUniqueGroupMap.get(group);
			if (list == null) {
				list = new ArrayList<String>();
			}
			list.add(name);

			sUniqueGroupMap.put(group, list);
			sOnUniqueConflictsMap.put(group, conflictAction);
		}
	}

	public static String createTableDefinition(TableInfo tableInfo) {
		if (!TextUtils.isEmpty(tableInfo.getModule())) return null;

		final ArrayList<String> definitions = new ArrayList<String>();

		for (Field field : tableInfo.getFields()) {
			String definition = createColumnDefinition(tableInfo, field);
			if (!TextUtils.isEmpty(definition)) {
				definitions.add(definition);
			}
		}

		definitions.addAll(createUniqueDefinition(tableInfo));

		return String.format("CREATE TABLE IF NOT EXISTS %s (%s);", tableInfo.getTableName(),
				TextUtils.join(", ", definitions));
	}

	public static String createVirtualTableDefinition(TableInfo tableInfo) {
		if (TextUtils.isEmpty(tableInfo.getModule())) return null;

		final ArrayList<String> definitions = new ArrayList<String>();
		for (Field field : tableInfo.getFields()) {
			String definition = tableInfo.getColumnName(field);
			if (!TextUtils.isEmpty(definition)) {
				definitions.add(definition);
			}
		}

		definitions.addAll(createUniqueDefinition(tableInfo));

		if (Build.VERSION.SDK_INT < 16) {
			return String.format("CREATE VIRTUAL TABLE %s USING %s(%s);", tableInfo.getTableName(),
					tableInfo.getModule(), TextUtils.join(", ", definitions));
		} else {
			return String.format("CREATE VIRTUAL TABLE IF NOT EXISTS %s USING %s(%s);", tableInfo.getTableName(),
					tableInfo.getModule(), TextUtils.join(", ", definitions));
		}
	}

	@SuppressWarnings("unchecked")
	public static String createColumnDefinition(TableInfo tableInfo, Field field) {
		StringBuilder definition = new StringBuilder();

		Class<?> type = field.getType();
		final String name = tableInfo.getColumnName(field);
		final TypeSerializer typeSerializer = Cache.getParserForType(field.getType());
		final Column column = field.getAnnotation(Column.class);

		if (column.readOnly())
			return definition.toString();

		if (typeSerializer != null) {
			type = typeSerializer.getSerializedType();
		}

		if (TYPE_MAP.containsKey(type)) {
			definition.append(name);
			definition.append(" ");
			definition.append(TYPE_MAP.get(type).toString());
		}
		else if (ReflectionUtils.isModel(type)) {
			definition.append(name);
			definition.append(" ");
			definition.append(SQLiteType.INTEGER.toString());
		}
		else if (ReflectionUtils.isSubclassOf(type, Enum.class)) {
			definition.append(name);
			definition.append(" ");
			definition.append(SQLiteType.TEXT.toString());
		}

		if (!TextUtils.isEmpty(definition)) {
			if (column.length() > -1) {
				definition.append("(");
				definition.append(column.length());
				definition.append(")");
			}

			if (name.equals("Id")) {
				definition.append(" PRIMARY KEY AUTOINCREMENT");
			}

			if (column.notNull()) {
				definition.append(" NOT NULL ON CONFLICT ");
				definition.append(column.onNullConflict().toString());
			}

			if (column.unique()) {
				definition.append(" UNIQUE ON CONFLICT ");
				definition.append(column.onUniqueConflict().toString());
			}

			if (FOREIGN_KEYS_SUPPORTED && ReflectionUtils.isModel(type) && column.foreign()) {
				definition.append(" REFERENCES ");
				definition.append(Cache.getTableInfo((Class<? extends Model>) type).getTableName());
				definition.append("(Id)");
				definition.append(" ON DELETE ");
				definition.append(column.onDelete().toString().replace("_", " "));
				definition.append(" ON UPDATE ");
				definition.append(column.onUpdate().toString().replace("_", " "));
			}
		}
		else {
			Log.e("No type mapping for: " + type.toString());
		}

		return definition.toString();
	}

	public static <T extends Model> CursorList<T> processCursor(Class<? extends Model> type, Cursor cursor) {
		SmartCursorWrapper cursorWrapper = new SmartCursorWrapper(cursor);
		cursorWrapper.setOnCloseListener(new SmartCursorWrapper.OnCloseListener() {
			@Override
			public void onClose() {
				ActiveAndroid.beginReleaseTransaction();
			}
			@Override
			public void onCloseFinished() {
				ActiveAndroid.endReleaseTransaction();
			}
		});
		return new SimpleCursorList<T>(cursorWrapper, new ModelCursorMarshaller<T>(type));
	}

	public static void showException(boolean show) {
		final Integer tid = android.os.Process.myTid();

		final int index = sNoException.indexOf(tid);
		if (show) {
			if (index != -1)
				sNoException.remove(index);
		} else {
			if (index == -1)
				sNoException.add(tid);
		}
	}

	private static boolean showException() {
		final Integer tid = android.os.Process.myTid();

		final int index = sNoException.indexOf(tid);
		if (index != -1)
			return false;
		else
			return true;
	}
}
