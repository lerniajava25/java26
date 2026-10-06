FROM maven:3.10.0-eclipse-temurin-25 as build
COPY ./jakartaee/src /src/
COPY jakartaee/pom.xml pom.xml
RUN mvn package

FROM quay.io/wildfly/wildfly:latest-jdk25
COPY  --from=build ./target/*.war /opt/jboss/wildfly/standalone/deployments/
