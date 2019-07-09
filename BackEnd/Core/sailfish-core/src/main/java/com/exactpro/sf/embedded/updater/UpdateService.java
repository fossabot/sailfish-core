/*******************************************************************************
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

package com.exactpro.sf.embedded.updater;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.sf.common.util.EPSCommonException;
import com.exactpro.sf.configuration.workspace.FolderType;
import com.exactpro.sf.configuration.workspace.IWorkspaceDispatcher;
import com.exactpro.sf.configuration.workspace.WorkspaceSecurityException;
import com.exactpro.sf.embedded.IEmbeddedService;
import com.exactpro.sf.embedded.configuration.ServiceStatus;
import com.exactpro.sf.embedded.updater.configuration.UpdateServiceSettings;
import com.exactpro.sf.services.ITaskExecutor;
import com.exactpro.sf.storage.IMapableSettings;
import com.exactpro.sf.util.DateTimeUtility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

public class UpdateService implements IEmbeddedService {
    private static final Logger logger = LoggerFactory.getLogger(UpdateService.class);

    public static final String TIME_PATTERN = "HH:mm";
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeUtility.createFormatter(TIME_PATTERN);

    private static final long DEFAULT_COLLECT_DATA_TIMEOUT = 3;
    private static final String DEPLOYER_CFG_FILE = "deployer.cfg.xml";
    private static final String PATH_PARAMETER = "Path";
    private static final String SERVER_URL_PARAMETER = "ServerURL";
    private static final String PROTOCOL_PREFIX = "http://";

    private final ObjectMapper mapper = new ObjectMapper();
    private final IWorkspaceDispatcher wd;

    /**
     * Relative (to ROOT folder) path
     */
    private final String deployerPath;
    private final ITaskExecutor taskExecutor;

    private Future<?> updateCheckerFuture;
    private Future<?> updateFuture;

    private volatile List<ComponentUpdateInfo> componentUpdateInfos = Collections.emptyList();
    private final AtomicReference<ServiceStatus> serviceStatus = new AtomicReference<>(ServiceStatus.Disconnected);
    private volatile LocalDateTime lastCheckTime;
    private volatile String errorMsg;
    private volatile String updateErrorMsg;
    private volatile boolean updating;

    private boolean enableAutoUpdate;
    private DayOfWeek dayForUpdate;
    private LocalTime fromTime;
    private LocalTime toTime;

    private File deployerFile;

    private UpdateServiceSettings settings;

    public UpdateService(IWorkspaceDispatcher workspaceDispatcher, HierarchicalConfiguration config, ITaskExecutor taskExecutor) {
        this.wd = workspaceDispatcher;
        this.taskExecutor = taskExecutor;
        deployerPath = config.getString(PATH_PARAMETER, "");
    }

    @Override
    public synchronized void init() {
        try {
            logger.info("Start initializing...");

            if (getStatus() == ServiceStatus.Connected || getStatus() == ServiceStatus.Checking) {
                throw new IllegalStateException("Already init");
            }

            if (StringUtils.isEmpty(deployerPath)) {
                throw new IllegalStateException("Path to deployer wasn't set");
            }

            try {
                deployerFile = wd.getFile(FolderType.ROOT, deployerPath);
            } catch (FileNotFoundException e) {
                logger.warn("Deployer wasn't found in directory {}", deployerPath, e);
                throw new EPSCommonException("Deployer wasn't found", e);
            }

            readDeployerConfiguration();

            enableAutoUpdate = settings.isEnableAutoUpdate();
            if (enableAutoUpdate) {
                dayForUpdate = DayOfWeek.valueOf(Objects.requireNonNull(settings.getDayOfWeek(), "'Day of week ' parameter"));
                fromTime = LocalTime.parse(Objects.requireNonNull(settings.getFromTime(), "'From time ' parameter"), TIME_FORMATTER);
                toTime = LocalTime.parse(Objects.requireNonNull(settings.getToTime(), "'To time ' parameter"), TIME_FORMATTER);

                logger.debug("Auto update is enabled. Day for update {}; Interval start: {}; Interval end: {}", dayForUpdate, fromTime, toTime);
            }

            changeStatus(ServiceStatus.Connected);

            checkForUpdates(false);

            startCheckUpdateTask();

            this.errorMsg = "";

            logger.info("Initialized");
        } catch (Exception e) {
            logger.error("Can't init Update Service", e);
            setError(e);
            throw new EPSCommonException(e);
        }
    }

    private void changeStatus(ServiceStatus serviceStatus) {
        this.serviceStatus.set(serviceStatus);
    }

    public void checkUpdates() {
        try {
            checkForUpdates(false);
        } catch (Exception e) {
            logger.error("Error during user check request", e);
            setError(e);
            throw new EPSCommonException(e);
        }
    }

    private void checkForUpdates(boolean autoUpdateIfNeed) {
        List<ComponentUpdateInfo> updateInfos = Collections.emptyList();
        try {
            ServiceStatus prevStatus = serviceStatus.getAndUpdate(status -> ServiceStatus.Checking);
            if (prevStatus == ServiceStatus.Checking) {
                return;
            }

            logger.info("Collecting data...");
            String collectDataParameters = createCollectDataParameters();
            logger.debug("Exec {} with params {}", deployerFile.getAbsolutePath(), collectDataParameters);
            Process collectDataProcess = Runtime.getRuntime().exec(String.format("%s %s", deployerFile.getAbsolutePath(), collectDataParameters));
            boolean isFinished = collectDataProcess.waitFor(DEFAULT_COLLECT_DATA_TIMEOUT, TimeUnit.HOURS);
            int exitCode = isFinished ? collectDataProcess.exitValue() : -1;
            if (exitCode != 0) {
                if (isFinished) {
                    logger.error("Error during collecting sailfish data. Exit code: {}", exitCode);
                } else {
                    logger.error("The time limit for collecting data has been exceeded");
                }
            }

            logger.info("Checking for updates");

            String checkUpdateParameters = createCheckUpdateParameters();
            logger.debug("Exec {} with params {}", deployerFile.getAbsolutePath(), checkUpdateParameters);
            Process updateProcess = Runtime.getRuntime().exec(String.format("%s %s", deployerFile.getAbsolutePath(), checkUpdateParameters));
            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(updateProcess.getInputStream()))) {
                line = reader.readLine();
            }
            exitCode = updateProcess.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Deployer got an error during checking for updates; Exit code: " + exitCode);
            }

            updateInfos = Collections.unmodifiableList(mapper.readValue(line, mapper.getTypeFactory().constructCollectionType(List.class, ComponentUpdateInfo.class)));
            if (autoUpdateIfNeed && !updateInfos.isEmpty() && isTimeForAutoUpdate(DateTimeUtility.nowLocalDateTime())) {
                logger.info("Execute scheduled update");
                update();
            }
        } catch (InterruptedException e) {
            logger.warn("Check for update was interrupted", e);
        } catch (Exception e) {
            logger.error("Can't check updates", e);
            throw new EPSCommonException("Can't check updates", e);
        } finally {
            lastCheckTime = DateTimeUtility.nowLocalDateTime();
            this.componentUpdateInfos = updateInfos;
            changeStatus(ServiceStatus.Connected);
        }
    }

    public synchronized void update() {

        if (getStatus() == ServiceStatus.Error || getStatus() == ServiceStatus.Disconnected) {
            throw new IllegalStateException("Can't execute update in current state: " + serviceStatus);
        }

        if(updating) {
            throw new IllegalStateException("Update process already started");
        }
        logger.info("Start updating...");
        this.updating = true;

        updateFuture = taskExecutor.addTask(new UpdateTask());
    }

    private boolean isTimeForAutoUpdate(LocalDateTime dateTime) {
        if (!enableAutoUpdate) {
            return false;
        }
        LocalTime time = dateTime.toLocalTime();
        return DayOfWeek.from(dateTime) == dayForUpdate && time.isAfter(fromTime) && time.isBefore(toTime);
    }

    public LocalDateTime getLastCheckTime() {
        return lastCheckTime;
    }

    private String createCheckUpdateParameters() {
        StringBuilder builder = createCommonParameters("check");
        return builder.toString();
    }

    private String createUpdateParameters() {
        StringBuilder builder = createCommonParameters("update");

        addParameter(builder, "startTomcat");
        addParameter(builder, "checkTomcat");
        return builder.toString();
    }

    private String createActualizingDeployerParameters(File tmpDir) {
        StringBuilder builder = createCommonParameters("actualize");
        addParameter(builder, "moveDest", tmpDir.getAbsolutePath());
        return builder.toString();
    }

    private String createCollectDataParameters() {
        return createCommonParameters("collect").toString();
    }

    private StringBuilder createCommonParameters(String mode) {
        try {
            File deployerCfg = wd.getFile(FolderType.CFG, DEPLOYER_CFG_FILE);
            File logsFolder = wd.getFolder(FolderType.LOGS);
            File reportDir = wd.getFolder(FolderType.REPORT);
            StringBuilder builder = new StringBuilder();
            addParameter(builder, "cfg", deployerCfg.getAbsolutePath());
            addParameter(builder, "log", logsFolder.getAbsolutePath());
            addParameter(builder, "report", reportDir.getAbsolutePath());
            addParameter(builder, "serverUrl", getServerURL());

            addParameter(builder, "mode", mode);
            addParameter(builder, "quiet");
            return builder;
        } catch (FileNotFoundException e) {
            throw new EPSCommonException("Can't set parameters", e);
        }
    }

    private String getServerURL() {
        return PROTOCOL_PREFIX + settings.getHost() + ":" + settings.getPort();
    }

    private void addParameter(StringBuilder builder, String paramName) {
        addParameter(builder, paramName, null);
    }

    private void addParameter(StringBuilder builder, String paramName, String paramValue) {
        Objects.requireNonNull(builder, "'Builder' parameter");
        Objects.requireNonNull(paramName, "'Parameter name' parameter");
        builder.append(StringUtils.SPACE).append('-').append(paramName);
        if (paramValue != null) {
            builder.append(StringUtils.SPACE).append(paramValue);
        }
    }

    private void readDeployerConfiguration() {
        XMLConfiguration deployerCfg = new XMLConfiguration();

        try {
            File configFile = wd.getFile(FolderType.CFG, DEPLOYER_CFG_FILE);
            try (InputStream stream = new FileInputStream(configFile)) {
                deployerCfg.load(stream);
            }
        } catch (ConfigurationException | WorkspaceSecurityException e) {
            throw new EPSCommonException("Could not read [" + DEPLOYER_CFG_FILE + "] configuration file", e);
        } catch (FileNotFoundException e) {
            throw new EPSCommonException("Can't find deployer cfg file in SF workspaces", e);
        } catch (Exception e) {
            throw new EPSCommonException("Can't load deployer configuration", e);
        }

        if (settings != null && settings.getHost() == null) {
            String updateServerURLString = deployerCfg.getString(SERVER_URL_PARAMETER, "");
            try {
                URL url = new URL(updateServerURLString);
                settings.setHost(url.getHost());
                settings.setPort(url.getPort());
            } catch (MalformedURLException e) {
                throw new EPSCommonException("Wrong URL format in deployer configuration: " + updateServerURLString, e);
            }
        }
    }

    public String getUpdateErrorMsg() {
        return updateErrorMsg;
    }

    public boolean isUpdateRequire() {
        return componentUpdateInfos != null && !componentUpdateInfos.isEmpty();
    }

    public boolean isUpdating() {
        return updating;
    }

    @Override
    public synchronized void tearDown() {
        if (getStatus() == ServiceStatus.Disconnected) {
            return;
        }
        logger.info("Tear down");
        cancelCheckUpdateTask();

        if(updateFuture != null) {
            updateFuture.cancel(true);
            this.updateFuture = null;
        }

        this.componentUpdateInfos = null;
        changeStatus(ServiceStatus.Disconnected);
        logger.info("Update service disposed");
    }

    @Override
    public boolean isConnected() {
        return getStatus() == ServiceStatus.Connected;
    }

    @Override
    public ServiceStatus getStatus() {
        return serviceStatus.get();
    }

    @Override
    public String getErrorMsg() {
        return errorMsg;
    }

    private synchronized void cancelCheckUpdateTask() {
        if(updateCheckerFuture != null) {
            logger.info("Cancelling update checking task...");
            updateCheckerFuture.cancel(true);
            try {
                updateCheckerFuture.get();
            } catch (CancellationException e) {
                logger.info("Update checker task was cancelled", e);
            } catch (InterruptedException e) {
                logger.info("Update checker task was interrupted", e);
            } catch (ExecutionException e) {
                logger.info("Update checker task finished with an error", e);
            }
            this.updateCheckerFuture = null;
        } else {
            logger.info("Update checking task was already cancelled");
        }
    }

    private synchronized void startCheckUpdateTask() {
        if (updateCheckerFuture == null) {
            logger.info("Creating update checking task...");
            this.updateCheckerFuture = taskExecutor.addRepeatedTask(new UpdateChecker(), settings.getCheckUpdateTimeout(),
                    settings.getCheckUpdateTimeout(), TimeUnit.valueOf(settings.getTimeUnit()));
        } else {
            logger.warn("Update checking task was already created. Can't create another one");
        }
    }

    private void setError(Throwable t) {
        changeStatus(ServiceStatus.Error);
        StringBuilder error = new StringBuilder();
        error.append(t.getMessage());
        if (t.getCause() != null) {
            error.append(StringUtils.SPACE)
                    .append("(")
                    .append(t.getCause().getMessage())
                    .append(")");
        }
        this.errorMsg = error.toString();
    }

    @Override
    public void setSettings(IMapableSettings settings) {
        this.settings = (UpdateServiceSettings) settings;
    }

    public UpdateServiceSettings getSettings() {
        return settings;
    }

    public List<ComponentUpdateInfo> getComponentUpdateInfos() {
        return componentUpdateInfos;
    }

    public boolean hasCriticalError() {
        return getStatus() == ServiceStatus.Error
                && (StringUtils.isEmpty(deployerPath)
                    || deployerFile == null);
    }

    private class UpdateChecker implements Runnable {

        @Override
        public void run() {
            try {
                logger.info("Scheduled run");
                checkForUpdates(true);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                setError(e);
            }
        }
    }

    private class UpdateTask implements Runnable {
        @Override
        public void run() {
            try {
                cancelCheckUpdateTask();

                logger.info("Actualizing deployer...");
                Path tempDirectory = Files.createTempDirectory("deployer");
                String updateDeployerParameters = createActualizingDeployerParameters(tempDirectory.toFile());

                logger.debug("Exec {} with params {}", deployerFile.getAbsolutePath(), updateDeployerParameters);
                Process actualizeDeployer = Runtime.getRuntime().exec(String.format("%s %s", deployerFile.getAbsolutePath(), updateDeployerParameters));
                int exitCode = actualizeDeployer.waitFor();
                if (exitCode != 0) {
                    throw new EPSCommonException("Can't actualize deployer; Exit code: " + exitCode);
                }

                Path actualDeployerPath = tempDirectory.resolve(deployerFile.getName()).toAbsolutePath();

                logger.info("Updating Sailfish...");
                String updateParameters = createUpdateParameters();

                logger.debug("Exec {} with params {}", actualDeployerPath, updateParameters);
                Process updateSailfish = Runtime.getRuntime().exec(String.format("%s %s", actualDeployerPath.toString(), updateParameters));
                exitCode = updateSailfish.waitFor();
                if (exitCode != 0) {
                    throw new EPSCommonException("Problem during updating Sailfish; Exit code: " + exitCode);
                }
            } catch (InterruptedException e) {
                logger.info("Update interrupted", e);
            } catch (RuntimeException e) {
                logger.error(e.getMessage(), e);
                updateFailed(e);
            } catch (IOException e) {
                logger.error("Can't execute update", e);
                updateFailed(e);
            } finally {
                UpdateService.this.updating = false;
            }
        }

        private void updateFailed(Throwable e) {
            String msg = "Last attempt to update Sailfish was failed: " + e.getMessage();
            if (e.getCause() != null) {
                msg += " (" + e.getCause().getMessage() + ")";
            }
            UpdateService.this.updateErrorMsg = msg;
            startCheckUpdateTask();
        }

    }

    public static class ComponentUpdateInfo {

        private final String name;

        private final String version;

        @JsonCreator
        public ComponentUpdateInfo(@JsonProperty(value = "name", required = true) String name,
                                   @JsonProperty(value = "version", required = true) String version) {
            this.name = name;
            this.version = version;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }
    }
}
