package me.yeonhyuk.testfixturecreatorlambda.db.repository.impl;

import me.yeonhyuk.testfixturecreatorlambda.db.JooqConfiguration;
import me.yeonhyuk.testfixturecreatorlambda.db.repository.UsersRepository;
import org.jooq.DSLContext;

public class UsersRepositoryImpl implements UsersRepository {
    private static UsersRepositoryImpl instance;

    private final DSLContext dslContext;

    private UsersRepositoryImpl() {
        this.dslContext = JooqConfiguration.getInstance().getDSLContext();
    }

    public static UsersRepositoryImpl getInstance() {
        if (instance == null) {
            instance = new UsersRepositoryImpl();
        }
        return instance;
    }
}
