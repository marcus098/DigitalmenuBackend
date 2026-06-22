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

RUN mvn clean package -Dmaven.test.skip=true -B -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/main-app/target/main-app-*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
