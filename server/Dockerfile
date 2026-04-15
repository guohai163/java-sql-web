FROM eclipse-temurin:17-jre

WORKDIR /opt/jsw

COPY target/*.jar /opt/jsw/program.jar

EXPOSE 8002

CMD ["java", "-jar", "/opt/jsw/program.jar"]
