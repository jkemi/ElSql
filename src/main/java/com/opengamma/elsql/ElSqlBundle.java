/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.elsql;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * A bundle of elsql formatted SQL.
 * <p>
 * The bundle encapsulates the SQL needed for a particular feature.
 * This will typically correspond to a data access object, or set of related tables.
 * <p>
 * This class is immutable and thread-safe.
 */
public final class ElSqlBundle {

  /**
   * The map of known elsql.
   */
  private final Map<String, NameSqlFragment> _map;
  /**
   * The config.
   */
  private final ElSqlConfig _config;

  /**
   * Loads external SQL based for the specified type.
   * <p>
   * The type is used to identify the location and name of the ".elsql" file.
   * The loader will attempt to find and use two files, using the full name of
   * the type to query the class path for resources.
   * <p>
   * The first resource searched for is optional - the file will have the suffix
   * "-ConfigName.elsql", such as "com/foo/Bar-MySql.elsql".
   * The second resource searched for is mandatory - the file will just have the
   * ".elsql" suffix, such as "com/foo/Bar.elsql".
   * <p>
   * The config is designed to handle some, but not all, database differences.
   * Other differences should be handled by creating and using a database specific
   * override file (the first optional resource is the override file).
   *
   * @param config  the config, not null
   * @param type  the type, not null
   * @return the bundle, not null
   * @throws java.io.IOException if unable to read input
   * @throws IllegalArgumentException if the input cannot be parsed
   */
  public static ElSqlBundle of(ElSqlConfig config, Class<?> type) throws IOException {
    if (config == null) {
      throw new IllegalArgumentException("Config must not be null");
    }
    if (type == null) {
      throw new IllegalArgumentException("Type must not be null");
    }

    InputStream baseResource = type.getResourceAsStream(type.getSimpleName() + ".elsql");
	if (baseResource == null) {
	  throw new FileNotFoundException("Resource not found: " + type.getSimpleName() + ".elsql");
	}
    try {
      InputStream configResource = type.getResourceAsStream(type.getSimpleName() + "-" + config.getName() + ".elsql");
      if (configResource != null) {
        try {
          return parse(config, baseResource, configResource);
        } finally {
          configResource.close();
        }
      } else {
        return parse(config, baseResource);
      }
	} finally {
		baseResource.close();
	}
  }

  /**
   * Parses a bundle from a resource locating a file, specify the config.
   * <p>
   * This parses a list of resources. Named blocks in later resources override
   * blocks with the same name in earlier resources.
   * <p>
   * The config is designed to handle some, but not all, database differences.
   * Other differences are handled via the override resources passed in.
   * 
   * @param config  the config to use, not null
   * @param resources  the resources to load, not null
   * @return the external identifier, not null
   * @throws IllegalArgumentException if the input cannot be parsed
   */
  public static ElSqlBundle parse(ElSqlConfig config, InputStream... resources) throws IOException {
    if (config == null) {
      throw new IllegalArgumentException("Config must not be null");
    }
    if (resources == null) {
      throw new IllegalArgumentException("Resources must not be null");
    }
    return parseResource(resources, config);
  }

  private static ElSqlBundle parseResource(InputStream[] resources, ElSqlConfig config) throws IOException {
    List<List<String>> files = new ArrayList<List<String>>();
    for (InputStream resource : resources) {
      List<String> lines = loadResource(resource);
      files.add(lines);
    }
    return parse(files, config);
  }

  // package scoped for testing
  static ElSqlBundle parse(List<String> lines) {
    ArrayList<List<String>> files = new ArrayList<List<String>>();
    files.add(lines);
    return parse(files, ElSqlConfig.DEFAULT);
  }

  private static ElSqlBundle parse(List<List<String>> files, ElSqlConfig config) {
    Map<String, NameSqlFragment> parsed = new LinkedHashMap<String, NameSqlFragment>();
    for (List<String> lines : files) {
      ElSqlParser parser = new ElSqlParser(lines);
      parsed.putAll(parser.parse());
    }
    return new ElSqlBundle(parsed, config);
  }

  private static List<String> loadResource(InputStream in) throws IOException {
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
      List<String> list = new ArrayList<String>();
      String line = reader.readLine();
      while (line != null) {
          list.add(line);
          line = reader.readLine();
      }
      return list;
  }

  /**
   * Creates an instance..
   *
   * @param map  the map of names, not null
   * @param config  the config to use, not null
   */
  private ElSqlBundle(Map<String, NameSqlFragment> map, ElSqlConfig config) {
    if (map == null) {
      throw new IllegalArgumentException("Fragment map must not be null");
    }
    if (config == null) {
      throw new IllegalArgumentException("Config must not be null");
    }
    _map = map;
    _config = config;
  }

  //-------------------------------------------------------------------------
  /**
   * Gets the configuration object.
   * 
   * @return the config, not null
   */
  public ElSqlConfig getConfig() {
    return _config;
  }

  /**
   * Returns a copy of this bundle with a different configuration.
   * <p>
   * This does not reload the underlying resources.
   * 
   * @param config  the new config, not null
   * @return a bundle with the config updated, not null
   */
  public ElSqlBundle withConfig(ElSqlConfig config) {
    return new ElSqlBundle(_map, config);
  }

  //-------------------------------------------------------------------------
  /**
   * Finds SQL for a named fragment key, without specifying parameters.
   * <p>
   * This finds, processes and returns a named block from the bundle.
   * Note that if the SQL contains tags that depend on variables, like AND or LIKE,
   * then an error will be thrown.
   * 
   * @param name  the name, not null
   * @return the SQL, not null
   * @throws IllegalArgumentException if there is no fragment with the specified name
   * @throws RuntimeException if a problem occurs
   */
  public String getSql(String name) {
    return getSql(name, new EmptySource());
  }

  /**
   * Finds SQL for a named fragment key.
   * <p>
   * This finds, processes and returns a named block from the bundle.
   * 
   * @param name  the name, not null
   * @param paramSource  the Spring SQL parameters, not null
   * @return the SQL, not null
   * @throws IllegalArgumentException if there is no fragment with the specified name
   * @throws RuntimeException if a problem occurs
   */
  public String getSql(String name, SqlParameterSource paramSource) {
    NameSqlFragment fragment = getFragment(name);
    StringBuilder buf = new StringBuilder(1024);
    fragment.toSQL(buf, this, paramSource, -1);
    return buf.toString();
  }

  //-------------------------------------------------------------------------
  /**
   * Gets a fragment by name.
   * 
   * @param name  the name, not null
   * @return the fragment, not null
   * @throws IllegalArgumentException if there is no fragment with the specified name
   */
  NameSqlFragment getFragment(String name) {
    NameSqlFragment fragment = _map.get(name);
    if (fragment == null) {
      throw new IllegalArgumentException("Unknown fragment name: " + name);
    }
    return fragment;
  }

  //-------------------------------------------------------------------------
  /**
   * An empty parameter source.
   * Using this reduces coupling with the Spring librray.
   */
  private final class EmptySource implements SqlParameterSource {
    @Override
    public boolean hasValue(String field) {
      return false;
    }
  
    @Override
    public int getSqlType(String field) {
      return TYPE_UNKNOWN;
    }
  
    @Override
    public String getTypeName(String field) {
      throw new IllegalArgumentException();
    }
  
    @Override
    public Object getValue(String field) throws IllegalArgumentException {
      return null;
    }
  }

}
