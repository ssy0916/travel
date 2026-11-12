package org.example.traveljavserver;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlMigrationSmokeTest {

    @Test
    void mysqlMigrationCreatesTripsTableInMysqlCompatibilityMode() throws Exception {
        String url = "jdbc:h2:mem:mysql-migration-test;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration/mysql")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var tables = connection.getMetaData().getTables(null, "PUBLIC", "TRIPS", null)) {
            assertTrue(tables.next());
        }
    }
}
