package org.albi.core.testing;

import com.mysql.cj.jdbc.MysqlDataSource;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public final class TestMySql {
    private TestMySql() {}

    public static MysqlDataSource database() throws java.sql.SQLException {
        String port = System.getenv("VISE_TEST_MYSQL_PORT");
        assumeTrue(port != null, "Set VISE_TEST_MYSQL_PORT to run the MySQL integration tests.");
        var database = new MysqlDataSource();
        database.setServerName("127.0.0.1");
        database.setPort(Integer.parseInt(port));
        database.setDatabaseName("vise_integration");
        database.setUser("vise_test");
        database.setPassword("vise_test");
        database.setSslMode("DISABLED");
        database.setAllowPublicKeyRetrieval(true);
        return database;
    }
}
