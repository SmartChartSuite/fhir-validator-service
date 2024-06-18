# HL7Validator
### Introduction
HL7Validator is a api-enabled web service of the [FHIR validator tool provided by hapi fhir](https://confluence.hl7.org/display/FHIR/Using+the+FHIR+Validator).
This service allows tooling to pass existing FHIR records (as json or xml) and retrieve validation issues relating to a specific implementation guide, as well as a details report with severity, location, and fhirpath to diagnose the issue.
### Project Support Features
* Validate operation for full report of record validation
* OperationOutcome response which can be deserialized by common FHIR client tools
* Translate operation for ease of use for translating between xml and json if needed

### Software Prerequisites
* [A jdk environment of version 11 or higher](https://www.oracle.com/java/technologies/downloads/)
* [The maven compile and build tool](https://maven.apache.org/)
### Installation Guide
* Clone this repository
* The develop branch is the most stable branch at any given time. Stable releases are released [here](https://github.gatech.edu/HDAP/HL7ValidatorService/releases)
* Access the root directory of the project
* run the maven-jetty-plugin by running ```mvn jetty:run```
### API Endpoints
| HTTP Verbs | Endpoints | Body Parameters | Action |
| --- | --- | --- | --- |
| POST | /fhir/validate | resource,ig,format,includeFormattedResource | Validate FHIR resource against provided Implementation Guide(IG) |
| POST | /fhir/translate | resource | Translate from json-to-xml or xml-to-json based on the content-type header |
#### fhir/validate request
the fhir/validate endpoint uses a POST body of [FHIR parameters as described in the FHIR spec](https://hl7.org/fhir/R4/parameters.html)
This consist of a post body with an array of parameter components, each component containing a name and a valueof a specific type
| Parameter Name | Parameter Value/Type | Description |
| --- | --- | --- |
| resource | resource | The fhir resource to be validated by the server. Inserted in the resource field as either json or xml |
| ig | valueString | The ig version with a hastag(#) delimited set of namespace in version. If you're unsure what version your IG is using, check the footer of the page for the versioned guide package name |
| format | valueString | A required parameter to hint to the validate which format to use when validating. Accepted values are: application/json, application/fhir+json, application/xml, application/fhir+xml |
| includeFormattedResource | valueBoolean | An optional parameter, when set to 'true' returns the parsed resource as an extension within the returned OperationOutcome. Useful for determining if resources were parsed as intended |
#### fhir/translate request
the fhir/translate endpoint uses a POST body of [FHIR parameters as described in the FHIR spec](https://hl7.org/fhir/R4/parameters.html)
This consist of a post body with an array of parameter components, each component containing a name and a valueof a specific type
| Parameter Name | Parameter Value/Type | Description |
| --- | --- | --- |
| resource | resource | The fhir resource to be translated by the server. Inserted in the resource field as either json or xml |
| format | valueString | A required parameter to hint to the validate which format to use when translating. Accepted values are: application/json, application/fhir+json, application/xml, application/fhir+xml |
### Technologies Used
* Built on top of the base [hapi-fhir framework](https://hapifhir.io/)
### Authors
* [Michael Riley](https://github.com/blackdevelopa)
### License
This project is available for use under the Apache License.