# 백엔드 이미지 — Gradle 빌드와 실행을 분리한다 (실행 이미지에 JDK·소스·Gradle 캐시를 남기지 않음)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 레이어를 먼저 굳혀 소스만 바뀐 재빌드에서 Gradle 다운로드를 반복하지 않는다
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src ./src
# t3.micro(1GiB)에서 Gradle 데몬을 띄우면 OOM으로 빌드가 죽는다. --no-daemon과 힙 상한이 필수다
ENV GRADLE_OPTS="-Xmx512m -Dorg.gradle.daemon=false"
RUN ./gradlew --no-daemon -x test bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# 컨테이너에 1GiB뿐이라 JVM 기본 힙(물리 메모리의 1/4)에 맡기지 않고 명시한다
ENV JAVA_TOOL_OPTIONS="-Xmx384m -XX:MaxMetaspaceSize=192m"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
