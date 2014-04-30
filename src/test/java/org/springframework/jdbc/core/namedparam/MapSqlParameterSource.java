package org.springframework.jdbc.core.namedparam;

import java.util.HashMap;
import java.util.Map;

public final class MapSqlParameterSource implements SqlParameterSource {

	private final static Object NULL_SENTINEL = new Object();

	private final Map<String,Object> values;

	public MapSqlParameterSource() {
		values = new HashMap<String,Object>();
	}

	public MapSqlParameterSource(String name, Object val) {
		this();
		addValue(name, val);
	}

	public MapSqlParameterSource addValue(String name, Object val) {
		values.put(name,val);
		return this;
	}


	@Override
	public Object getValue(String name) throws IllegalArgumentException {
		Object ret = values.get(name);
		if (ret == null) {
			throw new IllegalArgumentException("No parameter named " + name);
		}
		if (ret == NULL_SENTINEL) {
			return null;
		}
		return ret;
	}

	@Override
	public boolean hasValue(String name) {
		return values.containsKey(name);
	}

	@Override
	public int getSqlType(String name) {
		Object ret = getValue(name);
		if (ret instanceof String) {
			return java.sql.Types.CHAR;
		}
		return TYPE_UNKNOWN;
	}

	@Override
	public String getTypeName(String name) {
		Object ret = getValue(name);
		if (ret instanceof String) {
			return "CHAR";
		}
		return "UNKNOWN";
	}


}
