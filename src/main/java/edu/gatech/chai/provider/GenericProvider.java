package edu.gatech.chai.provider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.utilities.TimeTracker;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.validation.Scanner;
import org.hl7.fhir.validation.ValidationEngine;
import org.hl7.fhir.validation.cli.model.CliContext;
import org.hl7.fhir.validation.cli.utils.EngineMode;
import org.hl7.fhir.validation.cli.utils.Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.Operation;
import edu.gatech.chai.service.MyValidationService;

public class GenericProvider{
	
	private static final Logger logger = LoggerFactory.getLogger(GenericProvider.class);
	
	DateFormat df;
	IParser jsonParser;
	IParser xmlParser;
	ObjectMapper jsonMapper;
	ObjectMapper xmlMapper;

	MyValidationService validationService;
	String sessionId; //This is the validator sessionId that must be preserved.
	
	public GenericProvider() {
		TimeZone tz = TimeZone.getTimeZone("UTC");
		df = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss");
		df.setTimeZone(tz);
		FhirContext ctx = FhirContext.forR4();
		jsonParser = ctx.newJsonParser().setPrettyPrint(true);
		xmlParser = ctx.newXmlParser().setPrettyPrint(true);
		jsonMapper = new ObjectMapper();
		xmlMapper = new XmlMapper();
		validationService = new MyValidationService();
		sessionId = "";
	}
	
