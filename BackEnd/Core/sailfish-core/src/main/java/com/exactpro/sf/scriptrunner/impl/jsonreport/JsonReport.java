/******************************************************************************
 * Copyright 2009-2019 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
******************************************************************************/
package com.exactpro.sf.scriptrunner.impl.jsonreport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.mvel2.math.MathProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.sf.SerializeUtil;
import com.exactpro.sf.aml.AMLBlockType;
import com.exactpro.sf.aml.generator.AggregateAlert;
import com.exactpro.sf.aml.script.CheckPoint;
import com.exactpro.sf.center.IVersion;
import com.exactpro.sf.center.impl.SFLocalContext;
import com.exactpro.sf.common.messages.IMessage;
import com.exactpro.sf.comparison.ComparisonResult;
import com.exactpro.sf.configuration.IDictionaryManager;
import com.exactpro.sf.configuration.workspace.FolderType;
import com.exactpro.sf.configuration.workspace.IWorkspaceDispatcher;
import com.exactpro.sf.configuration.workspace.WorkspaceStructureException;
import com.exactpro.sf.scriptrunner.IReportStats;
import com.exactpro.sf.scriptrunner.IScriptProgress;
import com.exactpro.sf.scriptrunner.IScriptReport;
import com.exactpro.sf.scriptrunner.LoggerRow;
import com.exactpro.sf.scriptrunner.MessageLevel;
import com.exactpro.sf.scriptrunner.OutcomeCollector;
import com.exactpro.sf.scriptrunner.ReportUtils;
import com.exactpro.sf.scriptrunner.ScriptContext;
import com.exactpro.sf.scriptrunner.ScriptRunException;
import com.exactpro.sf.scriptrunner.StatusDescription;
import com.exactpro.sf.scriptrunner.TestScriptDescription;
import com.exactpro.sf.scriptrunner.TestScriptDescription.ScriptState;
import com.exactpro.sf.scriptrunner.TestScriptDescription.ScriptStatus;
import com.exactpro.sf.scriptrunner.impl.ReportStats;
import com.exactpro.sf.scriptrunner.impl.ReportTable;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.Action;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.Alert;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.Bug;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.CustomLink;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.CustomMessage;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.CustomTable;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.KnownBugStatus;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.Message;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.OutcomeSummary;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.Parameter;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.ReportException;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.ReportProperties;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.ReportRoot;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.Status;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.TestCase;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.TestCaseMetadata;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.Verification;
import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.VerificationEntry;
import com.exactpro.sf.scriptrunner.reportbuilder.textformatter.TextColor;
import com.exactpro.sf.scriptrunner.reportbuilder.textformatter.TextStyle;
import com.exactpro.sf.util.BugDescription;
import com.exactpro.sf.util.EnumReplacer;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.Sets;

@SuppressWarnings("MismatchedQueryAndUpdateOfCollection, unused, FieldCanBeLocal")
public class JsonReport implements IScriptReport {
    private static final Logger logger = LoggerFactory.getLogger(JsonReport.class);
    private static final ObjectMapper mapper;
    private static final String REPORT_ROOT_FILE_NAME = "report";
    private static final String REPORT_DATA_DIRECTORY_NAME = "reportData";
    private static final String LIVE_REPORT_DIRECTORY_NAME = "live";

    private long actionIdCounter;
    private long knownBugIdCounter;

    private ScriptContext scriptContext;
    private Context context;
    private IReportStats reportStats;
    private AtomicLong actionNodeDepth = new AtomicLong(0);

    private LiveTestCaseWriter liveTestCaseWriter;
    private Path liveReportDir;
    private final Map<Long, Set<Long>> messageToActionIdMap;

    private final IWorkspaceDispatcher dispatcher;
    private final String reportRootDirectoryPath;
    private final TestScriptDescription testScriptDescription;
    private final IDictionaryManager dictionaryManager;


    //Main bean of report
    private final ReportRoot reportRoot = new ReportRoot();

