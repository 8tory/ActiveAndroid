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
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.v4.util.LruCache;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.activeandroid.serializer.TypeSerializer;
import com.activeandroid.util.Log;
import com.activeandroid.util.SQLiteUtils.Yield;

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
	private static SparseArray<Yield> sYield = new SparseArray<Yield>();
	private static SparseIntArray sYieldCount = new SparseIntArray();
	private static SparseIntArray sYieldTransaction = new SparseIntArray();

	private static Configuration sConfiguration;
	private static String sDatabaseName;

	private static Map<String, WeakReference<ReentrantLock>> sModelLocks = new HashMap<String, WeakReference<ReentrantLock>>();

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

	public static String getIdentifier8(Model entity) {
		String identifier;
		if ((entity.getId() != null) && (entity.getId() != -1)) {
			identifier = getIdentifier(entity);
		} else {
			identifier = getIdentifier(entity.getClass(), entity.getSpecificId());
		}
		return identifier;
	}

	public static synchronized void addEntity(Model entity) {
		if (sEntities == null) return;
		synchronized (sEntities) {
		sEntities.put(getIdentifier(entity), entity);
		}
	}

	public static synchronized Model getEntity(Class<? extends Model> type, long id) {
		if (sEntities == null) return null;
		return sEntities.get(getIdentifier(type, id));
	}

	public static synchronized void removeEntity(Model entity) {
		if (sEntities == null) return;
		synchronized (sEntities) {
		sEntities.remove(getIdentifier(entity));
		}
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

	public static synchronized ReentrantLock getModelLock(Model model) {
		WeakReference<ReentrantLock> modelRef = sModelLocks.get(getIdentifier8(model));
		ReentrantLock lock = null;

		if (modelRef != null) {
			lock = modelRef.get();
		}

		if (lock == null) {
			lock = new ReentrantLock();
			modelRef = new WeakReference<ReentrantLock>(lock);
			sModelLocks.put(getIdentifier8(model), modelRef);
		}

		return lock;
	}

	public static synchronized void beginTransaction() {
		final int tid = android.os.Process.myTid();

		final int count = sYieldCount.get(tid);

		if (count == 0) {
			final Yield yield = new Yield();
			yield.begin();
			sYield.put(tid, yield);
		}

		sYieldCount.put(tid, count + 1);
	}

	public static synchronized void endTransaction() {
		final int tid = android.os.Process.myTid();

		final int count = sYieldCount.get(tid);

		if (count > 1) {
			sYieldCount.put(tid, count - 1);
			return;
		}

		final Yield yield = sYield.get(tid);
		yield.end();
		sYield.delete(tid);

		sYieldCount.delete(tid);
	}

	public static synchronized void setTransactionSuccessful() {
		final int tid = android.os.Process.myTid();

		final int count = sYieldCount.get(tid);

		if (count > 1)
			return;

		final Yield yield = sYield.get(tid);
		yield.success();
	}

	public static void beginReleaseTransaction() {
		synchronized (yieldTransactionLock) {
			final int tid = android.os.Process.myTid();
			final int count = sYieldTransaction.get(tid);
			sYieldTransaction.put(tid, count + 1);
		}
	}

	public static void endReleaseTransaction() {
		synchronized (yieldTransactionLock) {
			final int tid = android.os.Process.myTid();

			final int count = sYieldTransaction.get(tid);
			if (count > 1) {
				sYieldTransaction.put(tid, count - 1);
				return;
			}

			sYieldTransaction.delete(tid);
			if (sYieldTransaction.size() != 0)
				return;

			yieldTransactionLock.notifyAll();
		}
	}

	public static void yieldTransaction() {
		SQLiteDatabase db = null;

		synchronized (yieldTransactionLock) {
			if (!needYieldTransaction())
				return;

			db = sDatabaseHelper.getWritableDatabase();

			try {
				db.setTransactionSuccessful();
			} finally {
				db.endTransaction();
			}

			while (!canTransaction()) {
				try {
					yieldTransactionLock.wait();
				} catch (Exception e) {
				}
			}
		}

		db.beginTransaction();
	}

	private static boolean needYieldTransaction() {
		synchronized (yieldTransactionLock) {
			final int size = sYieldTransaction.size();
			if (size == 0)
				return false;

			final int tid = android.os.Process.myTid();
			if (size == 1 && sYieldTransaction.get(tid) > 0)
				return false;

			return true;
		}
	}

	private static boolean canTransaction() {
		synchronized (yieldTransactionLock) {
			final int size = sYieldTransaction.size();
			if (size == 0)
				return true;

			final int tid = android.os.Process.myTid();
			if (sYieldTransaction.get(tid) > 0)
				return true;

			return false;
		}
	}
}
