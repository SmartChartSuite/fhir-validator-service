package edu.gatech.chai.provider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.Constants;
import edu.gatech.chai.service.MyValidationService;

public class ValidateProvider{
	
	private static final Logger logger = LoggerFactory.getLogger(ValidateProvider.class);
	
	DateFormat df;
	IParser jsonParser;
	IParser r5Parser;
	IParser xmlParser;
	ObjectMapper jsonMapper;
	ObjectMapper xmlMapper;

	MyValidationService validationService;
	DefaultCorsProcessor defaultCorsProcessor;
	String sessionId; //This is the validator sessionId that must be preserved.
	String base_supported_igs;
	Pattern lineAndColPattern = Pattern.compile("^\\(line\\s*(?<line>\\d+),\\s*col(?<col>\\d+)\\)$");
	public ValidateProvider(FhirContext ctx, String base_supported_igs) {
		TimeZone tz = TimeZone.getTimeZone("UTC");
		df = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss");
		df.setTimeZone(tz);
		jsonParser = ctx.newJsonParser().setPrettyPrint(true);
		xmlParser = ctx.newXmlParser().setPrettyPrint(true);
		r5Parser = FhirContext.forR5().newJsonParser().setPrettyPrint(true);
		jsonMapper = new ObjectMapper();
		xmlMapper = new XmlMapper();
		validationService = new MyValidationService();
		defaultCorsProcessor = new DefaultCorsProcessor();
		sessionId = "";
		logger.info("base_supported_igs:"+base_supported_igs);
		this.base_supported_igs = base_supported_igs;
	}

	@Operation(name = "$packages", manualRequest = true, manualResponse = true, global = true, idempotent = true)
	public void testConfiguration(HttpServletRequest servletRequest,HttpServletResponse servletResponse) throws Exception{
		ArrayNode jsonOutput = convertIGStringsToJson(Arrays.asList(base_supported_igs.split(",")));
		servletResponse.getWriter().write(jsonMapper.writeValueAsString(jsonOutput));
		return;
	}
	
	@Operation(name = "$translate", manualRequest = true, manualResponse = true)
	public void translateResource(
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) throws Exception{
		logger.info("Received $translate operation to genericprovider");
		boolean corsProcessed = defaultCorsProcessor.processRequest(createDefaultCorsConfig(), servletRequest, servletResponse);
		if(!corsProcessed){
			return;
		}
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
					postProcess(servletResponse);
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

	@Operation(name = "$validate", global = true)
	public OperationOutcome validateResource(
			@OperationParam(name = "ig", min = 1) StringType ig,
			@OperationParam(name = "format", min = 1) StringType format,
			@OperationParam(name = "sct", min = 1) StringType sct,
			@OperationParam(name = "includeFormattedResource") BooleanType includeFormattedResource,
			@OperationParam(name = "resource", min = 1) IBaseResource resource) throws Exception {
		logger.info("Received $validate operation call");
		logger.info("igParam:"+ig.toString());
		logger.info("formatParam:"+format.toString());
		IParser currentParser = jsonParser;
		ObjectMapper currentMapper = jsonMapper;
		//application/xml; utf-8
		if(format.getValue().equalsIgnoreCase("application/json") || format.getValue().equalsIgnoreCase("application/fhir+json")) {
			currentParser = jsonParser;
			currentMapper = jsonMapper;
		}
		else if(format.getValue().equalsIgnoreCase("application/xml") || format.getValue().equalsIgnoreCase("application/fhir+xml")) {
			currentParser = xmlParser;
			currentMapper = jsonMapper;
		}
		else {
			return createErrorOperationOutcome("Incorrect format parameter. Expecting either application/json, application/fhir+json," +
					" application/xml, application/fhir+xml",currentParser);
		}
		//TimeTracker is required for ValidationService
		TimeTracker tt = new TimeTracker();
		//Setup args as if they were String[] like CLI
		List<String> cliArgsList = new ArrayList<String>();
		if(!ig.isEmpty()){
			cliArgsList.add("-ig");
			cliArgsList.add(ig.getValue());
		}
		//Establish sct default value of "us"
		if(sct == null || sct.isEmpty()){
			sct = new StringType("us"); 
		}
		if(!sct.isEmpty()){
			cliArgsList.add("-sct");
			cliArgsList.add(sct.getValue());
		}
		String fileName = "";
		//You have to write the resource to disk for the service to use it.
		String nowAsISO = df.format(new Date());
		fileName = nowAsISO;
		//Write the source to file so validatorService can use it
		if(format.getValue().equalsIgnoreCase("application/json") || format.getValue().equalsIgnoreCase("application/fhir+json")) {
			fileName = fileName + ".json";
		}
		else if(format.getValue().equalsIgnoreCase("application/xml") || format.getValue().equalsIgnoreCase("application/fhir+xml")) {
			fileName = fileName + ".xml";
		}
		String resourceBody = currentParser.encodeResourceToString(resource);
		
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
			e1.printStackTrace();
			return oo;
		}
		cliArgsList.add(fileName); //Note file name is last in the set
		TimeTracker.Session tts = tt.start("Loading");
		//Make CLIContext
		CliContext cliContext = Params.loadCliContext(cliArgsList.toArray(new String[0]));
		//
		logger.info("CLIContext:"+cliContext.toString());
		//Use the validationservice to set the Server Version
		logger.info("ValidationService determineVersion:"+validationService.determineVersion(cliContext));
		cliContext.setSv(validationService.determineVersion(cliContext));
		//If the sessionId exists; use it to help initialize without recreating the context
		String definitions = VersionUtilities.packageForVersion(cliContext.getSv()) + "#" + VersionUtilities.getCurrentVersion(cliContext.getSv());
		logger.info("Initializing Validator");
		logger.info("definitions:"+definitions);
		//Make Validation Engine
    	sessionId = validationService.initializeValidator(cliContext, definitions, tt, sessionId);
		logger.info("Session id:"+sessionId);
		tts.end();
		//TODO: Gracefully Handle tx.fhir.org unavailable
		ValidationEngine validator = validationService.getValidationEngine(sessionId);
		logger.info("ValidationEngine's LocalCache Folder:"+validator.getPcm().getFolder());
		logger.info("IGs:");
		for(ImplementationGuide IG:validator.getIgs()){
			logger.info("\t"+IG.toString());
		}
		logger.info("ValidationEngine's Version:"+validator.getVersion());
		ArrayNode issuesNode = null;
		//Actually do the validation
		logger.info("Starting profile loading.");
		OperationOutcome successOO = new OperationOutcome();
		for (String s : cliContext.getProfiles()) {
		//Note our validator is R5 but we're validating R4 resources, so StructureDefinition and ImplementationGuide here are R5 classes.
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
			issuesNode = validationService.validateSources(cliContext, validator);
			logger.info("Number of issues:"+issuesNode.size());
			for(JsonNode issue:issuesNode){
				OperationOutcomeIssueComponent ooic = new OperationOutcomeIssueComponent();
				ooic.setSeverity(IssueSeverity.fromCode(((ObjectNode) issue).get("severity").toString().toLowerCase().replaceAll("\"","")));
				ooic.addExpression(((ObjectNode) issue).get("fhirPath").toString().toString().toLowerCase().replaceAll("\"",""));
				ooic.setDiagnostics(((ObjectNode) issue).get("message").toString().toString().toLowerCase().replaceAll("\"",""));
				String locationString = ((ObjectNode) issue).get("location").toString().toString().toLowerCase().replaceAll("\"","");
				ooic.addLocation(locationString);
				Matcher locationMatcher = lineAndColPattern.matcher(locationString);
				if(locationMatcher.matches()){
					String line = locationMatcher.group("line");
					String col = locationMatcher.group("col");
					Extension lineAndColExtension = new Extension("urn:local:line-and-col");
					Extension lineExtension = new Extension("urn:local:line", new IntegerType(line));
					lineAndColExtension.addExtension(lineExtension);
					Extension colExtension = new Extension("urn:local:col", new IntegerType(col));
					lineAndColExtension.addExtension(colExtension);
					ooic.addExtension(lineAndColExtension);
				}
				successOO.addIssue(ooic);
			}
		}
		ObjectNode returnNode = JsonNodeFactory.instance.objectNode();
		returnNode.put("fhirValidatorVersion","v6.6.1");
		returnNode.set("issues", issuesNode);
		if(includeFormattedResource != null && includeFormattedResource.booleanValue()){
			Extension formattedResourceExtension = new Extension();
			formattedResourceExtension.setUrl("urn:local:formattedResourceBody");
			formattedResourceExtension.setValue(new StringType(resourceBody));
			successOO.addExtension(formattedResourceExtension);
		}
		return successOO;
	}
	
