# Imagem de produção da API. Os testes (Testcontainers) rodam fora do build: ./mvnw verify
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/confeitaria-api-*.jar app.jar

FROM eclipse-temurin:25-jre
# Usuário sem privilégios; uploads em diretório próprio, fora de qualquer pasta pública.
RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app /var/lib/confeitaria/uploads && chown -R app:app /app /var/lib/confeitaria
WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    APP_UPLOAD_DIR=/var/lib/confeitaria/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /api/public/health HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q UP <&3"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
