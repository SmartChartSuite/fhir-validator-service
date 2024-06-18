package edu.gatech.chai.service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.hl7.fhir.r5.context.ContextUtilities;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.context.SystemOutLoggingService;
import org.hl7.fhir.r5.context.TerminologyCache;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.formats.IParser;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.formats.XmlParser;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.CanonicalResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.OperationOutcome;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.r5.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.r5.model.ValueSet;
import org.hl7.fhir.r5.renderers.spreadsheets.CodeSystemSpreadsheetGenerator;
import org.hl7.fhir.r5.renderers.spreadsheets.ConceptMapSpreadsheetGenerator;
import org.hl7.fhir.r5.renderers.spreadsheets.StructureDefinitionSpreadsheetGenerator;
import org.hl7.fhir.r5.renderers.spreadsheets.ValueSetSpreadsheetGenerator;
import org.hl7.fhir.r5.terminologies.CodeSystemUtilities;
import org.hl7.fhir.r5.utils.ToolingExtensions;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.TimeTracker;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.VersionUtilities;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.validation.IgLoader;
import org.hl7.fhir.validation.ValidationEngine;
import org.hl7.fhir.validation.ValidationRecord;
import org.hl7.fhir.validation.cli.model.CliContext;
import org.hl7.fhir.validation.cli.model.FileInfo;
import org.hl7.fhir.validation.cli.model.ValidationOutcome;
import org.hl7.fhir.validation.cli.model.ValidationRequest;
import org.hl7.fhir.validation.cli.model.ValidationResponse;
import org.hl7.fhir.validation.cli.services.HTMLOutputGenerator;
import org.hl7.fhir.validation.cli.services.SessionCache;
import org.hl7.fhir.validation.cli.services.StandAloneValidatorFetcher;
import org.hl7.fhir.validation.cli.utils.EngineMode;
import org.hl7.fhir.validation.cli.utils.VersionSourceInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MyValidationService{

  private static final Logger logger = LoggerFactory.getLogger(MyValidationService.class);


  protected final SessionCache sessionCache;
  public MyValidationService() {
    sessionCache = new SessionCache(30, TimeUnit.DAYS);
  }

  public MyValidationService(SessionCache cache) {
    this.sessionCache = cache;
  }

  public ValidationEngine getValidationEngine(String sessionId){
    return sessionCache.fetchSessionValidatorEngine(sessionId);
  }

  public ValidationResponse validateSources(ValidationRequest request) throws Exception {
    if (request.getCliContext().getSv() == null) {
      String sv = determineVersion(request.getCliContext(), request.sessionId);
      request.getCliContext().setSv(sv);
    }

    String definitions = VersionUtilities.packageForVersion(request.getCliContext().getSv()) + "#" + VersionUtilities.getCurrentVersion(request.getCliContext().getSv());

    String sessionId = initializeValidator(request.getCliContext(), definitions, new TimeTracker(), request.sessionId);
    ValidationEngine validator = sessionCache.fetchSessionValidatorEngine(sessionId);

    if (request.getCliContext().getProfiles().size() > 0) {
      System.out.println("  .. validate " + request.listSourceFiles() + " against " + request.getCliContext().getProfiles().toString());
    } else {
      System.out.println("  .. validate " + request.listSourceFiles());
    }

    ValidationResponse response = new ValidationResponse().setSessionId(sessionId);

    for (FileInfo fp : request.getFilesToValidate()) {
      List<ValidationMessage> messages = new ArrayList<>();
      validator.validate(fp.getFileContent().getBytes(), Manager.FhirFormat.getFhirFormat(fp.getFileType()),
        request.getCliContext().getProfiles(), messages);
      ValidationOutcome outcome = new ValidationOutcome().setFileInfo(fp);
      messages.forEach(outcome::addMessage);
      response.addOutcome(outcome);
    }
    System.out.println("  Max Memory: "+Runtime.getRuntime().maxMemory());
    return response;
  }

  public VersionSourceInformation scanForVersions(CliContext cliContext) throws Exception {
    VersionSourceInformation versions = new VersionSourceInformation();
    IgLoader igLoader = new IgLoader(
      new FilesystemPackageCacheManager(true),
      new SimpleWorkerContext.SimpleWorkerContextBuilder().fromNothing(),
      null);
    for (String src : cliContext.getIgs()) {
      igLoader.scanForIgVersion(src, cliContext.isRecursive(), versions);
    }
    igLoader.scanForVersions(cliContext.getSources(), versions);
    return versions;
  }

  public ArrayNode validateSources(CliContext cliContext, ValidationEngine validator) throws Exception {
    long start = System.currentTimeMillis();
    List<ValidationRecord> records = new ArrayList<>();
    Resource r = validator.validate(cliContext.getSources(), cliContext.getProfiles(), records);
    int ec = 0;
    MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
    System.out.println("Done. " + validator.getContext().clock().report()+". Memory = "+Utilities.describeSize(mbean.getHeapMemoryUsage().getUsed()+mbean.getNonHeapMemoryUsage().getUsed()));
    System.out.println();

    ArrayNode returnNode = JsonNodeFactory.instance.arrayNode();
    if (cliContext.getOutput() == null) {
      if (r instanceof Bundle){
        for (Bundle.BundleEntryComponent e : ((Bundle) r).getEntry()){
          ArrayNode instanceNode = displayOperationOutcome((OperationOutcome) e.getResource(), ((Bundle) r).getEntry().size() > 1, validator.isCrumbTrails());
          returnNode.addAll(instanceNode);
        }
      }
      else if (r == null) {
        ec = ec + 1;
        System.out.println("No output from validation - nothing to validate");
      } else {
        return displayOperationOutcome((OperationOutcome) r, false, validator.isCrumbTrails());
      }
    } else {
      IParser x;
      if (cliContext.getOutput() != null && cliContext.getOutput().endsWith(".json")) {
        System.out.println("x = jsonParser");
        x = new JsonParser();
      } else {
        System.out.println("x = xmlParser");
        x = new XmlParser();
      }
      x.setOutputStyle(IParser.OutputStyle.PRETTY);
      System.out.println("CliContext.getOutput() is now:"+cliContext.getOutput());
      FileOutputStream s = new FileOutputStream(cliContext.getOutput());
      x.compose(s, r);
      s.close();
    }
    if (cliContext.getHtmlOutput() != null) {
      String html = new HTMLOutputGenerator(records).generate(System.currentTimeMillis() - start);
      TextFile.stringToFile(html, cliContext.getHtmlOutput());
      System.out.println("HTML Summary in " + cliContext.getHtmlOutput());
    }
    return returnNode;
  }

  public void convertSources(CliContext cliContext, ValidationEngine validator) throws Exception {
    System.out.println(" ...convert");
    validator.convert(cliContext.getSources().get(0), cliContext.getOutput());
  }

  public void evaluateFhirpath(CliContext cliContext, ValidationEngine validator) throws Exception {
    System.out.println(" ...evaluating " + cliContext.getFhirpath());
    System.out.println(validator.evaluateFhirPath(cliContext.getSources().get(0), cliContext.getFhirpath()));
  }

  public void generateSnapshot(CliContext cliContext, ValidationEngine validator) throws Exception {
    StructureDefinition r = validator.snapshot(cliContext.getSources().get(0), cliContext.getSv());
    System.out.println(" ...generated snapshot successfully");
    if (cliContext.getOutput() != null) {
      validator.handleOutput(r, cliContext.getOutput(), cliContext.getSv());
    }
  }

  public void generateNarrative(CliContext cliContext, ValidationEngine validator) throws Exception {
    Resource r = validator.generate(cliContext.getSources().get(0), cliContext.getSv());
    System.out.println(" ...generated narrative successfully");
    if (cliContext.getOutput() != null) {
      validator.handleOutput(r, cliContext.getOutput(), cliContext.getSv());
    }
  }

  public void transform(CliContext cliContext, ValidationEngine validator) throws Exception {
    if (cliContext.getSources().size() > 1)
      throw new Exception("Can only have one source when doing a transform (found " + cliContext.getSources() + ")");
    if (cliContext.getTxServer() == null)
      throw new Exception("Must provide a terminology server when doing a transform");
    if (cliContext.getMap() == null)
      throw new Exception("Must provide a map when doing a transform");
    try {
      ContextUtilities cu = new ContextUtilities(validator.getContext());
      List<StructureDefinition> structures =  cu.allStructures();
      for (StructureDefinition sd : structures) {
        if (!sd.hasSnapshot()) {
          if (sd.getKind() != null && sd.getKind() == StructureDefinitionKind.LOGICAL) {
            cu.generateSnapshot(sd, true);
          } else {
            cu.generateSnapshot(sd, false);
          }
        }
      }
      validator.setMapLog(cliContext.getMapLog());
      org.hl7.fhir.r5.elementmodel.Element r = validator.transform(cliContext.getSources().get(0), cliContext.getMap());
      System.out.println(" ...success");
      if (cliContext.getOutput() != null) {
        FileOutputStream s = new FileOutputStream(cliContext.getOutput());
        if (cliContext.getOutput() != null && cliContext.getOutput().endsWith(".json"))
          new org.hl7.fhir.r5.elementmodel.JsonParser(validator.getContext()).compose(r, s, IParser.OutputStyle.PRETTY, null);
        else
          new org.hl7.fhir.r5.elementmodel.XmlParser(validator.getContext()).compose(r, s, IParser.OutputStyle.PRETTY, null);
        s.close();
      }
    } catch (Exception e) {
      System.out.println(" ...Failure: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public void transformVersion(CliContext cliContext, ValidationEngine validator) throws Exception {
    if (cliContext.getSources().size() > 1) {
      throw new Exception("Can only have one source when converting versions (found " + cliContext.getSources() + ")");
    }
    if (cliContext.getTargetVer() == null) {
      throw new Exception("Must provide a map when converting versions");
    }
    if (cliContext.getOutput() == null) {
      throw new Exception("Must nominate an output when converting versions");
    }
    try {
      if (cliContext.getMapLog() != null) {
        validator.setMapLog(cliContext.getMapLog());
      }
      byte[] r = validator.transformVersion(cliContext.getSources().get(0), cliContext.getTargetVer(), cliContext.getOutput().endsWith(".json") ? Manager.FhirFormat.JSON : Manager.FhirFormat.XML, cliContext.getCanDoNative());
      System.out.println(" ...success");
      TextFile.bytesToFile(r, cliContext.getOutput());
    } catch (Exception e) {
      System.out.println(" ...Failure: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public ValidationEngine initializeValidator(CliContext cliContext, String definitions, TimeTracker tt) throws Exception {
    return sessionCache.fetchSessionValidatorEngine(initializeValidator(cliContext, definitions, tt, null));
  }

  protected ValidationEngine.ValidationEngineBuilder getValidationEngineBuilder() {
    return new ValidationEngine.ValidationEngineBuilder();
  }

  protected ValidationEngine buildValidationEngine( CliContext cliContext, String definitions, TimeTracker timeTracker, String sessionId) throws IOException, URISyntaxException {
    System.out.print("  Load FHIR v" + cliContext.getSv() + " from " + definitions);
    ValidationEngine validationEngine = getValidationEngineBuilder().withTHO(false).withVersion(cliContext.getSv()).withTimeTracker(timeTracker).withUserAgent("fhir/validator").fromSource(definitions);
    System.out.println(" - " + validationEngine.getContext().countAllCaches() + " resources (" + timeTracker.milestone() + ")");
    loadIgsAndExtensions(validationEngine, cliContext, timeTracker);
    System.out.print("  Get set... ");
    validationEngine.setQuestionnaireMode(cliContext.getQuestionnaireMode());
    validationEngine.setLevel(cliContext.getLevel());
    validationEngine.setDoNative(cliContext.isDoNative());
    validationEngine.setHintAboutNonMustSupport(cliContext.isHintAboutNonMustSupport());
    for (String s : cliContext.getExtensions()) {
      if ("any".equals(s)) {
        validationEngine.setAnyExtensionsAllowed(true);
      } else {
        validationEngine.getExtensionDomains().add(s);
      }
    }
    validationEngine.setLanguage(cliContext.getLang());
    validationEngine.setLocale(cliContext.getLocale());
    validationEngine.setSnomedExtension(cliContext.getSnomedCTCode());
    validationEngine.setAssumeValidRestReferences(cliContext.isAssumeValidRestReferences());
    validationEngine.setShowMessagesFromReferences(cliContext.isShowMessagesFromReferences());
    validationEngine.setDoImplicitFHIRPathStringConversion(cliContext.isDoImplicitFHIRPathStringConversion());
    validationEngine.setHtmlInMarkdownCheck(cliContext.getHtmlInMarkdownCheck());
    validationEngine.setNoExtensibleBindingMessages(cliContext.isNoExtensibleBindingMessages());
    validationEngine.setNoUnicodeBiDiControlChars(cliContext.isNoUnicodeBiDiControlChars());
    validationEngine.setNoInvariantChecks(cliContext.isNoInvariants());
    validationEngine.setWantInvariantInMessage(cliContext.isWantInvariantsInMessages());
    validationEngine.setSecurityChecks(cliContext.isSecurityChecks());
    validationEngine.setCrumbTrails(cliContext.isCrumbTrails());
    validationEngine.setForPublication(cliContext.isForPublication());
    validationEngine.setShowTimes(cliContext.isShowTimes());
    validationEngine.setAllowExampleUrls(cliContext.isAllowExampleUrls());
    StandAloneValidatorFetcher fetcher = new StandAloneValidatorFetcher(validationEngine.getPcm(), validationEngine.getContext(), validationEngine);
    validationEngine.setFetcher(fetcher);
    validationEngine.getContext().setLocator(fetcher);
    validationEngine.getBundleValidationRules().addAll(cliContext.getBundleValidationRules());
    validationEngine.setJurisdiction(CodeSystemUtilities.readCoding(cliContext.getJurisdiction()));
    TerminologyCache.setNoCaching(cliContext.isNoInternalCaching());
    validationEngine.prepare(); // generate any missing snapshots
    System.out.println(" go (" + timeTracker.milestone() + ")");
    return validationEngine;
  }

  protected void loadIgsAndExtensions(ValidationEngine validationEngine, CliContext cliContext, TimeTracker timeTracker) throws IOException, URISyntaxException {
    FhirPublication ver = FhirPublication.fromCode(cliContext.getSv());
    IgLoader igLoader = new IgLoader(validationEngine.getPcm(), validationEngine.getContext(), validationEngine.getVersion(), validationEngine.isDebug());
    igLoader.loadIg(validationEngine.getIgs(), validationEngine.getBinaries(), "hl7.terminology", false);
    if (!VersionUtilities.isR5Ver(validationEngine.getContext().getVersion())) {
      igLoader.loadIg(validationEngine.getIgs(), validationEngine.getBinaries(), "hl7.fhir.uv.extensions", false);
    }
    System.out.print("  Terminology server " + cliContext.getTxServer());
    String txver = validationEngine.setTerminologyServer(cliContext.getTxServer(), cliContext.getTxLog(), ver);
    System.out.println(" - Version " + txver + " (" + timeTracker.milestone() + ")");
    validationEngine.setDebug(cliContext.isDoDebug());
    validationEngine.getContext().setLogger(new SystemOutLoggingService(cliContext.isDoDebug()));
    for (String src : cliContext.getIgs()) {
      igLoader.loadIg(validationEngine.getIgs(), validationEngine.getBinaries(), src, cliContext.isRecursive());
    }
    System.out.println("  Package Summary: "+ validationEngine.getContext().loadedPackageSummary());
  }

  public String initializeValidator(CliContext cliContext, String definitions, TimeTracker tt, String sessionId) throws Exception {
    tt.milestone();
    sessionCache.removeExpiredSessions();
    if (!sessionCache.sessionExists(sessionId)) {
      if (sessionId != null) {
        System.out.println("No such cached session exists for session id " + sessionId + ", re-instantiating validator.");
      }
      ValidationEngine validator = buildValidationEngine(cliContext, definitions, tt, sessionId);
      //Testing IGLoading
      //logger.info("Validation Engine built; trying an IG loading test.");
      //validator.getIgLoader().loadIgSource("C:\\Users\\mriley7\\.fhir\\packages", true, true);
      sessionId = sessionCache.cacheSession(validator);
    } else {
      System.out.println("Cached session exists for session id " + sessionId + ", returning stored validator session id.");
    }
    return sessionId;
  }

  public ArrayNode displayOperationOutcome(OperationOutcome oo, boolean hasMultiples, boolean crumbs) {
    int error = 0;
    int warn = 0;
    int info = 0;
    String file = ToolingExtensions.readStringExtension(oo, ToolingExtensions.EXT_OO_FILE);

    for (OperationOutcome.OperationOutcomeIssueComponent issue : oo.getIssue()) {
      if (issue.getSeverity() == OperationOutcome.IssueSeverity.FATAL || issue.getSeverity() == OperationOutcome.IssueSeverity.ERROR)
        error++;
      else if (issue.getSeverity() == OperationOutcome.IssueSeverity.WARNING)
        warn++;
      else
        info++;
    }

    ArrayNode returnNode = JsonNodeFactory.instance.arrayNode();
    System.out.println((error == 0 ? "Success" : "*FAILURE*") + ": " + Integer.toString(error) + " errors, " + Integer.toString(warn) + " warnings, " + Integer.toString(info) + " notes");
    for (OperationOutcome.OperationOutcomeIssueComponent issue : oo.getIssue()) {
      returnNode.add(getIssueSummaryAsObjectNode(issue));
    //   System.out.println(getIssueSummary(issue));
    //   ValidationMessage vm = (ValidationMessage) issue.getUserData("source.msg");
    //   if (vm != null && vm.sliceText != null && (crumbs || vm.isCriticalSignpost())) {
    //     for (String s : vm.sliceText) {
    //       System.out.println("    slice info: "+s);          
    //     }
    //   }
    }
    return returnNode;
  }

  private String getIssueSummary(OperationOutcome.OperationOutcomeIssueComponent issue) {
    String loc;
    if (issue.hasExpression()) {
      int line = ToolingExtensions.readIntegerExtension(issue, ToolingExtensions.EXT_ISSUE_LINE, -1);
      int col = ToolingExtensions.readIntegerExtension(issue, ToolingExtensions.EXT_ISSUE_COL, -1);
      loc = issue.getExpression().get(0).asStringValue() + (line >= 0 && col >= 0 ? " (line " + Integer.toString(line) + ", col" + Integer.toString(col) + ")" : "");
    } else if (issue.hasLocation()) {
      loc = issue.getLocation().get(0).asStringValue();
    } else {
      int line = ToolingExtensions.readIntegerExtension(issue, ToolingExtensions.EXT_ISSUE_LINE, -1);
      int col = ToolingExtensions.readIntegerExtension(issue, ToolingExtensions.EXT_ISSUE_COL, -1);
      loc = (line >= 0 && col >= 0 ? "line " + Integer.toString(line) + ", col" + Integer.toString(col) : "??");
    }
    return "  " + issue.getSeverity().getDisplay() + " @ " + loc + " : " + issue.getDetails().getText();
  }

  private ObjectNode getIssueSummaryAsObjectNode(OperationOutcome.OperationOutcomeIssueComponent issue) {
    ObjectNode returnNode = JsonNodeFactory.instance.objectNode();
    String loc = "";
    String lineAndCol = "";
    if (issue.hasExpression()) {
      int line = ToolingExtensions.readIntegerExtension(issue, ToolingExtensions.EXT_ISSUE_LINE, -1);
      int col = ToolingExtensions.readIntegerExtension(issue, ToolingExtensions.EXT_ISSUE_COL, -1);
      loc = issue.getExpression().get(0).asStringValue();
      lineAndCol = line >= 0 && col >= 0 ? "(line " + Integer.toString(line) + ", col" + Integer.toString(col) + ")" : "";
    } else if (issue.hasLocation()) {
      loc = issue.getLocation().get(0).asStringValue();
    } else {
      int line = ToolingExtensions.readIntegerExtension(issue, ToolingExtensions.EXT_ISSUE_LINE, -1);
      int col = ToolingExtensions.readIntegerExtension(issue, ToolingExtensions.EXT_ISSUE_COL, -1);
      lineAndCol = line >= 0 && col >= 0 ? "line " + Integer.toString(line) + ", col" + Integer.toString(col) : "??";
    }
    //return "  " + issue.getSeverity().getDisplay() + " @ " + loc + " : " + issue.getDetails().getText();
    returnNode.put("severity", issue.getSeverity().getDisplay());
    returnNode.put("fhirPath", loc);//TODO Seperate the fhirPath from the line and col
    returnNode.put("location", lineAndCol);
    returnNode.put("message", issue.getDetails().getText());
    return returnNode;
  }

  public String determineVersion(CliContext cliContext) throws Exception {
    return determineVersion(cliContext, null);
  }

  public String determineVersion(CliContext cliContext, String sessionId) throws Exception {
    if (cliContext.getMode() != EngineMode.VALIDATION && cliContext.getMode() != EngineMode.INSTALL) {
      return "5.0";
    }
    System.out.println("Scanning for versions (no -version parameter):");
    VersionSourceInformation versions = scanForVersions(cliContext);
    for (String s : versions.getReport()) {
      if (!s.equals("(nothing found)")) {
        System.out.println("  " + s);
      }
    }
    if (versions.isEmpty()) {
      System.out.println("  No Version Info found: Using Default version R5");
      return "5.0.0";
    }
    if (versions.size() == 1) {
      System.out.println("-> use version " + versions.version());
      return versions.version();
    }
    throw new Exception("-> Multiple versions found. Specify a particular version using the -version parameter");
  }

  public void generateSpreadsheet(CliContext cliContext, ValidationEngine validator) throws Exception {
    CanonicalResource cr = validator.loadCanonicalResource(cliContext.getSources().get(0), cliContext.getSv());
    boolean ok = true;
    if (cr instanceof StructureDefinition) {
      new StructureDefinitionSpreadsheetGenerator(validator.getContext(), false, false).renderStructureDefinition((StructureDefinition) cr, false).finish(new FileOutputStream(cliContext.getOutput()));
    } else if (cr instanceof CodeSystem) {
      new CodeSystemSpreadsheetGenerator(validator.getContext()).renderCodeSystem((CodeSystem) cr).finish(new FileOutputStream(cliContext.getOutput()));
    } else if (cr instanceof ValueSet) {
      new ValueSetSpreadsheetGenerator(validator.getContext()).renderValueSet((ValueSet) cr).finish(new FileOutputStream(cliContext.getOutput()));
    } else if (cr instanceof ConceptMap) {
      new ConceptMapSpreadsheetGenerator(validator.getContext()).renderConceptMap((ConceptMap) cr).finish(new FileOutputStream(cliContext.getOutput()));
    } else {
      ok = false;
      System.out.println(" ...Unable to generate spreadsheet for "+cliContext.getSources().get(0)+": no way to generate a spreadsheet for a "+cr.fhirType());
    }
    
    if (ok) {
      System.out.println(" ...generated spreadsheet successfully");
    } 
  }
}