	private OperationOutcome createErrorOperationOutcome(String message,IParser currentParser) {
		OperationOutcome oo = new OperationOutcome();
		oo.addIssue()
		.setSeverity(IssueSeverity.FATAL)
		.setDetails(new CodeableConcept().setText(message));
		return oo;
	}

	private OperationOutcome createErrorOperationOutcome(String message,HttpServletResponse servletResponse, IParser currentParser) {
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

	//Manual creationg of Cors configuration for the defaultcorshandler
	private static CorsConfiguration createDefaultCorsConfig() {
		CorsConfiguration retVal = new CorsConfiguration();
		retVal.setAllowedHeaders(new ArrayList(Constants.CORS_ALLOWED_HEADERS));
		retVal.setAllowedMethods(new ArrayList(Constants.CORS_ALLWED_METHODS));
		retVal.addExposedHeader("Content-Location");
		retVal.addExposedHeader("Location");
		retVal.addAllowedOrigin("*");
		return retVal;
   }

   private static HttpServletResponse postProcess(HttpServletResponse servletResponse){
		if(servletResponse.getHeader("access-control-allow-origin") == null){
			servletResponse.addHeader("access-control-allow-origin", "*");
		}
		return servletResponse;
   }

   //IG support 
   protected ArrayNode convertIGStringsToJson(List<String> igStrings){
		ArrayNode returnArrayNode = JsonNodeFactory.instance.arrayNode();
		for(String igString:igStrings){
			returnArrayNode.add(convertIGStringToJson(igString));
		}
		return returnArrayNode;
   }

   protected ObjectNode convertIGStringToJson(String fullVersionString){
		String[] parts = convertIGStringToParts(fullVersionString);
		ObjectNode returnNode = JsonNodeFactory.instance.objectNode();
		returnNode.put("name", parts[0]);
		returnNode.put("version", parts[1]);
		returnNode.put("canonicalUrl", parts[0] + "#" + parts[1]);
		return returnNode;
   }

   protected String[] convertIGStringToParts(String fullVersionString){
		int hashtagIndex = fullVersionString.indexOf("#");
		String namespace = fullVersionString.substring(0, hashtagIndex);
		String version = hashtagIndex == -1 ? "latest" : fullVersionString.substring(hashtagIndex + 1);
		return new String[]{namespace, version};
   }
}