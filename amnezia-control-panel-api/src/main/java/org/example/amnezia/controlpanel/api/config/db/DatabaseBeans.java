package org.example.amnezia.controlpanel.api.config.db;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DatabaseBeans {

    private static final String DATA_SOURCE_PROPERTIES = ".DataSourceProperties";
    private static final String DATA_SOURCE = ".DataSource";
    private static final String NAMED_JDBC_TEMPLATE = ".NamedJdbcTemplate";
    private static final String TRANSACTION_MANAGER = ".TransactionManager";
    private static final String LIQUIBASE = ".Liquibase";

    // SQLite
    public static final String SQLITE_PREFIX = "sqlite";

    public static final String SQLITE_DATASOURCE_PROPERTIES_BEAN = SQLITE_PREFIX + DATA_SOURCE_PROPERTIES;
    public static final String SQLITE_DATASOURCE_BEAN = SQLITE_PREFIX + DATA_SOURCE;
    public static final String SQLITE_NAMED_JDBC_TEMPLATE_BEAN = SQLITE_PREFIX + NAMED_JDBC_TEMPLATE;
    public static final String SQLITE_TX_MANAGER_BEAN = SQLITE_PREFIX + TRANSACTION_MANAGER;
    public static final String SQLITE_LIQUIBASE_BEAN = SQLITE_PREFIX + LIQUIBASE;
}