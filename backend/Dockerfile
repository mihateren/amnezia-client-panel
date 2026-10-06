FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties lombok.config ./
COPY src src
COPY docs-java docs-java
RUN --mount=type=cache,target=/root/.gradle sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends openssh-client curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 10001 panel && useradd -u 10001 -g panel -m panel \
    && mkdir /data && chown panel:panel /data
WORKDIR /app
COPY --from=build /workspace/build/libs/amnezia-control-panel.jar /app/app.jar
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod 755 /app/entrypoint.sh
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]

