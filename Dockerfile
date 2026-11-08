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

# Runtime su Ubuntu (glibc), non Alpine: la libreria webp-imageio carica un libwebp nativo compilato per glibc.
# Su Alpine (musl) la conversione in WebP falliva e la lettura di un .webp mandava in crash la JVM.
FROM eclipse-temurin:17-jre
WORKDIR /app
# Utente non-root
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
COPY --from=builder /app/main-app/target/main-app-*.jar app.jar
USER app:app
# Nessun application.properties nell'immagine (vedi .dockerignore): la configurazione arriva a runtime
# via variabili d'ambiente (relaxed binding Spring), es. SPRING_DATASOURCE_URL, SPRING_DATASOURCE_PASSWORD,
# SPRING_DATA_MONGODB_URI, SPRING_KAFKA_BOOTSTRAP_SERVERS, oppure montando un file e usando
# SPRING_CONFIG_ADDITIONAL_LOCATION=/config/ (es. -v ./config:/config:ro).
# Fuso orario del business: le date/ore senza offset (comande, pagamenti, pausa asporto) sono ore italiane.
# La JVM usa il proprio database dei fusi (non serve tzdata); anche il main() imposta Europe/Rome.
ENV TZ=Europe/Rome
EXPOSE 8085
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Duser.timezone=Europe/Rome", \
  "-jar", "app.jar"]
