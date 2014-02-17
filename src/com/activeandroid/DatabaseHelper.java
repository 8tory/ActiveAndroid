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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Build;

import com.activeandroid.util.Log;
import com.activeandroid.util.NaturalOrderComparator;
import com.activeandroid.util.SQLiteUtils;

public final class DatabaseHelper extends SQLiteOpenHelper {
	//////////////////////////////////////////////////////////////////////////////////////
	// PUBLIC CONSTANTS
	//////////////////////////////////////////////////////////////////////////////////////

	public final static String MIGRATION_PATH = "migrations";

	//////////////////////////////////////////////////////////////////////////////////////
	// CONSTRUCTORS
	//////////////////////////////////////////////////////////////////////////////////////

	public DatabaseHelper(Configuration configuration) {
		super(configuration.getContext(), configuration.getDatabaseName(), null, configuration.getDatabaseVersion());
		copyAttachedDatabase(configuration.getContext(), configuration.getDatabaseName());
	}

	//////////////////////////////////////////////////////////////////////////////////////
	// OVERRIDEN METHODS
	//////////////////////////////////////////////////////////////////////////////////////

	@Override
	public void onOpen(SQLiteDatabase db) {
		executePragmas(db);
	};

	@Override
	public void onCreate(SQLiteDatabase db) {
		executePragmas(db);
		executeCreate(db);
		executeMigrations(db, -1, db.getVersion());
		executeCreate(db); // Maybe droped tables after mirgation
		executeCreateIndex(db);
		executeCreateVirtualTable(db);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		executePragmas(db);
		executeCreate(db);
		executeMigrations(db, oldVersion, newVersion);
		executeCreate(db);
		executeCreateIndex(db);
		executeCreateVirtualTable(db);
	}

	//////////////////////////////////////////////////////////////////////////////////////
	// PUBLIC METHODS
	//////////////////////////////////////////////////////////////////////////////////////

	public void copyAttachedDatabase(Context context, String databaseName) {
		if (databaseName == null)
			return;

		final File dbPath = context.getDatabasePath(databaseName);

		// If the database already exists, return
		if (dbPath.exists()) {
			return;
		}

		// Make sure we have a path to the file
		dbPath.getParentFile().mkdirs();

		// Try to copy database file
		try {
			final InputStream inputStream = context.getAssets().open(databaseName);
			final OutputStream output = new FileOutputStream(dbPath);

			byte[] buffer = new byte[1024];
			int length;

			while ((length = inputStream.read(buffer)) > 0) {
				output.write(buffer, 0, length);
			}

			output.flush();
			output.close();
			inputStream.close();
		}
		catch (IOException e) {
			Log.e("Failed to open file", e);
		}
	}

	//////////////////////////////////////////////////////////////////////////////////////
	// PRIVATE METHODS
	//////////////////////////////////////////////////////////////////////////////////////

	private void executePragmas(SQLiteDatabase db) {
		if (SQLiteUtils.FOREIGN_KEYS_SUPPORTED) {
			db.execSQL("PRAGMA foreign_keys=ON;");
			Log.i("Foreign Keys supported. Enabling foreign key features.");
		}
	}

	private void executeCreateIndex(SQLiteDatabase db) {
		db.beginTransaction();
		try {
			for (TableInfo tableInfo : Cache.getTableInfos()) {
				String[] definitions = SQLiteUtils.createIndexDefinition(tableInfo);

				for (String definition : definitions) {
					db.execSQL(definition);
				}
			}
			db.setTransactionSuccessful();
		}
		finally {
			db.endTransaction();
		}
	}

	private void executeCreate(SQLiteDatabase db) {
		db.beginTransaction();
		try {
			for (TableInfo tableInfo : Cache.getTableInfos()) {
				String toSql = SQLiteUtils.createTableDefinition(tableInfo);
				tableInfo.setSchema(toSql);
				if (android.text.TextUtils.isEmpty(toSql)) continue;
				alterColumnsIfNeed(tableInfo);
				/*
				 * TODO
				 * alter if need
				String schemaFrom = SQLiteUtils.getSchema(tableInfo);
				*/
				db.execSQL(toSql);
			}
			db.setTransactionSuccessful();
		}
		finally {
			db.endTransaction();
		}
	}

	private void executeCreateVirtualTable(SQLiteDatabase db) {
		db.beginTransaction();
		try {
			for (TableInfo tableInfo : Cache.getTableInfos()) {
				if (Build.VERSION.SDK_INT <= 10) {
					if (existsTable(db, tableInfo)) continue;
				}
				String toSql = SQLiteUtils.createVirtualTableDefinition(tableInfo);
				if (android.text.TextUtils.isEmpty(toSql)) continue;
				db.execSQL(toSql);
			}
			db.setTransactionSuccessful();
		}
		finally {
			db.endTransaction();
		}
	}

	private boolean existsTable(SQLiteDatabase db, TableInfo tableInfo) {
		String tableName = tableInfo.getTableName();
		SQLiteStatement statement = db.compileStatement(
					"SELECT DISTINCT tbl_name from sqlite_master where tbl_name = '" + tableName + "'");
		String result = null;
		try {
			result = statement.simpleQueryForString();
		} catch (SQLiteDoneException e) {
			//e.printStackTrace();
		}
		return tableName.equals(result);
	}

	private void executeDrop(SQLiteDatabase db) {
		db.beginTransaction();
		try {
			for (TableInfo tableInfo : Cache.getTableInfos()) {
				db.execSQL("DROP TABLE IF EXISTS " + tableInfo.getTableName());
			}
			db.setTransactionSuccessful();
		}
		finally {
			db.endTransaction();
		}
	}

	/**
	 * TODO
	 */
	private boolean alterColumnsIfNeed(TableInfo tableInfo) {
		return false;
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

	private boolean executeMigrations(SQLiteDatabase db, int oldVersion, int newVersion) {
		boolean migrationExecuted = false;
		int currentVersion = oldVersion;
		try {
			final List<String> files = Arrays.asList(Cache.getContext().getAssets().list(MIGRATION_PATH));
			Collections.sort(files, new NaturalOrderComparator());

			db.beginTransaction();
			try {
				for (String file : files) {
					try {
						final int version = Integer.valueOf(file.replace(".sql", ""));

						if (version > oldVersion && version <= newVersion) {
							executeSqlScript(db, file);
							migrationExecuted = true;

							Log.i(file + " executed succesfully.");
							currentVersion = version;
						}
					}
					catch (NumberFormatException e) {
						Log.w("Skipping invalidly named file: " + file, e);
					}
				}
				db.setTransactionSuccessful();
			}
			finally {
				db.endTransaction();
			}
		}
		catch (IOException e) {
			Log.e("Failed to execute migrations.", e);
		}
		if (currentVersion != newVersion && currentVersion >= 0) {
			executeDrop(db);
		}

		return migrationExecuted;
	}

	private void executeSqlScript(SQLiteDatabase db, String file) {
		try {
			final InputStream input = Cache.getContext().getAssets().open(MIGRATION_PATH + "/" + file);
			final BufferedReader reader = new BufferedReader(new InputStreamReader(input));
			String line = null;

			while ((line = reader.readLine()) != null) {
				db.execSQL(line.replace(";", ""));
			}
		}
		catch (IOException e) {
			Log.e("Failed to execute " + file, e);
		}
	}
}
