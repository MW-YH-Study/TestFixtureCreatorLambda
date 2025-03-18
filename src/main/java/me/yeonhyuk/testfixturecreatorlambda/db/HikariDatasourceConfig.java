package me.yeonhyuk.testfixturecreatorlambda.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class HikariDatasourceConfig {
    // DB 연결 정보
    private static final String DB_URL = System.getenv("DB_URL"); // jdbc:postgresql://your-db-host:5432/dbname
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

    // 연결 풀 (Lambda 컨테이너 재사용 시 연결 재활용)
    private HikariDataSource dataSource;

    private static HikariDatasourceConfig instance;

    // Singleton 구현
    private HikariDatasourceConfig() {
    }

    public static HikariDatasourceConfig getInstance() {
        if (instance == null) {
            instance = new HikariDatasourceConfig();
            instance.initializeDataSource();
        }
        return instance;
    }

    public HikariDataSource getDataSource() {
        // Lambda 컨테이너 재사용 시 연결 유효성 확인
        if (dataSource == null || dataSource.isClosed()) {
            initializeDataSource();
        }
        return dataSource;
    }

    /**
     * HikariCP 데이터 소스 초기화
     */
    private void initializeDataSource() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DB_URL);
            config.setUsername(DB_USER);
            config.setPassword(DB_PASSWORD);
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setIdleTimeout(600000); // 10분
            config.setMaxLifetime(1800000); // 30분
            config.setConnectionTimeout(30000); // 30초
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);

        } catch (Exception e) {
            throw new RuntimeException("Database connection initialization failed", e);
        }
    }

}
