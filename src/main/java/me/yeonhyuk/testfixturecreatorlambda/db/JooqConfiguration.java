package me.yeonhyuk.testfixturecreatorlambda.db;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

public class JooqConfiguration {

    private static JooqConfiguration instance;

    private final HikariDatasourceConfig datasourceConfig;
    private DSLContext dslContext;

    private JooqConfiguration() {
        this.datasourceConfig = HikariDatasourceConfig.getInstance();
    }

    public static JooqConfiguration getInstance() {
        if (instance == null) {
            instance = new JooqConfiguration();
        }
        return instance;
    }

    public DSLContext getDSLContext() {
        if (dslContext == null) {
            this.dslContext = DSL.using(datasourceConfig.getDataSource(), SQLDialect.POSTGRES);
        }
        return this.dslContext;
    }
}
