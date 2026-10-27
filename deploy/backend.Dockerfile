# Runtime-only build from the tested release bundle; no Gradle on the production VPS.
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends openssh-client curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 10001 panel && useradd -u 10001 -g panel -m panel \
    && mkdir /data && chown panel:panel /data
WORKDIR /app
COPY backend/build/libs/amnezia-control-panel.jar /app/app.jar
COPY backend/docker/entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod 755 /app/entrypoint.sh
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
