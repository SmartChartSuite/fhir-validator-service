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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
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
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.param.SpecialParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import edu.gatech.chai.security.NoExitSecurityManager;

public class GenericProvider{
	
	private static final Logger logger = LoggerFactory.getLogger(GenericProvider.class);
	
	DateFormat df;
	IParser jsonParser;
	IParser xmlParser;
	ObjectMapper jsonMapper;
	ObjectMapper xmlMapper;
	public GenericProvider() {
		TimeZone tz = TimeZone.getTimeZone("UTC");
		df = new SimpleDateFormat("yyyy-MM-dd'T'HHmmss");
		df.setTimeZone(tz);
		FhirContext ctx = FhirContext.forR4();
		jsonParser = ctx.newJsonParser().setPrettyPrint(true);
		xmlParser = ctx.newXmlParser().setPrettyPrint(true);
		jsonMapper = new ObjectMapper();
		xmlMapper = new XmlMapper();
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
	public void validateResource(
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		logger.info("Received $validate operation to genericprovider");
		IParser currentParser = jsonParser;
		ObjectMapper currentMapper = jsonMapper;
		String contentType = servletRequest.getContentType();
		if(contentType.equalsIgnoreCase("application/json") || contentType.equalsIgnoreCase("application/fhir+json")) {
			currentParser = jsonParser;
			currentMapper = jsonMapper;
			servletResponse.setContentType("application/json");
		}
		else if(contentType.equalsIgnoreCase("application/xml") || contentType.equalsIgnoreCase("application/fhir+xml")) {
			currentParser = xmlParser;
			currentMapper = jsonMapper;
			servletResponse.setContentType("application/json"); //Leaving output response as json regardless of content
		}
		else {
			createErrorOperationOutcome("Incorrect Content-Type Header. Expecting either application/json, application/fhir+json," +
					" application/xml, application/fhir+xml",servletResponse,currentParser);
			return;
		}
		Resource myParametersResource = null;
		try {
			myParametersResource = (Parameters)currentParser.parseResource(servletRequest.getInputStream());
		} catch (IOException e) {
			createErrorOperationOutcome("Error serializing request body:" + e.getLocalizedMessage(),servletResponse,currentParser);
			return;
		}
		if(myParametersResource instanceof Parameters) {
			Parameters parameters = (Parameters)myParametersResource;
			StringType ig = (StringType)parameters.getParameter("ig");
			for(ParametersParameterComponent ppc: parameters.getParameter()) {
				if(ppc.getName().equalsIgnoreCase("resource")) {
					Resource validatingResource = ppc.getResource();
					baseValidate(ig,validatingResource,currentParser,currentMapper,servletResponse);
					return;
				}
			}
		}
		else {
			createErrorOperationOutcome("Expected Parameters instead found " + myParametersResource.fhirType(),servletResponse,currentParser);
			return;
		}
		createErrorOperationOutcome("Could not parse Parameters options. Expecting stringParam named 'ig' and resourceParam named 'resource'",servletResponse,currentParser);
		return;
	}
	
	
	public HttpServletResponse baseValidate(StringType ig,Resource resource, IParser currentParser, ObjectMapper currentMapper, HttpServletResponse servletResponse) {
		String resourceType = resource.getResourceType().toString();
		String resourceBody = currentParser.encodeResourceToString(resource);
		String nowAsISO = df.format(new Date());
		String fileType = "";
		if(currentParser.getEncoding().equals(EncodingEnum.JSON)) {
			fileType = ".json";
		}
		else if(currentParser.getEncoding().equals(EncodingEnum.XML)) {
			fileType = ".xml";
		}
		String fileName = resource.getResourceType().toString() + nowAsISO + fileType;
		//Write the source to file so validatorCLI can use it
		if(ig == null) {
			ig = new StringType("hl7.fhir.us.mdi#current");
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
			validatorProcess.waitFor(4, TimeUnit.MINUTES); //4 minute timeout for now
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
		JsonNode issuesJson = convertHL7ValidatorOutputToJsonIssues(result);
		//Add original resource body as a json string body here.
		ObjectNode returnNode = JsonNodeFactory.instance.objectNode();
		returnNode.put("issues", issuesJson);
		returnNode.put("formattedResource", resourceBody);
		String responseContent;
		try {
			responseContent = currentMapper.writeValueAsString(returnNode);
		} catch (JsonProcessingException e1) {
			createErrorOperationOutcome(e1.getLocalizedMessage(),servletResponse,currentParser);
			return servletResponse;
		}
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
				+ "([\\w\\[\\]\\(\\)]+(\\.[\\w\\[\\]\\(\\)]+)*|\\?\\?|\\(document\\)|(\\/f:.*))\\s"
				+ "(\\(line \\d+, col\\d+\\))?"
				+ ": (.*)");
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
					location = matcher.group(5) == null ? "??" : matcher.group(5);
					message = matcher.group(6);
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