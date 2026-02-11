# Use Ubuntu for the build stage as requested
FROM ubuntu:22.04 AS build

# Install Java 21 and Maven
RUN apt-get update && \
    apt-get install -y openjdk-21-jdk maven && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Use a lightweight JRE image for runtime
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Environment variables (can be overridden at runtime)
ENV SERVER_PORT=8080
ENV STORAGE_TYPE=local
ENV LOCAL_STORAGE_PATH=/app/data

# Create volume for local storage
VOLUME /app/data

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
