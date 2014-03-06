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

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.text.TextUtils;

import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.util.ReflectionUtils;

public final class TableInfo {
	//////////////////////////////////////////////////////////////////////////////////////
	// PRIVATE MEMBERS
	//////////////////////////////////////////////////////////////////////////////////////

	private Class<? extends Model> mType;
	private String mTableName;
	private String mModule;
	private String mSchema;

	private Map<Field, String> mColumnNames = new LinkedHashMap<Field, String>();
	private Map<String, Column> mColumns = new HashMap<String, Column>();
	private Map<String, Boolean> mReadOnlyColumns = new HashMap<String, Boolean>();

	//////////////////////////////////////////////////////////////////////////////////////
	// CONSTRUCTORS
	//////////////////////////////////////////////////////////////////////////////////////

	public TableInfo(Class<? extends Model> type) {
		mType = type;

		final Table tableAnnotation = type.getAnnotation(Table.class);
		if (tableAnnotation != null) {
			mTableName = tableAnnotation.name();
			mModule = tableAnnotation.module();
		}
		else {
			mTableName = type.getSimpleName();
		}

		List<Field> fields = new LinkedList<Field>(ReflectionUtils.getDeclaredColumnFields(type));
		Collections.reverse(fields);
		
		for (Field field : fields) {
			final Column columnAnnotation = field.getAnnotation(Column.class);
			String columnName = columnAnnotation.name();
			if (TextUtils.isEmpty(columnName)) {
				columnName = field.getName();
			}
			
			mColumnNames.put(field, columnName);
                        mColumns.put(columnName, columnAnnotation);
                        mReadOnlyColumns.put(columnName, columnAnnotation.readOnly());
		}
	}

	public String getSchema() {
		return mSchema;
	}

	public void setSchema(String schema) {
		mSchema = schema;
	}

	//////////////////////////////////////////////////////////////////////////////////////
	// PUBLIC METHODS
	//////////////////////////////////////////////////////////////////////////////////////

	public Class<? extends Model> getType() {
		return mType;
	}

	public String getTableName() {
		return mTableName;
	}

	public String getModule() {
		return mModule;
	}

	public Collection<Field> getFields() {
		return mColumnNames.keySet();
	}

	public String getColumnName(Field field) {
		return mColumnNames.get(field);
	}

	public Column getColumn(String name) {
		return mColumns.get(name);
	}

	public boolean isReadOnlyColumn(String name) {
		return mReadOnlyColumns.get(name);
	}
}
