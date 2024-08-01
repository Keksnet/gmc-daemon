package de.swiftbyte.gmc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.swiftbyte.gmc.cache.CacheModel;
import de.swiftbyte.gmc.common.entity.NodeSettings;
import de.swiftbyte.gmc.common.entity.ResourceUsage;
import de.swiftbyte.gmc.common.packet.node.NodeHeartbeatPacket;
import de.swiftbyte.gmc.common.packet.node.NodeLogoutPacket;
import de.swiftbyte.gmc.server.GameServer;
import de.swiftbyte.gmc.service.BackupService;
import de.swiftbyte.gmc.stomp.StompHandler;
import de.swiftbyte.gmc.utils.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.FileUtils;
import org.jline.terminal.impl.DumbTerminal;
import org.springframework.shell.component.context.ComponentContext;
import oshi.SystemInfo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

@Getter
@Slf4j
public class Node extends Thread {

    public static Node INSTANCE;

    private ConnectionState connectionState;

    @Setter
    private String nodeName;
    @Setter
    private String teamName;
    @Setter
    private String serverPath;
    private boolean manageFirewallAutomatically;

    private NodeSettings.AutoBackup autoBackup;
    private boolean isAutoUpdateEnabled;
    private String serverStopMessage;
    private String serverRestartMessage;

    private String secret;
    private String nodeId;

    private boolean isUpdating;

    public Node() {

        INSTANCE = this;

        this.connectionState = (ConfigUtils.hasKey("node.id") && ConfigUtils.hasKey("node.secret")) ? ConnectionState.NOT_CONNECTED : ConnectionState.NOT_JOINED;

        this.nodeName = "daemon";
        this.teamName = "gmc";

        serverPath = "servers";

        getCachedNodeInformation();
        BackupService.initialiseBackupService();
        NodeUtils.checkInstallation();
    }

    private void getCachedNodeInformation() {

        log.debug("Getting cached information...");

        nodeId = ConfigUtils.get("node.id", "dummy");
        secret = ConfigUtils.get("node.secret", "dummy");

        File cacheFile = new File("./cache.json");

        if (!cacheFile.exists()) {
            log.debug("No cache file found. Skipping...");
            return;
        }

        try {
            CacheModel cacheModel = CommonUtils.getObjectReader().readValue(new File("./cache.json"), CacheModel.class);
            nodeName = cacheModel.getNodeName();
            teamName = cacheModel.getTeamName();
            serverPath = cacheModel.getServerPath();
            isAutoUpdateEnabled = cacheModel.isAutoUpdateEnabled();
            manageFirewallAutomatically = cacheModel.isManageFirewallAutomatically();

            serverStopMessage = cacheModel.getServerStopMessage();
            serverRestartMessage = cacheModel.getServerRestartMessage();

            if (cacheModel.getAutoBackup() != null) autoBackup = cacheModel.getAutoBackup();
            else autoBackup = new NodeSettings.AutoBackup();

            log.debug("Got cached information.");

        } catch (IOException e) {
            log.error("An unknown error occurred while getting cached information.", e);
        }
    }

    public void shutdown() {
        if (connectionState == ConnectionState.DELETING) return;
        NodeLogoutPacket logoutPacket = new NodeLogoutPacket();
        logoutPacket.setReason("Terminated by user");
        log.debug("Sending shutdown packet...");
        StompHandler.send("/app/node/logout", logoutPacket);
        log.info("Disconnecting from backend...");
        log.debug("Deleting temporary files...");
        try {
            FileUtils.deleteDirectory(new File(NodeUtils.TMP_PATH));
        } catch (IOException e) {
            log.warn("An error occurred while deleting the temporary directory.");
        }
        log.debug("Caching information...");
        NodeUtils.cacheInformation(this);
    }

