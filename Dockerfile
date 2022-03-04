LABEL maintainer="Michael.Riley@gtri.gatech.edu"
#Build the Maven project
FROM maven:3.6.1-alpine as builder
COPY . /usr/src/app
WORKDIR /usr/src/app
RUN mvn clean install

#Build the Tomcat container
FROM tomcat:alpine
RUN apk update
RUN apk add zip

# Copy GT-FHIR war file to webapps.
COPY --from=builder /usr/src/app/HL7ValidatorService/target/omoponfhir-stu3-server.war $CATALINA_HOME/webapps/omoponfhir3.war

EXPOSE 8080