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
	private static int sYieldTransactionCount;
	private static int sTransactionTid;
	private static int sTransactionCount;

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
		final int cacheSize = configuration.getCacheSize();
		sEntities = (cacheSize > 0) ? new LruCache<String, Model>(cacheSize) : null;

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
		if (sEntities == null) return;
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
		if (sEntities == null) return;
		sEntities.put(getIdentifier(entity), entity);
	}

	public static synchronized Model getEntity(Class<? extends Model> type, long id) {
		if (sEntities == null) return null;
		return sEntities.get(getIdentifier(type, id));
	}

	public static synchronized void removeEntity(Model entity) {
		if (sEntities == null) return;
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

	public static Object getLock() {
		return yieldTransactionLock;
	}

	public static void beginTransaction() {
		synchronized (yieldTransactionLock) {
			sTransactionCount++;
			if (sTransactionCount > 1)
				return;
			sTransactionTid = android.os.Process.myTid();
		}
	}

	public static void endTransaction() {
		synchronized (yieldTransactionLock) {
			sTransactionCount--;
			if (sTransactionCount > 0)
				return;
			sTransactionCount = 0;
			sTransactionTid = 0;
		}
	}

	public static void beginReleaseTransaction() {
		synchronized (yieldTransactionLock) {
			sYieldTransactionCount++;
		}
	}

	public static void endReleaseTransaction() {
		synchronized (yieldTransactionLock) {
			sYieldTransactionCount--;
			if (sYieldTransactionCount > 0)
				return;
			sYieldTransactionCount = 0;
			yieldTransactionLock.notifyAll();
		}
	}

	public static void yieldTransaction() {
		synchronized (yieldTransactionLock) {
			if (sYieldTransactionCount <= 0)
				return;

			final SQLiteDatabase db = sDatabaseHelper.getWritableDatabase();
			if (!db.inTransaction())
				return;

			try {
				db.setTransactionSuccessful();
			} finally {
				sTransactionTid = 0;
				db.endTransaction();
			}

			try {
				yieldTransactionLock.wait();
			} catch (Exception e) {
			}

			db.beginTransaction();

			final int tid = android.os.Process.myTid();
			sTransactionTid = tid;
		}
	}
}
