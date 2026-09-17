FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .

RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src

RUN mvn -q -DskipTests package


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/ditec-assistencia.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT:-8080}"]