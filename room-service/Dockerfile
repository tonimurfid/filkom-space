FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./pom.xml
COPY room-service/pom.xml room-service/pom.xml
COPY reservation-service/pom.xml reservation-service/pom.xml
COPY room-service/src room-service/src
RUN mvn -B -pl room-service -am package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/room-service/target/room-service-1.0.0.jar app.jar
USER 10001
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
