FROM gradle:8-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN ./gradlew buildFatJar --no-daemon

FROM openjdk:21
EXPOSE 8080:8080
RUN mkdir /app

ENV JAVA_TOOL_OPTIONS=-javaagent:/app/opentelemetry-javaagent.jar
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

COPY --from=build /home/gradle/src/build/libs/*.jar /app/lessonarchiver-api-all.jar
CMD ["java", "-Dio.ktor.internal.disable.sfg=true", "-Dotel.instrumentation.common.default-enabled=false", "-jar", "/app/lessonarchiver-api-all.jar"]
