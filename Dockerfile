#Build the Maven project
FROM docker.io/library/maven:3.9.2-eclipse-temurin-17 AS builder
COPY . /usr/src/app
WORKDIR /usr/src/app
RUN mvn clean install

#!/bin/sh
FROM jetty:9-jre11

USER jetty:jetty

# Default database directory
RUN mkdir -p /var/lib/jetty/target

# Default config directory
RUN mkdir -p /var/lib/jetty/webapps/config

# Uncomment the following line if you wish to deploy both versions of the CQF Ruler HAPI FHIR server.
# Note: You will need to provide the cqf-ruler-dstu3.war in the appropriate location from the main CQF Ruler repository.
# COPY --chown=jetty:jetty ./cqf-ruler-dstu3/target/cqf-ruler-dstu3.war /var/lib/jetty/webapps/cqf-ruler-stu3.war
COPY --from=builder --chown=jetty:jetty /usr/src/app/target/ROOT.war /var/lib/jetty/webapps/ROOT.war
EXPOSE 8080

# Uncomment the appropriate lines below if you intend to not use the Docker Compose deployment.
# ENV SERVER_ADDRESS_DSTU3=""
# ENV SERVER_ADDRESS_R4=""
# ARG SERVER_ADDRESS
# ENV SERVER_ADDRESS=$SERVER_ADDRESS
# ENV JAVA_OPTIONS=""

# COPY ./r4/src/main/resources/hapi.properties /var/lib/jetty/webapps/config/r4.properties

# COPY --chown=jetty:jetty ./scripts/docker-entrypoint-override.sh /docker-entrypoint-override.sh
# ENTRYPOINT [ "sh", "/docker-entrypoint-override.sh" ]