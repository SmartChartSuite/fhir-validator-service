#Build the Maven project
FROM maven:3.8.4-jdk-11 as builder
COPY . /usr/src/app
WORKDIR /usr/src/app
RUN mvn clean install

#Build the Tomcat container
FROM tomcat:alpine
RUN apk update
RUN apk add zip

# Copy HL7ValidatorService war file to webapps.
COPY catalina.policy $CATALINA_HOME/conf/catalina.policy
COPY --from=builder /usr/src/app/target/HL7ValidatorService.war $CATALINA_HOME/webapps/HL7ValidatorService.war

EXPOSE 8080
