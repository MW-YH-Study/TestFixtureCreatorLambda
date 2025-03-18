FROM amazoncorretto:21 as build

# 작업 디렉토리 설정
WORKDIR /build

# Gradle 래퍼와 소스 코드 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src

# 실행 권한 부여
RUN chmod +x gradlew

# 빌드
RUN ./gradlew shadowJar

# 런타임 이미지
FROM public.ecr.aws/lambda/java:21

# 빌드 단계에서 생성된 JAR 파일 복사
COPY --from=build /build/build/libs/lambda-java-function.jar ${LAMBDA_TASK_ROOT}/lib/

# Lambda 핸들러 설정
CMD ["me.yeonhyuk.testfixturecreatorlambda.App::handleRequest"]