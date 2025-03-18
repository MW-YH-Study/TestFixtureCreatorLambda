package me.yeonhyuk.testfixturecreatorlambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.yeonhyuk.testfixturecreatorlambda.jooq.tables.records.UsersRecord;
import org.jooq.DSLContext;
import org.jooq.JSONFormat;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static me.yeonhyuk.testfixturecreatorlambda.jooq.Tables.USERS;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // DB 연결 정보
    private static final String DB_URL = System.getenv("DB_URL"); // jdbc:postgresql://your-db-host:5432/dbname
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

    // 연결 풀 (Lambda 컨테이너 재사용 시 연결 재활용)
    private static HikariDataSource dataSource;

    static {
        initializeDataSource();
    }

    /**
     * HikariCP 데이터 소스 초기화
     */
    private static void initializeDataSource() {
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

            logger.info("HikariCP connection pool initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize HikariCP", e);
            throw new RuntimeException("Database connection initialization failed", e);
        }
    }

    /**
     * JOOQ DSL 컨텍스트 획득
     */
    private DSLContext getDSLContext() {
        return DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        logger.info("Request received: {}", GSON.toJson(input));

        // 응답 객체 초기화
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setHeaders(Map.of("Content-Type", "application/json"));

        try {
            // HTTP 메서드 확인
            String httpMethod = input.getHttpMethod();
            String path = input.getPath();
            String body = input.getBody();

            // Lambda 컨테이너 재사용 시 연결 유효성 확인
            if (dataSource == null || dataSource.isClosed()) {
                initializeDataSource();
            }

            // 경로에 따른 작업 선택
            if (path.endsWith("/users")) {
                if ("DELETE".equalsIgnoreCase(httpMethod)) {
                    // 모든 사용자 삭제
                    int deletedCount = deleteAllUsers();
                    response.setStatusCode(200);
                    response.setBody(GSON.toJson(Map.of(
                            "message", "All users deleted successfully",
                            "count", deletedCount
                    )));
                } else if ("POST".equalsIgnoreCase(httpMethod)) {
                    // 새 사용자 추가
                    if (body != null && !body.isEmpty()) {
                        // JSON 파싱
                        JsonElement jsonElement = JsonParser.parseString(body);
                        String name = jsonElement.getAsJsonObject().get("name").getAsString();
                        String email = jsonElement.getAsJsonObject().get("email").getAsString();

                        // 사용자 추가
                        int userId = addUser(name, email);

                        response.setStatusCode(201);
                        response.setBody(GSON.toJson(Map.of(
                                "message", "User added successfully",
                                "id", userId,
                                "name", name,
                                "email", email
                        )));
                    } else {
                        response.setStatusCode(400);
                        response.setBody(GSON.toJson(Map.of("error", "Missing request body")));
                    }
                } else if ("GET".equalsIgnoreCase(httpMethod)) {
                    // 모든 사용자 조회
                    String users = getAllUsers();
                    response.setStatusCode(200);
                    response.setBody(users);
                } else {
                    response.setStatusCode(405);
                    response.setBody(GSON.toJson(Map.of("error", "Method not allowed")));
                }
            } else if (path.matches(".*/users/\\d+")) {
                // 개별 사용자 조회 또는 수정
                int userId = extractUserId(path);

                if ("GET".equalsIgnoreCase(httpMethod)) {
                    // 특정 사용자 조회
                    String user = getUserById(userId);
                    if (user != null) {
                        response.setStatusCode(200);
                        response.setBody(user);
                    } else {
                        response.setStatusCode(404);
                        response.setBody(GSON.toJson(Map.of("error", "User not found")));
                    }
                } else if ("DELETE".equalsIgnoreCase(httpMethod)) {
                    // 특정 사용자 삭제
                    boolean deleted = deleteUser(userId);
                    if (deleted) {
                        response.setStatusCode(200);
                        response.setBody(GSON.toJson(Map.of("message", "User deleted successfully")));
                    } else {
                        response.setStatusCode(404);
                        response.setBody(GSON.toJson(Map.of("error", "User not found")));
                    }
                } else {
                    response.setStatusCode(405);
                    response.setBody(GSON.toJson(Map.of("error", "Method not allowed")));
                }
            } else {
                response.setStatusCode(404);
                response.setBody(GSON.toJson(Map.of("error", "Endpoint not found")));
            }

        } catch (Exception e) {
            logger.error("Error processing request: {}", e.getMessage(), e);
            response.setStatusCode(500);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Internal server error");
            errorResponse.put("message", e.getMessage());

            response.setBody(GSON.toJson(errorResponse));
        }

        return response;
    }

    /**
     * URL 경로에서 사용자 ID 추출
     */
    private int extractUserId(String path) {
        String[] parts = path.split("/");
        return Integer.parseInt(parts[parts.length - 1]);
    }

    /**
     * JOOQ를 사용한 모든 사용자 조회
     */
    private String getAllUsers() {
        DSLContext dsl = getDSLContext();
        Result<Record> result = dsl.select().from(USERS).fetch();

        // JOOQ의 JSON 포맷팅 기능을 사용하여 JSON 문자열 생성
        JSONFormat format = new JSONFormat()
                .header(false)
                .format(true);

        return "{\"users\":" + result.formatJSON(format) + "}";
    }

    /**
     * JOOQ를 사용한 특정 사용자 조회
     */
    private String getUserById(int userId) {
        DSLContext dsl = getDSLContext();
        Record record = dsl.select().from(USERS)
                .where(USERS.NAME.eq(userId))
                .fetchOne();

        if (record == null) {
            return null;
        }

        JSONFormat format = new JSONFormat()
                .header(false)
                .format(true);

        return record.formatJSON(format);
    }

    /**
     * JOOQ를 사용한 모든 사용자 삭제
     */
    private int deleteAllUsers() {
        DSLContext dsl = getDSLContext();
        int result = dsl.deleteFrom(USERS).execute();
        logger.info("Deleted {} users", result);
        return result;
    }

    /**
     * JOOQ를 사용한 특정 사용자 삭제
     */
    private boolean deleteUser(int userId) {
        DSLContext dsl = getDSLContext();
        int result = dsl.deleteFrom(USERS)
                .where(USERS.ID.eq(userId))
                .execute();

        logger.info("Deleted user with ID {}: {}", userId, result > 0);
        return result > 0;
    }

    /**
     * JOOQ를 사용한 사용자 추가
     */
    private int addUser(String name, String email) {
        DSLContext dsl = getDSLContext();

        UsersRecord record = dsl.insertInto(USERS)
                .set(USERS.NAME, name)
                .set(USERS.EMAIL, email)
                .returning(USERS.ID)
                .fetchOne();

        int userId = record.getId();
        logger.info("Added user with ID: {}", userId);
        return userId;
    }
}