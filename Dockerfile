FROM gcontainer/centos7-jdk:openjdk11u8

COPY target/*.jar /opt/program.jar
COPY src/main/resources/application.yml /opt/conf/application.yml

EXPOSE 8002

CMD ["java","-jar","/opt/program.jar","--spring.config.location=/opt/conf/application.yml"]