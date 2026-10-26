package com.scrapescape.game;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=${MYSQL_TEST_URL}")
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches=".*scrap_escape_test.*")
class DatabaseMigrationIntegrationTest {
    @Autowired HikariDataSource datasource;
    @Autowired JdbcTemplate jdbc;
    @Test void freshDatabaseMigratesAndSecondStartupDoesNotReseed() { checkMigration(false); }
    @Test void legacyDatabaseRetainsGameRecipesAndSplitsOversizedStacks() { checkMigration(true); }
    void checkMigration(boolean legacy) {
        String schema="scrap_escape_test_migration_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4");
        try {
            String catalog=jdbc.queryForObject("SELECT DATABASE()",String.class);
            String url=datasource.getJdbcUrl().replace("/"+catalog,"/"+schema);
            assertNotEquals(datasource.getJdbcUrl(),url);
            var target=new DriverManagerDataSource(url,datasource.getUsername(),datasource.getPassword());
            var db=new JdbcTemplate(target);String game=UUID.randomUUID().toString();
            if(legacy) {
                new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__Initial_game.sql")).execute(target);
                db.update("INSERT INTO game(game_id,elapsed_hours) VALUES (?,12)",game);
                db.update("INSERT INTO game_repair(game_id,part_id) VALUES (?,2)",game);
                db.update("INSERT INTO item_batch(game_id,material_id,quantity,storage_type,acquired_hour,item_condition) VALUES (?,1,14,'CHEST',7,'DIRTY')",game);
            }
            var flyway=Flyway.configure().dataSource(target).baselineOnMigrate(true).baselineVersion("1").load();
            flyway.migrate();
            assertEquals(10,db.queryForObject("SELECT COUNT(*) FROM material",Integer.class));
            assertEquals(6,db.queryForObject("SELECT COUNT(*) FROM location",Integer.class));
            assertEquals(0,flyway.migrate().migrationsExecuted);
            if(legacy) {
                assertEquals(12,db.queryForObject("SELECT elapsed_hours FROM game WHERE game_id=?",Integer.class,game));
                assertEquals(1,db.queryForObject("SELECT rules_version FROM game WHERE game_id=?",Integer.class,game));
                assertEquals(List.of(6,6,2),db.queryForList("SELECT quantity FROM item_batch WHERE game_id=? ORDER BY batch_id",Integer.class,game));
                assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM item_batch WHERE game_id=? AND acquired_hour=7 AND item_condition='DIRTY'",Integer.class,game));
                assertEquals("통신 장치",db.queryForObject("SELECT part_name FROM game_repair WHERE game_id=?",String.class,game));
                assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM game_recipe WHERE game_id=?",Integer.class,game));
            }
        } finally { jdbc.execute("DROP DATABASE "+schema); }
    }
    @Test void versionFourGamesUpgradeToSecretRoomsWithoutResettingProgress() {
        String schema="scrap_escape_test_migration_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4");
        try {
            String catalog=jdbc.queryForObject("SELECT DATABASE()",String.class);
            String url=datasource.getJdbcUrl().replace("/"+catalog,"/"+schema);assertNotEquals(datasource.getJdbcUrl(),url);
            var target=new DriverManagerDataSource(url,datasource.getUsername(),datasource.getPassword());
            Flyway.configure().dataSource(target).target("4").load().migrate();
            var db=new JdbcTemplate(target);String id=UUID.randomUUID().toString();
            db.update("INSERT INTO game(game_id,rules_version,elapsed_hours,stamina,contamination) VALUES (?,2,12,7,25)",id);
            db.update("INSERT INTO item_batch(game_id,material_id,quantity,storage_type,acquired_hour,item_condition) VALUES (?,1,4,'INVENTORY',6,'DIRTY')",id);
            var flyway=Flyway.configure().dataSource(target).load();assertEquals(2,flyway.migrate().migrationsExecuted);
            assertEquals(0,db.queryForObject("SELECT secret_key_amount FROM game WHERE game_id=?",Integer.class,id));
            assertEquals(12,db.queryForObject("SELECT elapsed_hours FROM game WHERE game_id=?",Integer.class,id));
            assertEquals(7,db.queryForObject("SELECT stamina FROM game WHERE game_id=?",Integer.class,id));
            assertEquals(25,db.queryForObject("SELECT contamination FROM game WHERE game_id=?",Integer.class,id));
            assertEquals(4,db.queryForObject("SELECT quantity FROM item_batch WHERE game_id=?",Integer.class,id));
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM game_secret_room WHERE game_id=?",Integer.class,id));
            assertEquals(0,flyway.migrate().migrationsExecuted);
        } finally {jdbc.execute("DROP DATABASE "+schema);}
    }

}
