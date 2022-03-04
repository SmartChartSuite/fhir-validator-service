package edu.gatech.chai.provider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.validation.ValidatorCli;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
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
		// TODO Auto-generated method stub
		return Bundle.class;
	}
	
	@SuppressWarnings("static-access")
	@Operation(name = "$validate", idempotent= true, manualResponse = true)
	public void validateCLIWrapperMethod(@OperationParam(name = "sourceContent")StringParam sourceContent,
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
		String stringContent = URLDecoder.decode(sourceContent.getValue());
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
		
		ValidatorCli vCLI = new ValidatorCli();
		List<String> parameters = new ArrayList<String>();
		parameters.add(tempFile.getAbsolutePath());
		if(!ig.getValue().isEmpty()) {
			parameters.add("-ig");
			parameters.add(ig.getValue());
		}
		if(!profile.getValue().isEmpty()) {
			parameters.add("-profile");
			parameters.add(profile.getValue());
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream systemOutputRedirect = new PrintStream(baos);
		PrintStream originalSystemOut = System.out;
		System.setOut(systemOutputRedirect);
		//Must do this to prevent System.exit() from killing the whole server!
		//Security Manager just throws a SecurityError instead of killing whole process
		if(!(System.getSecurityManager() instanceof NoExitSecurityManager)) {
			System.setSecurityManager(new NoExitSecurityManager(System.getSecurityManager()));
		}
		try {
			vCLI.main(parameters.toArray(new String[0]));
		} catch (SecurityException e) {
			//It's ok to ignore this error if it's thrown from the security manager from System.exit()
		}
		catch (Exception e) {
			//Other show-stopping errors we do have to capture.
			OperationOutcome oo = new OperationOutcome();
			oo.addIssue()
			.setSeverity(IssueSeverity.FATAL)
			.setDetails(new CodeableConcept().setText(e.getLocalizedMessage()));
			setResponseAsOperationOutcome(servletResponse,oo,currentParser);
			e.printStackTrace();
			return;
		}
		finally {
			System.out.flush();
			System.setOut(originalSystemOut);
		}
		JsonNode finalIssuesJson = convertHL7ValidatorOutputToJsonIssues(baos);
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
		Pattern linePattern = Pattern.compile("(Warning|Error|Note|Information|Fatal) @ "
				+ "([\\w\\[\\]\\(\\)]+(\\.[\\w\\[\\]\\(\\)]+)*|\\?\\?|\\(document\\)) "
				+ "(\\(line \\d+, col\\d+\\) )?: (.*)");
		ArrayNode returnNode = JsonNodeFactory.instance.arrayNode();
		for(String line:lines) {
			Matcher matcher = linePattern.matcher(line);
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
				issueNode.put("location", location);
				issueNode.put("message", message);
				returnNode.add(issueNode);
			}
		}
		return returnNode;
	}
}