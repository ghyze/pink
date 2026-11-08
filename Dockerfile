# Build stage: compiles the application with the Maven wrapper. Tests are not run here; run them with ./mvnw test.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Download the dependencies first, so this layer is reused as long as pom.xml does not change
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
RUN chmod +x mvnw && ./mvnw --batch-mode --quiet dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode --quiet package -DskipTests

# Run stage: only a JRE and the application jar
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app/target/threekid-*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
