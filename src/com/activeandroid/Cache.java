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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.v4.util.LruCache;

import com.activeandroid.serializer.TypeSerializer;
import com.activeandroid.util.Log;

public final class Cache {
	//////////////////////////////////////////////////////////////////////////////////////
	// PRIVATE MEMBERS
	//////////////////////////////////////////////////////////////////////////////////////

	private static Context sContext;

	private static ModelInfo sModelInfo;
	private static DatabaseHelper sDatabaseHelper;

	private static LruCache<String, Model> sEntities;

	private static boolean sIsInitialized = false;

	private static Object yieldTransactionLock = new Object();
	private static List<Integer> yieldTransaction = new ArrayList<Integer>();

	private static Configuration sConfiguration;
	private static String sDatabaseName;

	//////////////////////////////////////////////////////////////////////////////////////
	// CONSTRUCTORS
	//////////////////////////////////////////////////////////////////////////////////////

	private Cache() {
	}

	//////////////////////////////////////////////////////////////////////////////////////
	// PUBLIC METHODS
	//////////////////////////////////////////////////////////////////////////////////////

	public static synchronized void initialize(Configuration configuration) {
		if (sIsInitialized) {
			Log.v("ActiveAndroid already initialized.");
			return;
		}

		sConfiguration = configuration;
		sDatabaseName = sConfiguration.getDatabaseName();

		sContext = configuration.getContext();
		sModelInfo = new ModelInfo(configuration);

		if (canValidDatabase())
			sDatabaseHelper = new DatabaseHelper(configuration);
		else
			reInitNullDatabase();

		// TODO: It would be nice to override sizeOf here and calculate the memory
		// actually used, however at this point it seems like the reflection
		// required would be too costly to be of any benefit. We'll just set a max
		// object size instead.
		sEntities = new LruCache<String, Model>(configuration.getCacheSize());

		openDatabase();

		sIsInitialized = true;

		Log.v("ActiveAndroid initialized successfully.");
	}

	public static synchronized void reInitDatabase() {
		if (sConfiguration == null)
			return;

		if (!canValidDatabase()) {
			reInitNullDatabase();
			return;
		}

		sConfiguration.setDatabaseName(sDatabaseName);

		if (sDatabaseHelper != null)
			sDatabaseHelper.close();
		sDatabaseHelper = new DatabaseHelper(sConfiguration);
	}

	public static synchronized void reInitNullDatabase() {
		if (sConfiguration == null)
			return;

		sConfiguration.setDatabaseName(null);

		if (sDatabaseHelper != null)
			sDatabaseHelper.close();
		sDatabaseHelper = new DatabaseHelper(sConfiguration);
	}

	public static synchronized boolean canValidDatabase() {
		if (sContext == null)
			return false;

		File file = sContext.getDatabasePath(sDatabaseName);

		do {
			if (file.exists())
				return true;

			try {
				if (!file.getCanonicalFile().equals(file.getAbsoluteFile()))
					return false;
			} catch (Exception e) {
			}
		} while ((file = file.getParentFile()) != null);

		return false;
	}

	public static synchronized void clear() {
		sEntities.evictAll();
		Log.v("Cache cleared.");
	}

	public static synchronized void dispose() {
		closeDatabase();

		sEntities = null;
		sModelInfo = null;
		sDatabaseHelper = null;

		sIsInitialized = false;

		Log.v("ActiveAndroid disposed. Call initialize to use library.");
	}

	// Database access

	public static synchronized SQLiteDatabase openDatabase() {
		return sDatabaseHelper.getWritableDatabase();
	}

	public static synchronized void closeDatabase() {
		sDatabaseHelper.close();
	}

	// Context access

	public static Context getContext() {
		return sContext;
	}

	// Entity cache

	public static String getIdentifier(Class<? extends Model> type, Long id) {
		return getTableName(type) + "@" + id;
	}

	public static String getIdentifier(Model entity) {
		return getIdentifier(entity.getClass(), entity.getId());
	}

	public static synchronized void addEntity(Model entity) {
		sEntities.put(getIdentifier(entity), entity);
	}

	public static synchronized Model getEntity(Class<? extends Model> type, long id) {
		return sEntities.get(getIdentifier(type, id));
	}

	public static synchronized void removeEntity(Model entity) {
		sEntities.remove(getIdentifier(entity));
	}

	// Model cache

	public static synchronized Collection<TableInfo> getTableInfos() {
		return sModelInfo.getTableInfos();
	}

	public static synchronized TableInfo getTableInfo(Class<? extends Model> type) {
		return sModelInfo.getTableInfo(type);
	}

	public static synchronized TypeSerializer getParserForType(Class<?> type) {
		return sModelInfo.getTypeSerializer(type);
	}

	public static synchronized String getTableName(Class<? extends Model> type) {
		return sModelInfo.getTableInfo(type).getTableName();
	}

	public static void beginReleaseTransaction() {
		synchronized (yieldTransactionLock) {
			final int tid = android.os.Process.myTid();
			if (yieldTransaction.contains(tid))
				return;
			yieldTransaction.add(tid);
		}
	}

	public static void endReleaseTransaction() {
		synchronized (yieldTransactionLock) {
			final int tid = android.os.Process.myTid();
			if (!yieldTransaction.remove(Integer.valueOf(tid)))
				return;
			if (yieldTransaction.isEmpty())
				yieldTransactionLock.notifyAll();
		}
	}

	public static void yieldTransaction() {
		synchronized (yieldTransactionLock) {
			if (yieldTransaction.isEmpty())
				return;

			final int tid = android.os.Process.myTid();
			if (yieldTransaction.contains(tid))
				return;

			final SQLiteDatabase db = sDatabaseHelper.getWritableDatabase();
			if (!db.inTransaction())
				return;

			try {
				db.setTransactionSuccessful();
			} finally {
				db.endTransaction();
			}

			try {
				yieldTransactionLock.wait();
			} catch (Exception e) {
			}

			db.beginTransaction();
		}
	}
}
