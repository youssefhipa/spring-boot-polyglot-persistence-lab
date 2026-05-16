FROM eclipse-temurin:25.0.2_10-jdk

WORKDIR /app

COPY target/*.jar app.jar

ENV USER_NAME=Youssef_Hipa
ENV ID=68-21941

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