	@Operation(name = "$translate", manualRequest = true, manualResponse = true)
	public void translateResource(
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation to genericprovider");
		IParser sourceParser = jsonParser;
		IParser targetParser = xmlParser;
		String contentType = servletRequest.getContentType();
		if(contentType.equalsIgnoreCase("application/json") || contentType.equalsIgnoreCase("application/fhir+json")) {
			sourceParser = jsonParser;
			targetParser = xmlParser;
		}
		else if(contentType.equalsIgnoreCase("application/xml") || contentType.equalsIgnoreCase("application/fhir+xml")) {
			sourceParser = xmlParser;
			targetParser = jsonParser;
		}
		else {
			createErrorOperationOutcome("Incorrect Content-Type Header. Expecting either application/json, application/fhir+json," +
					" application/xml, application/fhir+xml",servletResponse,sourceParser);
			return;
		}
		Resource myParametersResource = null;
		try {
			myParametersResource = (Parameters)sourceParser.parseResource(servletRequest.getInputStream());
		} catch (IOException e) {
			createErrorOperationOutcome("Error serializing request body:" + e.getLocalizedMessage(),servletResponse,sourceParser);
			return;
		}
		if(myParametersResource instanceof Parameters) {
			Parameters parameters = (Parameters)myParametersResource;
			for(ParametersParameterComponent ppc: parameters.getParameter()) {
				if(ppc.getName().equalsIgnoreCase("resource")) {
					Resource translatingResource = ppc.getResource();
					String returnBody = targetParser.encodeResourceToString(translatingResource);
					try {
						servletResponse.getWriter().write(returnBody);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return;
				}
			}
		}
		else {
			createErrorOperationOutcome("Expected Parameters instead found " + myParametersResource.fhirType(),servletResponse,sourceParser);
			return;
		}
		createErrorOperationOutcome("Could not parse Parameters options. Expecting stringParam named 'ig' and resourceParam named 'resource'",servletResponse,sourceParser);
		return;
	}

	@Operation(name = "$validate", manualRequest = true, manualResponse = true)
	public void validateResourceFast(
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) throws Exception {
		//TODO: Handle exceptions
		logger.info("Received $validate-fast operation call");
		IParser currentParser = jsonParser;
		ObjectMapper currentMapper = jsonMapper;
		String contentType = servletRequest.getContentType();
		Resource myParametersResource = null;
		try {
			myParametersResource = (Parameters)currentParser.parseResource(servletRequest.getInputStream());
		} catch (IOException e) {
			createErrorOperationOutcome("Error serializing request body:" + e.getLocalizedMessage(),servletResponse,currentParser);
			return;
		}
		Resource validatingResource = null;
		StringType igParam = null;
		if(myParametersResource instanceof Parameters) {
			Parameters parameters = (Parameters)myParametersResource;
			igParam = (StringType)parameters.getParameter("ig");
			for(ParametersParameterComponent ppc: parameters.getParameter()) {
				if(ppc.getName().equalsIgnoreCase("resource")) {
					validatingResource = ppc.getResource();
				}
			}
		}
		if (validatingResource == null || igParam == null){
			createErrorOperationOutcome("Expected Parameters instead found " + myParametersResource.fhirType(),servletResponse,currentParser);
			return;
		}
		//TimeTracker is required for ValidationService
		TimeTracker tt = new TimeTracker();
		//Setup args as if they were String[] like CLI
		List<String> cliArgsList = new ArrayList<String>();
		if(!igParam.isEmpty()){
			cliArgsList.add("-ig");
			cliArgsList.add(igParam.getValue());
		}
		String fileName = "";
		//You have to write the resource to disk for the service to use it.
		String resourceBody = jsonParser.encodeResourceToString(validatingResource);
		String nowAsISO = df.format(new Date());
		fileName = nowAsISO;
		//Write the source to file so validatorService can use it
		if(contentType.equalsIgnoreCase("application/json") || contentType.equalsIgnoreCase("application/fhir+json")) {
			fileName = fileName + ".json";
			currentParser = jsonParser;
			currentMapper = jsonMapper;
			servletResponse.setContentType("application/json");
		}
		else if(contentType.equalsIgnoreCase("application/xml") || contentType.equalsIgnoreCase("application/fhir+xml")) {
			fileName = fileName + ".xml";
			currentParser = xmlParser;
			currentMapper = jsonMapper;
			servletResponse.setContentType("application/json");
		}
		else {
			createErrorOperationOutcome("Incorrect Content-Type Header. Expecting either application/json, application/fhir+json," +
					" application/xml, application/fhir+xml",servletResponse,currentParser);
			return;
		}
		
		File tempFile = new File(fileName);
		FileOutputStream fos;
		try {
			tempFile.createNewFile();
			fos = new FileOutputStream(tempFile);
			byte[] sourceContentBytes = resourceBody.getBytes();
			fos.write(sourceContentBytes);
			fos.close();
		} catch (IOException e1) {
			OperationOutcome oo = new OperationOutcome();
			oo.addIssue()
			.setSeverity(IssueSeverity.FATAL)
			.setDetails(new CodeableConcept().setText("Could not access and create file:"+tempFile.getAbsolutePath()));
			setResponseAsOperationOutcome(servletResponse,oo,currentParser);
			e1.printStackTrace();
			return;
		}
		cliArgsList.add(fileName); //Note file name is last in the set
		TimeTracker.Session tts = tt.start("Loading");
		//Make CLIContext
		CliContext cliContext = Params.loadCliContext(cliArgsList.toArray(new String[0]));
		//Use the validationservice to set the Server Version
		cliContext.setSv(validationService.determineVersion(cliContext));
		//If the sessionId exists; use it to help initialize. THIS IS WHAT IS SUPPOSED TO CACHE THE PACKAGES
		String definitions = VersionUtilities.packageForVersion(cliContext.getSv()) + "#" + VersionUtilities.getCurrentVersion(cliContext.getSv());
		logger.info("Initializing Validator");
		//Make Validation Engine
		// Comment this out because definitions filename doesn't necessarily contain version (and many not even be 14 characters long).
    	// Version gets spit out a couple of lines later after we've loaded the context
    	sessionId = validationService.initializeValidator(cliContext, definitions, tt, sessionId);
		logger.info("Session id:"+sessionId);
		tts.end();
		logger.info("Retrieving ValidatorEngine");
		ValidationEngine validator = validationService.getValidationEngine(sessionId);
		ArrayNode returnNode = null;
		//Actually do the validation
		logger.info("Starting profile loading.");
		for (String s : cliContext.getProfiles()) {
		//Note maybe a weird versioning issue here. since we're using R5 versions of StructureDefinition and ImplementationGuide
		if (!validator.getContext().hasResource(StructureDefinition.class, s) && !validator.getContext().hasResource(ImplementationGuide.class, s)) {
			logger.info("  Fetch Profile from " + s);
			validator.loadProfile(cliContext.getLocations().getOrDefault(s, s));
		}
		}
		if (cliContext.getMode() == EngineMode.SCAN) {
			logger.info("running scanning mode.");
			Scanner validationScanner = new Scanner(validator.getContext(), validator.getValidator(null), validator.getIgLoader(), validator.getFhirPathEngine());
			validationScanner.validateScan(cliContext.getOutput(), cliContext.getSources());
		} else {
			logger.info("running validate sources mode.");
			returnNode = validationService.validateSources(cliContext, validator);
		}
		//Handle Response
		String responseContent;
		try {
			responseContent = currentMapper.writeValueAsString(returnNode);
		} catch (JsonProcessingException e1) {
			createErrorOperationOutcome(e1.getLocalizedMessage(),servletResponse,currentParser);
			return;
		}
		try {
			servletResponse.getWriter().write(responseContent);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return;
	}
	
	private OperationOutcome createErrorOperationOutcome(String message, HttpServletResponse servletResponse, IParser currentParser) {
		OperationOutcome oo = new OperationOutcome();
		oo.addIssue()
		.setSeverity(IssueSeverity.FATAL)
		.setDetails(new CodeableConcept().setText(message));
		setResponseAsOperationOutcome(servletResponse,oo,currentParser);
		return oo;
	}
	
	private HttpServletResponse setResponseAsOperationOutcome(HttpServletResponse servletResponse, OperationOutcome oo,IParser parser){
		try {
			servletResponse.getWriter().write(parser.encodeResourceToString(oo));
		} catch (DataFormatException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		servletResponse.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
		return servletResponse;
	}
}