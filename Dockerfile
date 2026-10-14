# syntax=docker/dockerfile:1
#
# Zamu container image (Render "Docker" runtime, or any container host).
#   docker build -t zamu .
#   docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=h2 zamu          # throwaway demo, in-memory data
#   docker run -p 8080:8080 --env-file .env zamu                        # mysql profile, see .env.example
# The image builds everything from source, including the Tailwind stylesheet, so nothing generated needs to
# be committed. It does not use the Maven wrapper.

# ---- Build -------------------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies first, so source changes don't re-download them.
COPY pom.xml .
RUN mvn -B -q -Dskip.frontend=true dependency:go-offline || true

COPY src ./src
# Tests run in CI (they need Docker for MySQL); the image build only packages.
RUN mvn -B -DskipTests package \
 && cp target/zamu-*.jar /build/app.jar

# ---- Run ---------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system zamu && useradd --system --gid zamu --home /app zamu
COPY --from=build --chown=zamu:zamu /build/app.jar /app/app.jar
USER zamu

# Render sets PORT (Zamu reads it: server.port=${PORT:8080}). TZ stays UTC: Zamu stores UTC and shows Nairobi time.
ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
