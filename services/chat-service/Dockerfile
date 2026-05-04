# ==========================================
# STAGE 1: Build the application using Maven
# ==========================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the pom.xml and source code
COPY pom.xml .
COPY src ./src

# Compile the code and skip tests
RUN mvn clean package -DskipTests

# ==========================================
# STAGE 2: Run the application
# ==========================================
# FIX: Changed 'alpine' to 'jammy' for Apple Silicon support
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy ONLY the built .jar file from Stage 1 and rename it to 'app.jar'
COPY --from=build /app/target/*.jar app.jar

# Expose the port your Chat Service uses
EXPOSE 8081

# FIX: Run the renamed app.jar, not the long snapshot name
ENTRYPOINT ["java", "-jar", "app.jar"]