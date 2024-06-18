package edu.gatech.chai.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

@Configuration
public class FhirServerConfig {

	@Bean
	public FhirContext getFhirContext() {
		return FhirContext.forR4();
	}
	
	@Bean
	public IParser getJsonParser() {
		return getFhirContext().newJsonParser();
	}
	
	@Bean
	public IParser getXMLParser() {
		return getFhirContext().newXmlParser();
	}
	
	@Bean
	public ObjectMapper getObjectMapper() {
		return new ObjectMapper();
	}
}