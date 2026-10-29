FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./pom.xml
COPY room-service/pom.xml room-service/pom.xml
COPY reservation-service/pom.xml reservation-service/pom.xml
COPY reservation-service/src reservation-service/src
RUN mvn -B -pl reservation-service -am package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/reservation-service/target/reservation-service-1.0.0.jar app.jar
USER 10001
EXPOSE 8082
ENTRYPOINT ["java","-jar","app.jar"]
