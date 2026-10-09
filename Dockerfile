FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Copy pom files first for dependency layer caching
COPY pom.xml .
COPY common/pom.xml common/
COPY main-app/pom.xml main-app/

RUN mvn dependency:go-offline -B -q || true

# Copy sources
COPY common/src common/src/
COPY main-app/src main-app/src/

RUN mvn clean package -DskipTests -B -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Utente non-root
RUN addgroup -S app && adduser -S -G app -H -s /sbin/nologin app
COPY --from=builder /app/main-app/target/main-app-*.jar app.jar
USER app:app
# Nessun application.properties nell'immagine (vedi .dockerignore): la configurazione arriva a runtime
# via variabili d'ambiente (relaxed binding Spring), es. SPRING_DATASOURCE_URL, SPRING_DATASOURCE_PASSWORD,
# SPRING_DATA_MONGODB_URI, SPRING_KAFKA_BOOTSTRAP_SERVERS, oppure montando un file e usando
# SPRING_CONFIG_ADDITIONAL_LOCATION=/config/ (es. -v ./config:/config:ro).
EXPOSE 8085
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