    public void joinTeam() {
        log.debug("Start joining a team...");
        setConnectionState(ConnectionState.JOINING);

        Integer token;
        token = tryGetTokenHeadless();
        if (token != null) {
            joinTeamWithValidatedToken(token);
            return;
        }

        ComponentContext<?> context = NodeUtils.promptForInviteToken();
        try {
            token = NodeUtils.getValidatedToken(context.get("inviteToken"));

            while (token == null) {
                log.error("The entered token does not match the expected pattern. Please enter it as follows: XXX-XXX");
                token = NodeUtils.getValidatedToken(NodeUtils.promptForInviteToken().get("inviteToken"));
            }

            joinTeamWithValidatedToken(token);
        } catch (NoSuchElementException e) {
            setConnectionState(ConnectionState.NOT_JOINED);

            if (Application.getTerminal() instanceof DumbTerminal) {
                log.error("The prompt to enter the Invite Token could not be created due to an error. Please try to invite the node with the command 'join <token>'.");
            } else {
                log.debug("An empty entry was made. Restart the join process.");
                joinTeam();
            }
        }
    }

    public Integer tryGetTokenHeadless() {
        String inviteToken = System.getenv("GMC_INVITE_TOKEN");
        if (inviteToken == null) {
            return null;
        }

        log.debug("Got invite token from environment variable: '{}'", inviteToken);
        Integer token = NodeUtils.getValidatedToken(inviteToken);

        if (token == null) {
            log.error("The invite token is incorrect. Please check your input.");
            return null;
        }

        return token;
    }

    public void joinTeam(String token) {
        setConnectionState(ConnectionState.JOINING);
        log.debug("Start joining a team with Invite Token '{}'...", token);

        Integer realToken = NodeUtils.getValidatedToken(token);

        if (realToken == null) {
            log.error("The entered token does not match the expected pattern. Please enter it as follows: XXX-XXX");
            setConnectionState(ConnectionState.NOT_JOINED);
            return;
        }

        joinTeamWithValidatedToken(realToken);
    }

