# syntax=docker/dockerfile:1
# API image: build with the Maven wrapper, run on a small JRE as a non-root user.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
ENV MAVEN_OPTS=-Xmx512m
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2,sharing=locked ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -q package -DskipTests -Dspotless.check.skip=true \
 && java -Djarmode=tools -jar target/trip-split-*.jar extract --destination /app

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app ./
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC"
ENTRYPOINT ["java", "-jar", "/app/trip-split-0.1.0-SNAPSHOT.jar"]
