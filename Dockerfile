FROM node:16 AS build-node

WORKDIR /opt/jsw

 COPY . .

 RUN npm install && \
    npm run build && \
    rm -rf ../src/main/resources/public/


FROM maven:3.8.6-openjdk-11 AS build-jdk

COPY --from=build-node /opt/jsw /opt/jsw
WORKDIR /opt/jsw

RUN mv build ../src/main/resources/public/ && \
    mvn clean package -U -DskipTests


FROM openjdk:11.0.16

COPY --from=build-jdk /opt/jsw/target/*.jar /opt/program.jar
COPY --from=build-jdk /opt/jsw/src/main/resources/application.yml /opt/conf/application.yml

EXPOSE 8002

CMD ["java","-jar","/opt/program.jar","--spring.config.location=/opt/conf/application.yml"]