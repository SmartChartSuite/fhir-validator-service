#Build the Maven project
FROM maven:3.8.3-openjdk-16 as builder
COPY . /usr/src/app
WORKDIR /usr/src/app
RUN mvn clean install

#Build the Tomcat container
FROM tomcat:jre17-temurin

# Update server.xml file with updated timeout to handle longer request
COPY tomcat_server.xml $CATALINA_HOME/conf/server.xml
# Update security policy to avoid AccessControlException
COPY catalina.policy $CATALINA_HOME/conf/catalina.policy
# Copy HL7ValidatorService war file to webapps.
COPY --from=builder /usr/src/app/target/HL7ValidatorService.war $CATALINA_HOME/webapps/HL7ValidatorService.war

EXPOSE 8080
