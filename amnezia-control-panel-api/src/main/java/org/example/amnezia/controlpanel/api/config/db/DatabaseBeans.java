package org.example.amnezia.controlpanel.api.config.db;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DatabaseBeans {

    private static final String DATA_SOURCE_PROPERTIES = ".DataSourceProperties";
    private static final String DATA_SOURCE = ".DataSource";
    private static final String NAMED_JDBC_TEMPLATE = ".NamedJdbcTemplate";
    private static final String TRANSACTION_MANAGER = ".TransactionManager";

    // Postgres
    public static final String POSTGRES_PREFIX = "postgres";

    public static final String POSTGRES_DATASOURCE_PROPERTIES_BEAN = POSTGRES_PREFIX + DATA_SOURCE_PROPERTIES;
    public static final String POSTGRES_DATASOURCE_BEAN = POSTGRES_PREFIX + DATA_SOURCE;
    public static final String POSTGRES_NAMED_JDBC_TEMPLATE_BEAN = POSTGRES_PREFIX + NAMED_JDBC_TEMPLATE;
    public static final String POSTGRES_TX_MANAGER_BEAN = POSTGRES_PREFIX + TRANSACTION_MANAGER;
}