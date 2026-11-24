FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -pl ticketiq-app -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/ticketiq-app/target/ticketiq-app-2.0.0.jar /app/ticketiq-app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh

ENV PORT=8080 \
    AI_PROVIDER=mock \
    AI_ENABLED=true \
    TICKETIQ_SEED=true \
    TICKETIQ_DB_URL="jdbc:h2:file:/app/data/ticketiq;DB_CLOSE_ON_EXIT=FALSE"

VOLUME ["/app/data"]
EXPOSE 8080
ENTRYPOINT ["/bin/sh", "/app/docker-entrypoint.sh"]
