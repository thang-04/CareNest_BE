# Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 carenest
COPY --from=build /app/target/carenest-be-*.jar app.jar
USER carenest
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
