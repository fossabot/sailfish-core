/******************************************************************************
 * Copyright 2009-2018 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.sf.statictesting;

import static com.exactpro.sf.common.services.ServiceName.DEFAULT_ENVIRONMENT;
import static com.google.common.collect.Lists.reverse;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.apache.commons.io.FilenameUtils.concat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.log4j.PropertyConfigurator;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.RunWith;
import org.junit.runner.notification.RunListener;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.exactpro.sf.aml.AML;
import com.exactpro.sf.aml.AMLException;
import com.exactpro.sf.aml.AMLSettings;
import com.exactpro.sf.aml.generator.Alert;
import com.exactpro.sf.aml.generator.AlertType;
import com.exactpro.sf.aml.generator.GeneratedScript;
import com.exactpro.sf.aml.iomatrix.MatrixFileTypes;
import com.exactpro.sf.center.ISFContext;
import com.exactpro.sf.center.IVersion;
import com.exactpro.sf.common.services.ServiceName;
import com.exactpro.sf.configuration.suri.SailfishURI;
import com.exactpro.sf.configuration.workspace.FolderType;
import com.exactpro.sf.configuration.workspace.IWorkspaceDispatcher;
import com.exactpro.sf.scriptrunner.IConnectionManager;
import com.exactpro.sf.scriptrunner.ScriptContext;
import com.exactpro.sf.scriptrunner.languagemanager.AutoLanguageFactory;
import com.exactpro.sf.services.ServiceDescription;
import com.exactpro.sf.services.ServiceMarshalManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.qameta.allure.Allure;

@RunWith(Parameterized.class)
public class StaticMatrixTest extends AbstractStaticTest {
    private static final ObjectReader VARIABLE_SET_READER = new ObjectMapper(new YAMLFactory()).readerFor(new TypeReference<Map<String, Map<String, String>>>() {});

    private static final String WORKSPACES_LONG_OPT = "workspaces";
    private static final String MATRICES_LONG_OPT = "matrices";
    private static final String SERVICES_LONG_OPT = "services";
    private static final String REPORT_LONG_OPT = "report";
    private static final String LOGGER_CONFIG_LONG_OPT = "logger-config";
    private static final String LOGS_ZIP_LONG_OPT = "logs-zip";
    private static final String SKIP_OPTIONAL_LONG_OPT = "skip-optional";
    private static final String VARIABLE_SETS_LONG_OPT = "variable-sets";
    private static final String ENVIRONMENT_VARIABLE_SET_LONG_OPT = "environment-variable-set";
    private static final String TEMP_DIRECTORY_LONG_OPT = "temp-directory";
    private static final String LANGUAGE_URI_LONG_OPT = "language-uri";

    private static List<Object[]> testData = Collections.emptyList();

    private final ISFContext context;
    private final File matrix;
    private final File services;
    private final boolean skipOptional;
    private final SailfishURI languageUri;

    public StaticMatrixTest(ISFContext context, File matrix, File services, boolean skipOptional, SailfishURI languageUri) {
        this.context = context;
        this.matrix = matrix;
        this.services = services;
        this.skipOptional = skipOptional;
        this.languageUri = languageUri;
    }

    @Parameters(name = "{1}")
    public static Iterable<Object[]> data() {
        return testData;
    }

    @Test
    public void testMatrix() throws Exception {
        System.out.println("Testing matrix: " + matrix.getCanonicalPath());

        try {
            compileMatrix(context, matrix, skipOptional, languageUri);
        } catch (AMLException e) {
            attachFiles();

            String errors = e.getAlertCollector()
                    .getAlerts(AlertType.ERROR)
                    .stream()
                    .map(Alert::toString)
                    .collect(Collectors.joining(System.lineSeparator()));

            throw new AssertionError("Failed to compile matrix. Errors: " + System.lineSeparator() + errors, e);
        } catch(Throwable e) {
            attachFiles();
            throw e;
        }
    }

    private void attachFiles() throws IOException {
        try(InputStream stream = new FileInputStream(matrix)) {
            String name = matrix.getName();
            Allure.addAttachment(name, Files.probeContentType(matrix.toPath()), stream, FilenameUtils.getExtension(name));
        }

        try(InputStream stream = new FileInputStream(services)) {
            Allure.addAttachment("Services.zip", Files.probeContentType(services.toPath()), stream, "zip");
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            CommandLine commandLine = parseArguments(args);

            if (commandLine == null) {
                return;
            }

            List<File> workspacePaths = stream(commandLine.getOptionValues(WORKSPACES_LONG_OPT))
                    .map(File::new)
                    .distinct()
                    .collect(toList());

            File matricesPath = (File)commandLine.getParsedOptionValue(MATRICES_LONG_OPT);

            Set<File> servicesPaths = stream(commandLine.getOptionValues(SERVICES_LONG_OPT))
                    .map(File::new)
                    .collect(Collectors.toSet());

            File reportDir = (File)commandLine.getParsedOptionValue(REPORT_LONG_OPT);
            File loggerConfigFile = (File)commandLine.getParsedOptionValue(LOGGER_CONFIG_LONG_OPT);
            File logsZipFile = (File)commandLine.getParsedOptionValue(LOGS_ZIP_LONG_OPT);
            boolean skipOptional = commandLine.hasOption(SKIP_OPTIONAL_LONG_OPT);
            File variableSetsFile = (File)commandLine.getParsedOptionValue(VARIABLE_SETS_LONG_OPT);
            String environmentVariableSet = commandLine.getOptionValue(ENVIRONMENT_VARIABLE_SET_LONG_OPT);
            File tempDirectory = (File)commandLine.getParsedOptionValue(TEMP_DIRECTORY_LONG_OPT);
            SailfishURI languageUri = ObjectUtils.defaultIfNull(SailfishURI.parse(commandLine.getOptionValue(LANGUAGE_URI_LONG_OPT)), AutoLanguageFactory.URI);

            if(!reportDir.exists() && !reportDir.mkdirs()) {
                throw new Exception("Report directory cannot be created: " + reportDir.getCanonicalPath());
            }

            if(loggerConfigFile == null) {
                for(File workspacePath : reverse(workspacePaths)) {
                    File layerConfigFile = new File(workspacePath, concat("cfg", "log.properties"));

                    if(layerConfigFile.exists() && layerConfigFile.isFile()) {
                        loggerConfigFile = layerConfigFile;
                        break;
                    }
                }

                if(loggerConfigFile == null) {
                    throw new Exception("Unable to locate logger config file");
                }
            } else if(!loggerConfigFile.isFile()) {
                throw new Exception("Logger config is not a file: " + loggerConfigFile.getCanonicalPath());
            }

            if(logsZipFile.exists()) {
                throw new Exception("Logs zip file already exists: " + logsZipFile.getCanonicalPath());
            }

            if(variableSetsFile != null && !variableSetsFile.isFile()) {
                throw new Exception("Variable sets is not a file: " + variableSetsFile.getCanonicalPath());
            }

            if (tempDirectory == null) {
                tempDirectory = FileUtils.getTempDirectory();
            } else if (tempDirectory.exists() && !tempDirectory.isDirectory()) {
                throw new Exception("Temp directory is not a directory: " + tempDirectory.getCanonicalPath());
            }

            tempDirectory.mkdirs();

            File lastWorkspaceLayer = Files.createTempDirectory(tempDirectory.toPath(), "statictest").toFile();
            File logsFolder = new File(lastWorkspaceLayer, "logs");

            logsFolder.mkdirs();
            System.setProperty("sf.log.dir", logsFolder.getAbsolutePath());
            PropertyConfigurator.configure(loggerConfigFile.toURI().toURL());
            workspacePaths.add(lastWorkspaceLayer);

            ISFContext context = initContext(workspacePaths);

            try(PrintWriter writer = new PrintWriter(new File(reportDir, "environment.properties"))) {
                System.out.println("--------------------------------");
                System.out.println("Components:");
                System.out.println("--------------------------------");
                System.out.println("Core - " + context.getVersion());

                writer.printf("core=%s", context.getVersion()).println();

                for(IVersion version : context.getPluginVersions()) {
                    System.out.println(version.getAlias() + " - " + version.buildVersion());
                    writer.printf("%s=%s", version.getAlias(), version.buildVersion()).println();
                }

                System.out.println("--------------------------------");
            }

            File services = Files.createTempFile(tempDirectory.toPath(), "services", ".zip").toFile();

            try (AutoCloseable closeable = context::dispose) {
                if (languageUri != AutoLanguageFactory.URI && !context.getLanguageManager().containsLanguage(languageUri)) {
                    throw new Exception("Unknown language URI: " + languageUri);
                }

                if(variableSetsFile != null) {
                    loadVariableSets(variableSetsFile, environmentVariableSet, context);
                }

                loadServices(servicesPaths, context);

                if (variableSetsFile != null) {
                    servicesPaths.add(variableSetsFile);
                }

                zipFile(servicesPaths, services);

                testData = new ArrayList<>();

                for (File matrix : getMatrices(matricesPath)) {
                    testData.add(ArrayUtils.toArray(context, matrix, services, skipOptional, languageUri));
                }

                System.setProperty("allure.results.directory", reportDir.getCanonicalPath());

                RunListener listener = new ExtendedAllureJunit4();
                JUnitCore core = new JUnitCore();

                core.addListener(listener);
                core.run(StaticMatrixTest.class);
            } finally {
                try {
                    if(logsZipFile.exists()) {
                        throw new Exception("Logs zip file already exists: " + logsZipFile.getCanonicalPath());
                    }

                    zipFile(Collections.singleton(logsFolder), logsZipFile);
                } finally {
                    deleteQuietly(services);
                    deleteQuietly(lastWorkspaceLayer);
                }
            }
        } catch(ParseException e) {
            // do nothing
        }
    }

    private static CommandLine parseArguments(String[] args) throws ParseException {
        Options options = new Options();

        Option workspacePathOption = Option.builder("w")
                .longOpt(WORKSPACES_LONG_OPT)
                .hasArg()
                .argName("path")
                .required()
                .type(File.class)
                .numberOfArgs(Option.UNLIMITED_VALUES)
                .desc("paths to workspace layers")
                .build();

        Option matricesPathOption = Option.builder("m")
                .longOpt(MATRICES_LONG_OPT)
                .hasArg()
                .argName("path")
                .required()
                .type(File.class)
                .desc("path to matrices (file or directory)")
                .build();

        Option servicesPathOption = Option.builder("s")
                .longOpt(SERVICES_LONG_OPT)
                .hasArg()
                .argName("paths")
                .required()
                .type(File.class)
                .numberOfArgs(Option.UNLIMITED_VALUES)
                .desc("paths to services (files or directories)")
                .build();

        Option reportDirOption = Option.builder("r")
                .longOpt(REPORT_LONG_OPT)
                .hasArg()
                .argName("path")
                .required()
                .type(File.class)
                .desc("path to report directory")
                .build();

        Option loggerConfigFileOption = Option.builder("l")
                .longOpt(LOGGER_CONFIG_LONG_OPT)
                .hasArg()
                .argName("file")
                .type(File.class)
                .desc("path to logger configuration file")
                .build();

        Option logsZipFileOption = Option.builder("z")
                .longOpt(LOGS_ZIP_LONG_OPT)
                .hasArg()
                .argName("file")
                .required()
                .type(File.class)
                .desc("path for logs zip file")
                .build();

        Option skipOptionalOption = Option.builder("so")
                .longOpt(SKIP_OPTIONAL_LONG_OPT)
                .desc("skip optional actions")
                .build();

        Option variableSetsFileOption = Option.builder("vs")
                .longOpt(VARIABLE_SETS_LONG_OPT)
                .hasArg()
                .argName("file")
                .type(File.class)
                .desc("path to variable sets file")
                .build();

        Option environmentVariableSetOption = Option.builder("ev")
                .longOpt(ENVIRONMENT_VARIABLE_SET_LONG_OPT)
                .hasArg()
                .argName("name")
                .desc("name of variable set to apply to environment")
                .build();

        Option tempDirOption = Option.builder("td")
                .longOpt(TEMP_DIRECTORY_LONG_OPT)
                .hasArg()
                .argName("directory")
                .type(File.class)
                .desc("path to temp directory")
                .build();

        Option languageUriOption = Option.builder("lu")
                .longOpt(LANGUAGE_URI_LONG_OPT)
                .hasArg()
                .argName("uri")
                .desc("sailfish URI for desired matrix language")
                .build();

        options.addOption(workspacePathOption);
        options.addOption(matricesPathOption);
        options.addOption(servicesPathOption);
        options.addOption(reportDirOption);
        options.addOption(loggerConfigFileOption);
        options.addOption(logsZipFileOption);
        options.addOption(skipOptionalOption);
        options.addOption(variableSetsFileOption);
        options.addOption(environmentVariableSetOption);
        options.addOption(tempDirOption);
        options.addOption(languageUriOption);

        try {
            return new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            new HelpFormatter().printHelp(StaticMatrixTest.class.getSimpleName(), options, true);
            throw e;
        }
    }

    private static void loadServices(Set<File> paths, ISFContext context) throws Exception {
        ServiceMarshalManager manager = new ServiceMarshalManager(context.getStaticServiceManager(), context.getDictionaryManager());
        IConnectionManager connectionManager = context.getConnectionManager();
        List<ServiceDescription> services = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for(File path : paths) {
            for(File service : getServices(path)) {
                try(InputStream stream = new FileInputStream(service)) {
                    manager.unmarshalServices(stream, FilenameUtils.isExtension(service.getName(), "zip"), services, errors);
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new Exception("Failed to load services. Errors: " + System.lineSeparator() + String.join(System.lineSeparator(), errors));
        }

        for (ServiceDescription service : services) {
            service.setEnvironment(DEFAULT_ENVIRONMENT);
            ServiceName serviceName = service.getServiceName();

            if (connectionManager.getService(serviceName) != null) {
                connectionManager.removeService(serviceName, null); // remove default service from plugin
            }

            connectionManager.addService(service, null).get();
        }
    }

    private static void loadVariableSets(File variableSetsFile, String environmentVariableSet, ISFContext context) throws IOException {
        if(environmentVariableSet == null) {
            return;
        }

        IConnectionManager connectionManager = context.getConnectionManager();
        Map<String, Map<String, String>> variableSets = VARIABLE_SET_READER.readValue(variableSetsFile);

        variableSets.forEach(connectionManager::putVariableSet);
        connectionManager.setEnvironmentVariableSet(DEFAULT_ENVIRONMENT, environmentVariableSet);
    }

    private static List<File> getMatrices(File path) throws IOException {
        return getFiles(path, file -> {
            MatrixFileTypes type = MatrixFileTypes.detectFileType(file.getName());
            return type != MatrixFileTypes.UNKNOWN && type != MatrixFileTypes.JSON;
        });
    }

    private static List<File> getServices(File path) throws IOException {
        return getFiles(path, file -> {
            return FilenameUtils.isExtension(file.getName(), Arrays.asList("zip", "xml"));
        });
    }

    private static List<File> getFiles(File path, Predicate<File> filter) throws IOException {
        return Files.walk(path.toPath())
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .filter(filter)
                .collect(toList());
    }

    private static void compileMatrix(ISFContext context, File matrix, boolean skipOptional, SailfishURI languageUri) throws Exception {
        IWorkspaceDispatcher dispatcher = context.getWorkspaceDispatcher();
        File reportFolder = dispatcher.getFolder(FolderType.REPORT);
        File copiedMatrix = new File(reportFolder, matrix.getName());
        Files.copy(matrix.toPath(), copiedMatrix.toPath(), StandardCopyOption.REPLACE_EXISTING);

        AMLSettings settings = new AMLSettings();
        settings.setAutoStart(true);
        settings.setBaseDir(matrix.getName() + System.currentTimeMillis());
        settings.setContinueOnFailed(true);
        settings.setLanguageURI(languageUri);
        settings.setMatrixPath(matrix.getName());
        settings.setSkipOptional(skipOptional);

        AML aml = new AML(settings,
                dispatcher,
                context.getAdapterManager(),
                context.getEnvironmentManager(),
                context.getDictionaryManager(),
                context.getStaticServiceManager(),
                context.getLanguageManager(),
                context.getActionManager(),
                context.getUtilityManager(),
                context.getCompilerClassPath());

        ScriptContext scriptContext = new ScriptContext(context, null, null, null, System.getProperty("user.name"), 0, DEFAULT_ENVIRONMENT);
        GeneratedScript script = aml.run(scriptContext, StandardCharsets.UTF_8.name());

        if(aml.getAlertCollector().getCount(AlertType.ERROR) != 0) {
            throw new AMLException("Errors detected", aml.getAlertCollector());
        }

        AML.compileScript(script, new File(reportFolder, settings.getBaseDir()), null, context.getCompilerClassPath());
    }

    private static void zipFile(Set<File> sourceFiles, File zipFile) throws IOException {
        try(ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for(File sourceFile : sourceFiles) {
                zipFile(sourceFile, sourceFile.getName(), stream);
            }
        }
    }

    private static void zipFile(File file, String name, ZipOutputStream stream) throws IOException {
        if(file.isDirectory()) {
            for(File child : file.listFiles()) {
                zipFile(child, name + "/" + child.getName(), stream);
            }

            return;
        }

        try(InputStream fileStream = new FileInputStream(file)) {
            stream.putNextEntry(new ZipEntry(name));
            IOUtils.copy(fileStream, stream);
        }
    }
}
