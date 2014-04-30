package org.springframework.jdbc.core.namedparam;

public interface SqlParameterSource {

	static final int TYPE_UNKNOWN = -1;

	Object getValue(String name) throws IllegalArgumentException;
	boolean hasValue(String name);
	public int getSqlType(String name);
	public String getTypeName(String name);
}