    private void joinTeamWithValidatedToken(int token) {

        log.debug("Start joining with Invite Token '{}'...", token);

        OkHttpClient client = new OkHttpClient();

        FormBody body = new FormBody.Builder()
                .add("inviteToken", String.valueOf(token))
                .build();

        Request request = new Request.Builder()
                .url(Application.getBackendUrl() + "/node/register")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                log.debug("Received error code {} with message '{}'", response.code(), response.body().string());
                log.error("The specified Invite Token is incorrect. Please check your input.");
                joinTeam();
                return;
            }

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            nodeId = jsonNode.get("nodeId").asText();
            secret = jsonNode.get("nodeSecret").asText();

        } catch (IOException e) {
            log.error("An unknown error occurred.", e);
            joinTeam();
            return;
        }

        ConfigUtils.store("node.id", nodeId);
        ConfigUtils.store("node.secret", secret);

        setConnectionState(ConnectionState.NOT_CONNECTED);
        connect();
    }

    public void connect() {
        if (connectionState == ConnectionState.RECONNECTING) {
            log.info("Reconnecting to backend...");
            StompHandler.initialiseStomp();
        } else {
            log.info("Connecting to backend...");
            setConnectionState(ConnectionState.CONNECTING);
            if (!StompHandler.initialiseStomp()) {
                setConnectionState(ConnectionState.RECONNECTING);
                ServerUtils.getCachedServerInformation();
            }
        }
    }

    public void updateDaemon() {
        try {
            if (isUpdating) {
                log.error("The daemon is already updating!");
                return;
            }
            isUpdating = true;
            log.debug("Start updating daemon to latest version! Downloading...");

            NodeUtils.downloadLatestDaemonInstaller();

            log.debug("Starting installer and restarting daemon...");

            String daemonSetupName = NodeUtils.getDaemonSetupName();
            try {
                List<String> installCommand = new ArrayList<>();

                // invoke the installer
                installCommand.add(CommonUtils.convertPathSeparator(Path.of(NodeUtils.TMP_PATH, daemonSetupName).toAbsolutePath()));

                Path logFile = Path.of("log/latest-installation.log");
                if (OsUtils.OPERATING_SYSTEM == OsUtils.OperatingSystem.WINDOWS) {
                    // silent mode
                    installCommand.add("/SILENT");
                    installCommand.add("/SUPPRESSMSGBOXES");

                    // log file
                    installCommand.add("/LOG=" + CommonUtils.convertPathSeparator(logFile.toAbsolutePath()));
                }

                log.debug("Starting installer with command: '{}'", installCommand);
                Process proc = Runtime.getRuntime().exec(installCommand.toArray(new String[0]));

                if (OsUtils.OPERATING_SYSTEM == OsUtils.OperatingSystem.LINUX) {
                    // redirecting output to log file on linux
                    InputStream inputStream = proc.getInputStream();
                    Files.copy(inputStream, logFile);

                    // redirecting error output to log file on linux
                    InputStream errorStream = proc.getErrorStream();
                    Files.copy(errorStream, Path.of("log/latest-installation-error.log"));
                }

                System.exit(0);
            } catch (IOException e) {
                log.error("An error occurred while starting the installer.", e);
                isUpdating = false;
            }
        } catch (Exception e) {
            log.error("An unknown error occurred while updating the daemon.", e);
            isUpdating = false;
        }

    }

    public void updateSettings(NodeSettings nodeSettings) {
        log.debug("Updating settings...");
        nodeName = nodeSettings.getName();

        if (!CommonUtils.isNullOrEmpty(nodeSettings.getServerPath())) serverPath = nodeSettings.getServerPath();
        else serverPath = "servers";

        if (nodeSettings.getAutoBackup() != null) autoBackup = nodeSettings.getAutoBackup();
        else autoBackup = new NodeSettings.AutoBackup();

        isAutoUpdateEnabled = nodeSettings.isEnableAutoUpdate();
        serverStopMessage = nodeSettings.getStopMessage();
        serverRestartMessage = nodeSettings.getRestartMessage();
        manageFirewallAutomatically = nodeSettings.isManageFirewallAutomatically();

        NodeUtils.cacheInformation(this);
        BackupService.updateAutoBackupSettings();
    }

    @Override
    public void run() {
        super.run();
        Application.getExecutor().scheduleAtFixedRate(updateRunnable, 0, 10, TimeUnit.SECONDS);
    }

    private final Runnable updateRunnable = () -> {
        if (connectionState == ConnectionState.CONNECTED) {

            NodeUtils.cacheInformation(this);

            NodeHeartbeatPacket heartbeatPacket = getNodeHeartbeatPacket();

            StompHandler.send("/app/node/heartbeat", heartbeatPacket);

            BackupService.deleteAllExpiredBackups();
        } else if (connectionState == ConnectionState.RECONNECTING) {
            connect();
        }
    };

    private static NodeHeartbeatPacket getNodeHeartbeatPacket() {
        SystemInfo systemInfo = new SystemInfo();
        NodeHeartbeatPacket heartbeatPacket = new NodeHeartbeatPacket();

        ResourceUsage resourceUsage = new ResourceUsage();
        resourceUsage.setRamBytes((systemInfo.getHardware().getMemory().getTotal() - systemInfo.getHardware().getMemory().getAvailable()) / (1024 * 1024));
        resourceUsage.setCpuPercentage(-1);
        resourceUsage.setCpuPercentage(-1);
        resourceUsage.setDiskBytes(-1);
        heartbeatPacket.setResourceUsage(resourceUsage);


        ArrayList<NodeHeartbeatPacket.GameServerUpdate> gameServerUpdates = new ArrayList<>();
        for (GameServer server : GameServer.getAllServers()) {
            NodeHeartbeatPacket.GameServerUpdate gameServerUpdate = new NodeHeartbeatPacket.GameServerUpdate();
            gameServerUpdate.setState(server.getState());
            gameServerUpdate.setId(server.getServerId());
            gameServerUpdate.setPlayerCount(server.getCurrentOnlinePlayers());
            gameServerUpdates.add(gameServerUpdate);
        }
        heartbeatPacket.setGameServers(gameServerUpdates);
        return heartbeatPacket;
    }

    public void setConnectionState(ConnectionState connectionState) {
        log.debug("Connection state changed from {} to {}", this.connectionState, connectionState.name());
        this.connectionState = connectionState;
    }
}
