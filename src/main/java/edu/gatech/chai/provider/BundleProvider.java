package edu.gatech.chai.provider;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.validation.ValidatorCli;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.tools.sjavac.Log;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.param.SpecialParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import edu.gatech.chai.security.NoExitSecurityManager;

public class BundleProvider implements IResourceProvider{
	
	private static final Logger logger = LoggerFactory.getLogger(BundleProvider.class);
	
	DateFormat df;
	IParser jsonParser;
	IParser xmlParser;
	ObjectMapper objectMapper;
	public BundleProvider() {
		TimeZone tz = TimeZone.getTimeZone("UTC");
		df = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss");
		df.setTimeZone(tz);
		FhirContext ctx = FhirContext.forR4();
		jsonParser = ctx.newJsonParser();
		xmlParser = ctx.newXmlParser();
		objectMapper = new ObjectMapper();
	}
	
	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return Bundle.class;
	}
	
	@Operation(name = "$validate", manualResponse = true)
	public void validateCLIWrapperMethodExternal(@OperationParam(name = "sourceContent")SpecialParam sourceContent,
			@OperationParam(name = "format")StringParam format,
			@OperationParam(name = "profile")StringParam profile,
			@OperationParam(name = "ig")StringParam ig,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation call");
		//Write the source to file so validatorCLI can use it
		IParser currentParser;
		String nowAsISO = df.format(new Date());
		String fileName = nowAsISO;
		if(ig == null) {
			ig = new StringParam("hl7.fhir.us.mdi#current");
		}
		if(format == null) {
			format = new StringParam("json");
		}
		if(format.getValue().equalsIgnoreCase("xml")) {
			fileName = fileName + ".xml";
			currentParser = xmlParser;
			servletResponse.setContentType("application/xml");
		}
		else {
			fileName = fileName + ".json";
			currentParser = jsonParser;
			servletResponse.setContentType("application/json");
		}
		
		String stringContent = "";
		try {
			stringContent = URLDecoder.decode(sourceContent.getValue());
		}
		catch(NullPointerException e) {
			//Other show-stopping errors we do have to capture.
			OperationOutcome oo = new OperationOutcome();
			oo.addIssue()
			.setSeverity(IssueSeverity.FATAL)
			.setDetails(new CodeableConcept().setText("The parameter 'sourceContent' is missing from the payload."));
			setResponseAsOperationOutcome(servletResponse,oo,currentParser);
			e.printStackTrace();
			return;
		}
		File tempFile = new File(fileName);
		FileOutputStream fos;
		try {
			tempFile.createNewFile();
			fos = new FileOutputStream(tempFile);
			byte[] sourceContentBytes = stringContent.getBytes();
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
		List<String> parameters = new ArrayList<String>();
		parameters.add("java"); //Manually setting up another java process
		parameters.add("-jar");
		parameters.add("validator_cli.jar");
		parameters.add(tempFile.getAbsolutePath());
		if(!ig.getValue().isEmpty()) {
			parameters.add("-ig");
			parameters.add(ig.getValue());
		}
		if(!profile.getValue().isEmpty()) {
			parameters.add("-profile");
			parameters.add(profile.getValue());
		}
		Resource resource = new ClassPathResource("validator_cli.jar");
		String directoryLocation = "";
		try {
			directoryLocation = resource.getFile().getParent();
		} catch (IOException e2) {
			OperationOutcome oo = new OperationOutcome();
			oo.addIssue()
			.setSeverity(IssueSeverity.FATAL)
			.setDetails(new CodeableConcept().setText("Could not find validator_cli.jar:"+e2.getLocalizedMessage()));
			setResponseAsOperationOutcome(servletResponse,oo,currentParser);
			e2.printStackTrace();
			return;
		}
		ProcessBuilder pb = new ProcessBuilder(parameters.toArray(new String[0]));
		pb.directory(new File(directoryLocation));
		Process validatorProcess;
		try {
			validatorProcess = pb.start();
		} catch (IOException e) {
			OperationOutcome oo = new OperationOutcome();
			oo.addIssue()
			.setSeverity(IssueSeverity.FATAL)
			.setDetails(new CodeableConcept().setText("Could not start validator process:"+e.getLocalizedMessage()));
			setResponseAsOperationOutcome(servletResponse,oo,currentParser);
			e.printStackTrace();
			return;
		}
		
		BufferedReader errorReader = 
                new BufferedReader(new InputStreamReader(validatorProcess.getErrorStream()));
		StringBuilder errorBuilder = new StringBuilder();
		String errorLine = null;
		try {
			while ( (errorLine = errorReader.readLine()) != null) {
			   errorBuilder.append(errorLine);
			   errorBuilder.append(System.getProperty("line.separator"));
			}
		} catch (IOException e) {
			OperationOutcome oo = new OperationOutcome();
			oo.addIssue()
			.setSeverity(IssueSeverity.FATAL)
			.setDetails(new CodeableConcept().setText("Could not read line from process error steram:"+e.getLocalizedMessage()));
			setResponseAsOperationOutcome(servletResponse,oo,currentParser);
			e.printStackTrace();
			return;
		}
		String errorResult = errorBuilder.toString();
		if(errorResult != null && !errorResult.isBlank()) {
			logger.error(errorResult);
			OperationOutcome oo = new OperationOutcome();
			oo.addIssue()
			.setSeverity(IssueSeverity.FATAL)
			.setDetails(new CodeableConcept().setText("Error running external validator_cli.jar:"+errorResult));
			setResponseAsOperationOutcome(servletResponse,oo,currentParser);
			return;
		}
		
		BufferedReader reader = 
                new BufferedReader(new InputStreamReader(validatorProcess.getInputStream()));
		StringBuilder builder = new StringBuilder();
		String line = null;
		try {
			while ( (line = reader.readLine()) != null) {
			   builder.append(line);
			   builder.append(System.getProperty("line.separator"));
			}
		} catch (IOException e) {
			OperationOutcome oo = new OperationOutcome();
			oo.addIssue()
			.setSeverity(IssueSeverity.FATAL)
			.setDetails(new CodeableConcept().setText("Could not read line from process input steram:"+e.getLocalizedMessage()));
			setResponseAsOperationOutcome(servletResponse,oo,currentParser);
			e.printStackTrace();
			return;
		}
		String result = builder.toString();
		JsonNode finalIssuesJson = convertHL7ValidatorOutputToJsonIssues(result);
		String responseContent;
		try {
			responseContent = objectMapper.writeValueAsString(finalIssuesJson);
		} catch (JsonProcessingException e1) {
			OperationOutcome oo = new OperationOutcome();
			oo.addIssue()
			.setSeverity(IssueSeverity.FATAL)
			.setDetails(new CodeableConcept().setText(e1.getLocalizedMessage()));
			setResponseAsOperationOutcome(servletResponse,oo,currentParser);
			e1.printStackTrace();
			return;
		}
		servletResponse.setContentType("application/json");
		try {
			servletResponse.getWriter().write(responseContent);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
}