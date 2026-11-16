FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/safeelo-1.0.0.jar /app/safeelo.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/safeelo.jar"]
