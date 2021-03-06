package com.activeandroid.query;

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

import android.database.Cursor;
import android.text.TextUtils;
import com.activeandroid.ActiveAndroid;
import com.activeandroid.Cache;
import com.activeandroid.Model;
import com.activeandroid.content.ContentProvider;
import com.activeandroid.query.Join.JoinType;
import com.activeandroid.util.Log;
import com.activeandroid.util.SQLiteUtils;
import com.novoda.notils.cursor.CursorList;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class From implements Sqlable {
	private Sqlable mQueryBase;

	private Class<? extends Model> mType;
	private String mAlias;
	private List<Join> mJoins;
	private String mFrom;
	private String mWhere;
	private String mMatch;
	private String mGroupBy;
	private String mHaving;
	private String mOrderBy;
	private String mAscendesc;
	private String mLimit;
	private String mOffset;

	public static final String DESC = "DESC";
	public static final String ASC = "ASC";

	private List<Object> mArguments;

	public From(Class<? extends Model> table, Sqlable queryBase) {
		this(table, queryBase, null);
	}

	public From(Class<? extends Model> table, Sqlable queryBase, String from) {
		mType = table;

		if (from != null)
			mFrom = from;
		else
			mFrom = Cache.getTableName(mType);

		mJoins = new ArrayList<Join>();
		mQueryBase = queryBase;

		mJoins = new ArrayList<Join>();
		mArguments = new ArrayList<Object>();
	}

	public From as(String alias) {
		mAlias = alias;
		return this;
	}

	public Join join(Class<? extends Model> table) {
		Join join = new Join(this, table, null);
		mJoins.add(join);
		return join;
	}

	public Join leftJoin(Class<? extends Model> table) {
		Join join = new Join(this, table, JoinType.LEFT);
		mJoins.add(join);
		return join;
	}

	public Join outerJoin(Class<? extends Model> table) {
		Join join = new Join(this, table, JoinType.OUTER);
		mJoins.add(join);
		return join;
	}

	public Join innerJoin(Class<? extends Model> table) {
		Join join = new Join(this, table, JoinType.INNER);
		mJoins.add(join);
		return join;
	}

	public Join crossJoin(Class<? extends Model> table) {
		Join join = new Join(this, table, JoinType.CROSS);
		mJoins.add(join);
		return join;
	}

	public From where() {
		return where((String) null);
	}

	public From where(Class<? extends Model> table) {
		return where(Cache.getTableName(table));
	}

	public From where(String where) {
		mWhere = where;
		mArguments.clear();

		return this;
	}

	public From where(String where, Object... args) {
		return where(where, Arrays.asList(args));
	}

	public From where(String where, String[] args) {
		//return where(where, Arrays.asList(args));
		return where(where, (Object[]) args);
	}

	public From where(String where, List<Object> args) {
		mWhere = where;
		mArguments.clear();
		mArguments.addAll(args);

		return this;
	}

	public From match(String match, Object... args) {
		return match(match, Arrays.asList(args));
	}

	public From match(String match, String[] args) {
		//return match(match, Arrays.asList(args));
		return match(match, (Object[]) args);
	}

	public From match(String match, List<Object> args) {
		mMatch = match;
		mArguments.clear();
		mArguments.addAll(args);

		return this;
	}

	public From groupBy(String groupBy) {
		mGroupBy = groupBy;
		return this;
	}

	public From having(String having) {
		mHaving = having;
		return this;
	}

	public From orderBy(String orderBy) {
		return orderBy(orderBy, null);
	}

	public From orderBy(String orderBy, String ascendesc) {
		mOrderBy = orderBy;
		mAscendesc = ascendesc;
		return this;
	}

	public From orderByAsc(String orderBy) {
		return orderBy(orderBy, ASC);
	}

	public From orderByDesc(String orderBy) {
		return orderBy(orderBy, DESC);
	}

	public From limit(int limit) {
		return limit(String.valueOf(limit));
	}

	public From limit(String limit) {
		mLimit = limit;
		return this;
	}

	public From offset(int offset) {
		return offset(String.valueOf(offset));
	}

	public From offset(String offset) {
		mOffset = offset;
		return this;
	}

	void addArguments(Object[] args) {
		mArguments.addAll(Arrays.asList(args));
	}

	@Override
	public String toSql() {
		StringBuilder sql = new StringBuilder();
		sql.append(mQueryBase.toSql());
		sql.append("FROM ");
		sql.append(mFrom).append(" ");

		if (mAlias != null) {
			sql.append("AS ");
			sql.append(mAlias);
			sql.append(" ");
		}

		for (Join join : mJoins) {
			sql.append(join.toSql());
		}

		if (mWhere != null) {
			sql.append("WHERE ");
			sql.append(mWhere);
			sql.append(" ");
		}

		if (mMatch != null) {
			sql.append("MATCH ");
			sql.append(mMatch);
			sql.append(" ");
		}

		if (mGroupBy != null) {
			sql.append("GROUP BY ");
			sql.append(mGroupBy);
			sql.append(" ");
		}

		if (mHaving != null) {
			sql.append("HAVING ");
			sql.append(mHaving);
			sql.append(" ");
		}

		if (mOrderBy != null) {
			sql.append("ORDER BY ");
			sql.append(mOrderBy);
			sql.append(" ");
			if (mAscendesc != null) {
				//if (!mOrderBy.equals(" " + DESC) && !mOrderBy.equals(" " + ASC))
				sql.append(mAscendesc);
				sql.append(" ");
			}
		}

		if (mLimit != null) {
			sql.append("LIMIT ");
			sql.append(mLimit);
			sql.append(" ");
		}

		if (mOffset != null) {
			sql.append("OFFSET ");
			sql.append(mOffset);
			sql.append(" ");
		}

		// Don't wast time building the string
		// unless we're going to log it.
		if (Log.isEnabled()) {
			Log.v(sql.toString() + " " + TextUtils.join(",", getArguments()));
		}

		return sql.toString().trim();
	}

	public <T extends Model> CursorList<T> execute() {
		if (mQueryBase instanceof Select) {
			if (!ActiveAndroid.inContentProvider()) {
				return SQLiteUtils.rawQuery(mType, toSql(), getArguments());
			} else {
				if (mGroupBy != null || mHaving != null || mLimit != null)
					throw new IllegalArgumentException(String.format("Query not support by ContentProvider"));

				String[] projection = {};
				for (Field field : Cache.getTableInfo(mType).getFields()) {
					final String fieldName = Cache.getTableInfo(mType).getColumnName(field);
					java.util.Arrays.fill(projection, fieldName);
				}
				Cursor c = Cache.getContext().getContentResolver().query(ContentProvider.createUri(mType, null), projection, mWhere, getArguments(), mOrderBy);
				CursorList<T> entities = com.activeandroid.util.SQLiteUtils.processCursor(mType, c);
				return entities;
			}
		} else if (mQueryBase instanceof Delete) {
			if (!ActiveAndroid.inContentProvider()) {
				SQLiteUtils.execSql(toSql(), getArguments());
				Cache.getContext().getContentResolver().notifyChange(
						ContentProvider.createUri(mType, null), null);
			} else {
				Cache.getContext().getContentResolver().delete(ContentProvider.createUri(mType, null), mWhere, getArguments());
			}
			return null;
		} else {
			if (!ActiveAndroid.inContentProvider()) SQLiteUtils.execSql(toSql(), getArguments());
			else Cache.getContext().getContentResolver().delete(ContentProvider.createUri(mType, null), mWhere, getArguments());
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends Model> T executeSingle() {
		if (ActiveAndroid.inContentProvider()) {
			CursorList<T> list = execute();
			if (list != null && !list.isEmpty()) {
				T item = list.get(0);
				list.close();
				return item;
			} else {
				return null;
			}
		}
		if (mQueryBase instanceof Select) {
			limit(1);
			return (T) SQLiteUtils.rawQuerySingle(mType, toSql(), getArguments());
		} else if (mQueryBase instanceof Delete) {
			limit(1);
			SQLiteUtils.rawQuerySingle(mType, toSql(), getArguments()).delete();
			return null;
		} else {
			SQLiteUtils.execSql(toSql(), getArguments());
			return null;
		}
	}

	public String[] getArguments() {
		final int size = mArguments.size();
		final String[] args = new String[size];

		for (int i = 0; i < size; i++) {
			args[i] = mArguments.get(i).toString();
		}

		return args;
	}
}
