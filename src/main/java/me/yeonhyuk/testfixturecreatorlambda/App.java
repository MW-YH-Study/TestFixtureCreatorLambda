package me.yeonhyuk.testfixturecreatorlambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import me.yeonhyuk.testfixturecreatorlambda.db.repository.impl.UsersRepositoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final UsersRepositoryImpl usersRepository = UsersRepositoryImpl.getInstance();

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

}