package edu.gatech.chai.provider;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.ProcessBuilder.Redirect;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.validation.ValidatorCli;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.param.SpecialParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import edu.gatech.chai.security.NoExitSecurityManager;

public class GenericProvider{
	
	private static final Logger logger = LoggerFactory.getLogger(GenericProvider.class);
	
	DateFormat df;
	IParser jsonParser;
	IParser xmlParser;
	ObjectMapper objectMapper;
	public GenericProvider() {
		TimeZone tz = TimeZone.getTimeZone("UTC");
		df = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss");
		df.setTimeZone(tz);
		FhirContext ctx = FhirContext.forR4();
		jsonParser = ctx.newJsonParser();
		xmlParser = ctx.newXmlParser();
		objectMapper = new ObjectMapper();
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Account.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Account resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = AdverseEvent.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")AdverseEvent resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = AllergyIntolerance.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")AllergyIntolerance resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Appointment.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Appointment resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = AppointmentResponse.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")AppointmentResponse resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true,type = AuditEvent.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")AuditEvent resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Basic.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Basic resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = BiologicallyDerivedProduct.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")BiologicallyDerivedProduct resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = BodyStructure.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")BodyStructure resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Bundle.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Bundle resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = CarePlan.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")CarePlan resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = CareTeam.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")CareTeam resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = CatalogEntry.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")CatalogEntry resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = ChargeItem.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")ChargeItem resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Claim.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Claim resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = ClaimResponse.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")ClaimResponse resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = ClinicalImpression.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")ClinicalImpression resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Communication.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Communication resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = CommunicationRequest.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")CommunicationRequest resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Composition.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Composition resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Condition.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Condition resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Consent.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Consent resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Contract.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Contract resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Coverage.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Coverage resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = CoverageEligibilityRequest.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")CoverageEligibilityRequest resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Medication.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Medication resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = MedicationAdministration.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")MedicationAdministration resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = MedicationDispense.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")MedicationDispense resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = MedicationRequest.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")MedicationRequest resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = MedicationStatement.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")MedicationStatement resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = MessageHeader.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")MessageHeader resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Observation.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Observation resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Parameters.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Parameters resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Patient.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Patient resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Practitioner.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Practitioner resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = PractitionerRole.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")PractitionerRole resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	@Operation(name = "$validate", manualResponse = true, type = Specimen.class)
	public void validateResource(
			@OperationParam(name = "ig")StringParam ig,
			@OperationParam(name = "resource")Specimen resource,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call for "+resource.getResourceType().toString()+" type resource");
		servletResponse = baseValidate(ig,resource,servletResponse);
	}
	
	
	public HttpServletResponse baseValidate(StringParam ig,Resource resource,HttpServletResponse servletResponse) {
		String resourceType = resource.getResourceType().toString();
		IParser currentParser = jsonParser;
		String resourceBody = currentParser.encodeResourceToString(resource);
		String nowAsISO = df.format(new Date());
		String fileName = resource.getResourceType().toString() + nowAsISO + ".json";
		servletResponse.setContentType("application/json");
		//Write the source to file so validatorCLI can use it
		if(ig == null) {
			ig = new StringParam("hl7.fhir.us.mdi#current");
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
			createErrorOperationOutcome("Could not access and create file:"+tempFile.getAbsolutePath(),servletResponse,currentParser);
			e1.printStackTrace();
			return servletResponse;
		}
		List<String> parameters = new ArrayList<String>();
		parameters.add("java"); //Manually setting up another java process
		parameters.add("-jar");
		parameters.add("validator_cli.jar");
		parameters.add(tempFile.getAbsolutePath());
		if(!ig.getValue().isEmpty()) {
			parameters.add("-ig");
			parameters.add(ig.getValue());
		}
		/*if(!profile.getValue().isEmpty()) {
			parameters.add("-profile");
			parameters.add(profile.getValue());
		}*/
		org.springframework.core.io.Resource jarResource = new ClassPathResource("validator_cli.jar");
		String directoryLocation = "";
		try {
			directoryLocation = jarResource.getFile().getParent();
		} catch (IOException e2) {
			createErrorOperationOutcome("Could not find validator_cli.jar:"+e2.getLocalizedMessage(),servletResponse,currentParser);
			e2.printStackTrace();
			return servletResponse;
		}
		ProcessBuilder pb = new ProcessBuilder(parameters.toArray(new String[0]));
		File errorFile = pb.redirectError(new File("validatorErrorStream.log")).redirectError().file();
		File outputFile = pb.redirectOutput(new File("validatorOutputStream.log")).redirectOutput().file();
		pb.directory(new File(directoryLocation));
		Process validatorProcess;
		try {
			validatorProcess = pb.start();
			validatorProcess.waitFor(2, TimeUnit.MINUTES); //2 minute timeout for now
		} catch (IOException e) {
			createErrorOperationOutcome("Could not start validator process:"+e.getLocalizedMessage(),servletResponse,currentParser);
			e.printStackTrace();
			return servletResponse;
		} catch (InterruptedException e) {
			createErrorOperationOutcome("Interruption error with validator_cli.jar:"+e.getLocalizedMessage(),servletResponse,currentParser);
			e.printStackTrace();
			return servletResponse;
		}
		finally {
			//Seems like process builder redirect closes it for itself
		}
		StringBuilder errorBuilder = new StringBuilder();
		try {
			BufferedReader errorReader = 
	                new BufferedReader(new FileReader(errorFile));
			String errorLine = null;
			while ( (errorLine = errorReader.readLine()) != null) {
			   errorBuilder.append(errorLine);
			   errorBuilder.append(System.getProperty("line.separator"));
			}
		} catch (IOException e) {
			createErrorOperationOutcome("Could not read line from process error steram:"+e.getLocalizedMessage(),servletResponse,currentParser);
			e.printStackTrace();
			return servletResponse;
		}
		String errorResult = errorBuilder.toString();
		if(errorResult != null && !errorResult.isBlank()) {
			logger.error(errorResult);
			createErrorOperationOutcome("Error running external validator_cli.jar:"+errorResult,servletResponse,currentParser);
			return servletResponse;
		}
		StringBuilder builder = new StringBuilder();
		try {
			BufferedReader reader = 
	                new BufferedReader(new FileReader(outputFile));
			String line = null;
			while ( (line = reader.readLine()) != null) {
			   builder.append(line);
			   builder.append(System.getProperty("line.separator"));
			}
		} catch (IOException e) {
			createErrorOperationOutcome("Could not read line from process input steram:"+e.getLocalizedMessage(),servletResponse,currentParser);
			e.printStackTrace();
			return servletResponse;
		}
		String result = builder.toString();
		JsonNode finalIssuesJson = convertHL7ValidatorOutputToJsonIssues(result);
		String responseContent;
		try {
			responseContent = objectMapper.writeValueAsString(finalIssuesJson);
		} catch (JsonProcessingException e1) {
			createErrorOperationOutcome(e1.getLocalizedMessage(),servletResponse,currentParser);
			return servletResponse;
		}
		servletResponse.setContentType("application/json");
		try {
			servletResponse.getWriter().write(responseContent);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return servletResponse;
	}
	
	private JsonNode convertHL7ValidatorOutputToJsonIssues(String string){
		logger.info(string);
		String[] lines = string.split("\n");
		logger.info(string);
		int lineNumber = findLineThatStartsTheReport(lines);
		String[] issueLines = Arrays.copyOfRange(lines, lineNumber+1, lines.length);
		return linesToIssue(issueLines);
	}
	
	private JsonNode convertHL7ValidatorOutputToJsonIssues(ByteArrayOutputStream baos){
		String[] lines = baos.toString().split("\n");
		logger.info(baos.toString());
		int lineNumber = findLineThatStartsTheReport(lines);
		String[] issueLines = Arrays.copyOfRange(lines, lineNumber+1, lines.length);
		return linesToIssue(issueLines);
	}
	
	private int findLineThatStartsTheReport(String[]lines) {
		Pattern headerPattern = Pattern.compile("[\\d,]+ errors, [\\d,]+ warnings, [\\d,]+ notes");
		for(int i=0;i<lines.length;i++) {
			Matcher matcher = headerPattern.matcher(lines[i]);
			if(matcher.find()) {
				return i;
			}
		}
		return -1;
	}
	
	private ArrayNode linesToIssue(String[]lines) {
		ArrayNode returnNode = JsonNodeFactory.instance.arrayNode();
		Pattern linePattern = Pattern.compile("\\w*(Warning|Error|Note|Information|Fatal) @ "
				+ "([\\w\\[\\]\\(\\)]+(\\.[\\w\\[\\]\\(\\)]+)*|\\?\\?|\\(document\\)) "
				+ "(\\(line \\d+, col\\d+\\))?: (.*)");
		Pattern allOKPattern = Pattern.compile("\\w*Information: All OK");
		for(String line:lines) {
			Matcher matcher = linePattern.matcher(line);
			Matcher allOKMatcher = allOKPattern.matcher(line);
			if(matcher.find()) {
				String severity = null;
				String fhirPath = null;
				String location = null;
				String message = null;
				if(matcher.group(1) != null) {
					severity = matcher.group(1);
					fhirPath = matcher.group(2);
					location = matcher.group(4) == null ? "??" : matcher.group(4);
					message = matcher.group(5);
				}
				else {
					continue;
				}
				ObjectNode issueNode = JsonNodeFactory.instance.objectNode();
				issueNode.put("severity", severity);
				issueNode.put("fhirPath", fhirPath);
				if(!location.equalsIgnoreCase("??")) {
					Pattern locationRowAndColPattern = Pattern.compile("\\(line (\\d+), col(\\d+)\\)");
					Matcher locationMatcher = locationRowAndColPattern.matcher(location);
					if(matcher.find()) {
						String row = matcher.group(1);
						String col = matcher.group(2);
						ObjectNode locationJson = JsonNodeFactory.instance.objectNode();
						locationJson.put("row", row);
						locationJson.put("col", col);
						issueNode.set("locationObj", locationJson);
					}
				}
				issueNode.put("location", location);
				issueNode.put("message", message);
				returnNode.add(issueNode);
			}
			else if(allOKMatcher.find()) {
				ObjectNode issueNode = JsonNodeFactory.instance.objectNode();
				issueNode.put("severity", "Information");
				issueNode.put("fhirPath", "");
				issueNode.put("location", "");
				issueNode.put("message", "ALL OK");
				returnNode.add(issueNode);
			}
		}
		return returnNode;
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
		return servletResponse;
	}
}