    static {
        mapper = new ObjectMapper();
        mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker().withFieldVisibility(Visibility.ANY)
                .withCreatorVisibility(Visibility.NONE).withSetterVisibility(Visibility.NONE)
                .withGetterVisibility(Visibility.NONE).withIsGetterVisibility(Visibility.NONE));
        mapper.registerModule(new JavaTimeModule());
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS"));
    }

    public JsonReport(String reportRootDirectoryPath, IWorkspaceDispatcher dispatcher, TestScriptDescription testScriptDescription, IDictionaryManager dictionaryManager) {
        this.messageToActionIdMap = new HashMap<>();
        this.reportStats = new ReportStats();
        this.dispatcher = dispatcher;
        this.reportRootDirectoryPath = reportRootDirectoryPath;
        this.testScriptDescription = testScriptDescription;
        this.liveReportDir = Paths.get(reportRootDirectoryPath, REPORT_DATA_DIRECTORY_NAME, LIVE_REPORT_DIRECTORY_NAME);
        this.dictionaryManager = dictionaryManager;
    }

    public boolean isActionCreated() throws UnsupportedOperationException {
        return actionNodeDepth.get() > 0;
    }

    private void assertState(boolean throwException, ContextType... states) {
        if (Arrays.stream(states).noneMatch(i -> context.cur == i)) {
            String msg = String.format("Incorrect report state '%s' ('%s' expected)", context.cur, Arrays.toString(states));
            if (throwException) {
                throw new RuntimeException(msg);
            } else {
                logger.error("{} - ignoring", msg);
            }
        }
    }

    private void assertState(ContextType... states) {
        assertState(true, states);
    }

    private File getFile(String fileName, String extension) {
        fileName = fileName.replaceAll("\\W", "_");
        if (!fileName.endsWith(extension)) {
            fileName = fileName + extension;
        }

        try {
            return dispatcher.createFile(FolderType.REPORT, true, reportRootDirectoryPath, REPORT_DATA_DIRECTORY_NAME, fileName);
        } catch (WorkspaceStructureException e) {
            throw new ScriptRunException("unable to create report file", e);
        }
    }

    private void exportToFile(Object data, String fileName) {
        File jsonFile = getFile(fileName, ".json");
        File jsonpFile = getFile(fileName, ".js");

        try (FileOutputStream jsonpStream = new FileOutputStream(jsonpFile)) {
            logger.info("saving json report - writing to file: '{}'", jsonpFile);
            mapper.writeValue(jsonFile, data);

            jsonpStream.write("window.loadJsonp(".getBytes());
            Files.copy(jsonFile.toPath(), jsonpStream);
            jsonpStream.write(")".getBytes());

        } catch (IOException e) {
            throw new ScriptRunException("unable to export json report", e);
        }

        if (data instanceof TestCase) {
            reportRoot.getMetadata().add(new TestCaseMetadata((TestCase)data, jsonpFile, jsonFile));
        }
    }

    private void initProperties() {
        ScriptState state = testScriptDescription.getState();
        ScriptStatus status = testScriptDescription.getStatus();
        String matrixFile = testScriptDescription.getMatrixFileName();
        long timestamp = testScriptDescription.getTimestamp().getTime();
        String environmentNameAttr = testScriptDescription.getContext().getEnvironmentName();
        String languageURI = testScriptDescription.getLanguageURI().toString();
        String workFolder = testScriptDescription.getWorkFolder();

        IScriptProgress progress = testScriptDescription.getContext().getScriptProgress();
        long passed  = progress.getPassed();
        long conditionallyPassed = progress.getConditionallyPassed();
        long failed  = progress.getFailed();
        long total = progress.getLoaded();

        List<String> services = testScriptDescription.getContext().getServiceList();
        String range = testScriptDescription.getRange();
        boolean autostart = testScriptDescription.getAutoStart();
        String cause = null;
        // Cause serializing
        if (testScriptDescription.getCause() != null) {
            cause = SerializeUtil.serializeToBase64(testScriptDescription.getCause());
        }

        reportRoot.setReportProperties(
                new ReportProperties(state, status, matrixFile, timestamp, environmentNameAttr, languageURI, workFolder, passed, conditionallyPassed,
                        failed, total, services, range, autostart, cause));
    }

    private void setContext(ContextType state, IJsonReportNode currentNode) {
        this.context = new Context(context, state, currentNode);
    }

    private void revertContext() {
        this.context = context.prev;
    }

    @SuppressWarnings("unchecked")
    private <T extends IJsonReportNode> T getCurrentContextNode() {
        return (T)context.node;
    }

    private Bug getOrSetKnownBug(TestCase testCase, BugDescription description) {
        Map.Entry<Bug, List<String>> existingBug = testCase.getBugToCategoryMap().entrySet().stream()
                .filter(entry ->
                        entry.getKey().getSubject().equals(description.getSubject())
                                && entry.getValue().equals(description.getCategories().list()))
                .findFirst().orElse(null);

        if (existingBug == null) {
            Bug bug = new Bug(description, KnownBugStatus.NOT_REPRODUCED, knownBugIdCounter++, new HashSet<>());
            testCase.addSubNodes(bug);
            testCase.getBugToCategoryMap().put(bug, description.getCategories().list());

            return bug;
        }

        return existingBug.getKey();
    }

    public void createReport(ScriptContext scriptContext, String name, String description, long scriptRunId, String environmentName,
            String userName) {

        this.scriptContext = scriptContext;
        reportRoot.setStartTime(Instant.now());

        try {
            reportRoot.setHostName(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            reportRoot.setHostName("n/a");
        }

        reportRoot.setPrecision(MathProcessor.COMPARISON_PRECISION.toPlainString());
        reportRoot.setName(name);
        reportRoot.setUserName(userName);
        reportRoot.setScriptRunId(scriptRunId);
        reportRoot.setVersion(SFLocalContext.getDefault().getVersion());
        reportRoot.setBranchName(SFLocalContext.getDefault().getBranchName());

        reportRoot.setPlugins(SFLocalContext.getDefault().getPluginVersions().stream().filter(i -> !i.isGeneral())
                .collect(Collectors.toMap(IVersion::getAlias, IVersion::buildVersion)));

        reportRoot.setDescription(description);

        setContext(ContextType.SCRIPT, null);
    }

    public void addAlerts(Collection<AggregateAlert> aggregatedAlerts) {
        synchronized (reportRoot.getAlerts()) {
            reportRoot.getAlerts().addAll(aggregatedAlerts.stream().map(a -> new Alert(a.joinLines(), a.getType().toString(), a.getColumn(), a.getMessage()))
                    .collect(Collectors.toList()));
        }
    }

    public void createTestCase(String reference, String description, int order, int matrixOrder, String tcId, int tcHash,
                               AMLBlockType type, Set<String> tags) {
        this.reportStats = new ReportStats();
        assertState(ContextType.SCRIPT);

        TestCase testcase = new TestCase();

        testcase.setName(ReportUtils.generateTestCaseName(reference, matrixOrder, type));
        testcase.setOrder(order);
        testcase.setReference(reference);
        testcase.setType(type.getName());
        testcase.setStartTime(Instant.now());
        testcase.setMatrixOrder(matrixOrder);
        testcase.setId(tcId);
        testcase.setHash(tcHash);
        testcase.setDescription(description);
        testcase.setTags(tags);

        setContext(ContextType.TESTCASE, testcase);

        this.liveTestCaseWriter = new LiveTestCaseWriter(testcase, mapper, liveReportDir, dispatcher);
    }

    public void closeTestCase(StatusDescription status) {
        assertState(ContextType.TESTCASE);

        TestCase curTestCase = getCurrentContextNode();
        curTestCase.setStatus(new Status(status));
        curTestCase.setFinishTime(Instant.now());

        exportToFile(curTestCase, curTestCase.getName());
        exportToFile(reportRoot, REPORT_ROOT_FILE_NAME);

        revertContext();
        reportStats.updateTestCaseStatus(status.getStatus());

        liveTestCaseWriter.clearDirectory();
        liveTestCaseWriter = null;
    }

    public void createAction(String id, String serviceName, String name, String messageType, String description, IMessage parameters,
            CheckPoint checkPoint, String tag, int hash, List<String> verificationsOrder, String outcome) {

        assertState(ContextType.TESTCASE, ContextType.ACTION, ContextType.ACTIONGROUP);

        Action curAction = new Action();
        getCurrentContextNode().addSubNodes(curAction);

        curAction.setId(actionIdCounter++);
        curAction.setMatrixId(id);
        curAction.setServiceName(serviceName);
        curAction.setStartTime(Instant.now());
        curAction.setName(name);
        curAction.setMessageType(messageType);
        curAction.setDescription(description);
        curAction.setCheckPointId(checkPoint != null ? checkPoint.getId() : null);
        curAction.setOutcome(outcome);
        curAction.setIsRunning(true);

        if (parameters != null) {
            curAction.setParameters(Parameter.fromMessage(parameters));
            curAction.getRelatedMessages().add(parameters.getMetaData().getId());
        }
        setContext(ContextType.ACTION, curAction);
        actionNodeDepth.incrementAndGet();
    }

    public void closeAction(StatusDescription status, Object actionResult) {
        assertState(ContextType.ACTION);

        Action curAction = getCurrentContextNode();
        curAction.setStatus(new Status(status));
        curAction.setFinishTime(Instant.now());
        curAction.setIsRunning(false);

        if (status.isUpdateTestCaseStatus()) {
            reportStats.updateActions(status.getStatus());
        }

        actionNodeDepth.decrementAndGet();

        revertContext();

        //content propagation
        IJsonReportNode parentNode = getCurrentContextNode();

        if (parentNode instanceof Action) {
            ((Action) parentNode).getRelatedMessages().addAll(curAction.getRelatedMessages());
            ((Action) parentNode).getBugs().addAll(curAction.getBugs());
        }

        for (Long id : curAction.getRelatedMessages()) {
            messageToActionIdMap.computeIfAbsent(id, key -> new HashSet<>()).add(curAction.getId());
        }

        liveTestCaseWriter.writeNode(ObjectUtils.defaultIfNull(getCurrentRootAction(), curAction));
    }

    public void openGroup(String name, String description) {
        assertState(ContextType.ACTION, ContextType.ACTIONGROUP);
        Action curGroup = new Action();
        getCurrentContextNode().addSubNodes(curGroup);

        curGroup.setId(actionIdCounter++);
        curGroup.setName(name);
        curGroup.setDescription(description);
        curGroup.setIsRunning(true);

        setContext(ContextType.ACTIONGROUP, curGroup);
    }

    public void closeGroup(StatusDescription status) {
        assertState(ContextType.ACTIONGROUP);
        Action curGroup = getCurrentContextNode();
        curGroup.setStatus(new Status(status));
        curGroup.setIsRunning(false);

        revertContext();

        //content propagation
        IJsonReportNode parentNode = getCurrentContextNode();

        if (parentNode instanceof Action) {
            ((Action) parentNode).getRelatedMessages().addAll(curGroup.getRelatedMessages());
            ((Action) parentNode).getBugs().addAll(curGroup.getBugs());
        }

        liveTestCaseWriter.writeNode(curGroup);
    }

    public void createVerification(String name, String description, StatusDescription status, ComparisonResult result) {
        assertState(ContextType.ACTION, ContextType.ACTIONGROUP, ContextType.TESTCASE);

        try {
            if (result != null && result.getMetaData() != null) {
                result = new ComparisonResult(result);

                EnumReplacer.replaceEnums(result,
                        dictionaryManager.getDictionary(result.getMetaData().getDictionaryURI())
                                .getMessages().get(result.getMetaData().getMsgName()));
            }
        } catch (Exception e) {
            logger.error("unable to set enum aliases", e);
        }

        Verification curVerification = new Verification();
        curVerification.setName(name);
        curVerification.setDescription(description);
        curVerification.setStatus(status);

        IJsonReportNode curNode = getCurrentContextNode();

        if (result != null) {
            if (result.getMetaData() != null) {
                curVerification.setMessageId(result.getMetaData().getId());
            }
            else {
                logger.warn("comparison result does not contain metadata - name='{}', description='{}'", name, description);
            }

            Action firstActionParent = context.getFirstParentNode(Action.class);
            Long firstActionParentId = firstActionParent == null ? null : firstActionParent.getId();


            TestCase firstTestCaseParent = context.getFirstParentNode(TestCase.class);

            Set<Bug> reproduced = result.getReproducedBugs().stream()
                    .distinct()
                    .map(descr -> getOrSetKnownBug(firstTestCaseParent, descr))
                    .collect(Collectors.toSet());

            reproduced.forEach(bug -> bug.updateStatus(KnownBugStatus.REPRODUCED));

            Set<Bug> notReproduced = Sets.difference(result.getAllKnownBugs(), result.getReproducedBugs()).stream()
                    .distinct()
                    .map(descr -> getOrSetKnownBug(firstTestCaseParent, descr))
                    .collect(Collectors.toSet());

            Set<Bug> combined = Sets.union(reproduced, notReproduced);

            if (firstActionParentId != null) {
                combined.forEach(bug -> bug.getRelatedActionIds().add(firstActionParentId));
            }

            firstTestCaseParent.addSubNodes(combined);
            curNode.addSubNodes(combined);

            curVerification.setEntries(result.getResults().isEmpty()
                    ? Collections.singletonList(new VerificationEntry(result))
                    : result.getResults().values().stream().map(VerificationEntry::new).collect(Collectors.toList()));
        }
        curNode.addSubNodes(curVerification);
    }

    public void createMessage(MessageLevel level, String... messages) {
        createMessage(level, null, null, null, messages);
    }

    public void createMessage(MessageLevel level, Throwable e, String... messages) {
        createMessage(level, e, null, null, messages);
    }

    public void createMessage(TextColor color, TextStyle style, String... messages) {
        createMessage(null, null, color, style, messages);
    }

    private void createMessage(MessageLevel level, Throwable e, TextColor color, TextStyle style, String... messages) {
        assertState(ContextType.TESTCASE, ContextType.ACTION, ContextType.ACTIONGROUP);

        if (ArrayUtils.isEmpty(messages) && e == null) {
            throw new ScriptRunException("Message array is empty");
        }

        getCurrentContextNode().addSubNodes(Arrays.stream(messages)
                .map(m -> new CustomMessage(m, Objects.toString(color, null), Objects.toString(style, null), level, e))
                .collect(Collectors.toSet()));
    }

    public void createException(Throwable cause) {
        if (cause != null) {
            if (getCurrentContextNode() != null) {
                getCurrentContextNode().addException(cause);
            } else {
                if(reportRoot.getException() == null) {
                    reportRoot.setException(new ReportException(cause));
                }
            }
        }
    }

    public void createParametersTable(IMessage message) {
        assertState(ContextType.ACTION, ContextType.ACTIONGROUP);

        if (message != null) {
            ((Action) getCurrentContextNode()).setParameters(Parameter.fromMessage(message));
            ((Action) getCurrentContextNode()).getRelatedMessages().add(message.getMetaData().getId());
        }
    }

    public void createTable(ReportTable table) {
        assertState(ContextType.ACTION, ContextType.ACTIONGROUP, ContextType.TESTCASE, ContextType.SCRIPT);

        IJsonReportNode currentNode = getCurrentContextNode();

        if("Messages".equals(table.getName())) {
            List<Message> messages = table.getRows().stream().map(Message::new).collect(Collectors.toList());

            if (currentNode instanceof Action) {
                long actionId = ((Action) currentNode).getId();

                for (Message message : messages) {
                    messageToActionIdMap.computeIfAbsent(message.getId(), k -> new HashSet<>()).add(actionId);
                }
            }

            if (currentNode instanceof TestCase) {
                for (Message message : messages) {
                    message.setRelatedActions(messageToActionIdMap.computeIfAbsent (message.getId(), k -> new HashSet<>()));
                }
            }
            currentNode.addSubNodes(messages);
        } else {
            currentNode.addSubNodes(new CustomTable(table.getRows()));
        }
    }

    public void createLogTable(List<String> header, List<LoggerRow> rows) {
//        FIXME: please rollback these changes when logs will be used on the front
//        assertState(ContextType.TESTCASE, ContextType.ACTION, ContextType.ACTIONGROUP);
//
//        List<IJsonReportNode> logs = rows.stream().map(LogEntry::new).collect(Collectors.toList());
//        getCurrentContextNode().addSubNodes(logs);
    }

    public void setOutcomes(OutcomeCollector outcomes) {
        assertState(ContextType.TESTCASE);
        TestCase curTestCase = getCurrentContextNode();

        for (String group : outcomes.getGroupOrder()) {
            for (String name : outcomes.getDefinedOutcomes().get(group)) {
                curTestCase.getOutcomes()
                        .add(new OutcomeSummary(name, outcomes.getPassedCount(group, name), outcomes.getConditionallyPassedCount(group, name),
                                outcomes.getFailedCount(group, name)));
            }
        }
    }

    public IReportStats getReportStats() {
        return reportStats;
    }

    public void closeReport() {
        assertState(false, null, ContextType.SCRIPT);
        reportRoot.setFinishTime(Instant.now());
        initProperties();
        exportToFile(reportRoot, REPORT_ROOT_FILE_NAME);

        if (liveTestCaseWriter != null) {
            logger.warn("test case was not closed properly - removing live report directory now");
            liveTestCaseWriter.clearDirectory();
            liveTestCaseWriter = null;
        }
    }

    public void createLinkToReport(String linkToReport) {
        assertState(ContextType.ACTION, ContextType.ACTIONGROUP);
        getCurrentContextNode().addSubNodes(new CustomLink(linkToReport));
    }

    public void flush() {
        //do nothing
    }

    private Action getCurrentRootAction() {
        Context context = this.context;

        if (context == null || (context.cur != ContextType.ACTIONGROUP && context.cur != ContextType.ACTION)) {
            return null;
        }

        while (context.prev != null && (context.prev.cur == ContextType.ACTIONGROUP || context.prev.cur == ContextType.ACTION)) {
            context = context.prev;
        }

        return (Action) context.node;
    }
}
