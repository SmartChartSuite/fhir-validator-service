package edu.gatech.chai.servlet;

import java.util.Arrays;

import org.springframework.web.cors.CorsConfiguration;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.narrative.DefaultThymeleafNarrativeGenerator;
import ca.uhn.fhir.narrative.INarrativeGenerator;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import edu.gatech.chai.provider.ValidateProvider;

/**
 * This servlet is the actual FHIR server itself
 */
public class ExampleRestfulServlet extends RestfulServer {

	private static final long serialVersionUID = 1L;

	/**
	 * Constructor
	 */
	public ExampleRestfulServlet() {
		super(FhirContext.forR4()); // This is an R4 server
	}
	
	/**
	 * This method is called automatically when the
	 * servlet is initializing.
	 */
	@Override
	public void initialize() {
		/*
		 * Two resource providers are defined. Each one handles a specific
		 * type of resource.
		 */
		registerProvider(new ValidateProvider());
		
		/*
		 * Use a narrative generator. This is a completely optional step, 
		 * but can be useful as it causes HAPI to generate narratives for
		 * resources which don't otherwise have one.
		 */
		INarrativeGenerator narrativeGen = new DefaultThymeleafNarrativeGenerator();
		getFhirContext().setNarrativeGenerator(narrativeGen);

		/*
		 * Use nice coloured HTML when a browser is used to request the content
		 */
		registerInterceptor(new ResponseHighlighterInterceptor());

		 // Define your CORS configuration. This is an example
		// showing a typical setup. You should customize this
		// to your specific needs
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedHeader("x-fhir-starter");
		config.addAllowedHeader("Origin");
		config.addAllowedHeader("Accept");
		config.addAllowedHeader("X-Requested-With");
		config.addAllowedHeader("Content-Type");

		config.addAllowedOrigin("*");

		config.addExposedHeader("Location");
		config.addExposedHeader("Content-Location");
		config.addExposedHeader("Access-Control-Allow-Origin");
		config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

		// Create the interceptor and register it
		CorsInterceptor interceptor = new CorsInterceptor(config);
		registerInterceptor(interceptor);
		
	}

}
