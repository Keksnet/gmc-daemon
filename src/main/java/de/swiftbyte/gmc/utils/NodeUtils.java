package de.swiftbyte.gmc.utils;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.swiftbyte.gmc.Application;
import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.cache.CacheModel;
import de.swiftbyte.gmc.cache.GameServerCacheModel;
import de.swiftbyte.gmc.common.entity.GameType;
import de.swiftbyte.gmc.server.AseServer;
import de.swiftbyte.gmc.server.GameServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.lang.NonNull;
import org.springframework.shell.component.context.ComponentContext;
import org.springframework.shell.component.flow.ComponentFlow;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

@Slf4j
public class NodeUtils {

    @NonNull
    public static final String TMP_PATH = ConfigUtils.get("tmp-path", "./tmp/"),
            DAEMON_LATEST_DOWNLOAD_URL = "https://github.com/swiftbytegbr/gmc-daemon/releases/latest/download/" + getDaemonSetupName(),
            STEAM_CMD_DIR = ConfigUtils.get("steamcmd-install-dir", "./steamcmd/"),
            STEAM_CMD_PATH = STEAM_CMD_DIR + getSteamCmdExecutable(),
            STEAM_CMD_DOWNLOAD_URL = "https://steamcdn-a.akamaihd.net/client/installer/" + getSteamCmdArchiveName();

    public static Path getSteamCmdPath() {
        return Paths.get(STEAM_CMD_PATH);
    }

    public static Integer getValidatedToken(String token) {

        log.debug("Validating token '{}'...", token);

        String normalizedToken = token.replace("-", "");

        log.debug("Token was normalized to '{}'. Checking length...", normalizedToken);

        if (normalizedToken.length() != 6) {
            log.debug("Token was not expected size.");
            return null;
        }

        log.debug("Token was expected size. Checking if token is a valid integer...");

        try {

            return Integer.parseInt(normalizedToken);

        } catch (NumberFormatException ignore) {

            log.debug("Convert token to integer failed.");
            return null;

        }
    }

    public static ComponentContext<?> promptForInviteToken() {
        ComponentFlow flow = Application.getComponentFlowBuilder().clone().reset()
                .withStringInput("inviteToken")
                .name("Please enter the Invite Token. You can find the Invite Token in the create node window in the web panel:")
                .and().build();
        return flow.run().getContext();
    }

    public static void checkInstallation() {
        if (Files.exists(Paths.get(STEAM_CMD_PATH))) {
            log.debug("SteamCMD installation found.");
        } else {
            log.info("SteamCMD installation not found. Try to install...");
            installSteamCmd();
        }
    }

    private static void installSteamCmd() {
        log.debug("Downloading SteamCMD from " + STEAM_CMD_DOWNLOAD_URL + "...");

        File tmp = new File(TMP_PATH);
        try {
            String steamArchiveName = getSteamCmdArchiveName();
            FileUtils.copyURLToFile(
                    new URI(STEAM_CMD_DOWNLOAD_URL).toURL(),
                    new File(TMP_PATH + steamArchiveName));

            if (steamArchiveName.endsWith(".zip")) {
                ZipUtil.unpack(new File(TMP_PATH + steamArchiveName), new File(STEAM_CMD_DIR));
            } else if (steamArchiveName.endsWith(".tar.gz")) {
                if (OsUtils.OPERATING_SYSTEM != OsUtils.OperatingSystem.LINUX) {
                    throw new UnsupportedOperationException("Unsupported operating system for tar.gz archives: " + OsUtils.OPERATING_SYSTEM);
                }

                log.debug("Extracting .tar.gz archive using system utilities...");
                Path steamCmdPath = Path.of(STEAM_CMD_DIR);
                if (!Files.exists(steamCmdPath)) {
                    Files.createDirectories(steamCmdPath);
                }

                ProcessBuilder processBuilder = new ProcessBuilder("tar", "-xvzf", TMP_PATH + steamArchiveName, "-C", STEAM_CMD_DIR);
                processBuilder.redirectOutput(new File("./log/steamcmd-tar.log"));
                processBuilder.redirectError(new File("./log/steamcmd-tar-error.log"));
                int exitCode = processBuilder.start().waitFor();

                if (exitCode == 127) {
                    throw new UnsupportedOperationException("tar command not found. Please install tar and try again.");
                }

                if (exitCode != 0) {
                    throw new UnsupportedOperationException("An error occurred while extracting the SteamCMD archive. See log/steamcmd-tar-error.log for more information. Exit code: " + exitCode);
                }

                log.debug("Extracted .tar.gz archive successfully!");
            }

            FileUtils.deleteDirectory(tmp);
            log.info("SteamCMD successfully installed!");
        } catch (IOException e) {
            log.error("An error occurred while downloading SteamCMD. Please check your internet connection!", e);
            try {
                FileUtils.deleteDirectory(tmp);
            } catch (IOException ex) {
                log.warn("An error occurred while deleting the temporary directory.", ex);
            }
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            log.error("An operating system specific operation failed while trying to install SteamCMD.", e);
            try {
                FileUtils.deleteDirectory(tmp);
            } catch (IOException ex) {
                log.warn("An error occurred while deleting the temporary directory.", ex);
            }
            System.exit(1);
        } catch (InterruptedException e) {
            log.error("An error occurred while extracting the SteamCMD archive.", e);
            System.exit(1);
        } catch (URISyntaxException e) {
            // Let the daemon fail because there is no way we can recover from here!
            throw new RuntimeException(e);
        }
    }

    public static void downloadLatestDaemonInstaller() {
        File tmp = new File(TMP_PATH);

        String daemonSetupName = getDaemonSetupName();
        try {
            FileUtils.copyURLToFile(
                    new URI(DAEMON_LATEST_DOWNLOAD_URL).toURL(),
                    new File(TMP_PATH + daemonSetupName));

            log.debug("Update successfully downloaded!");
        } catch (IOException e) {
            log.error("An error occurred while downloading the update. Please check your internet connection!", e);
            try {
                FileUtils.deleteDirectory(tmp);
            } catch (IOException ex) {
                log.warn("An error occurred while deleting the temporary directory.", ex);
            }
        } catch (URISyntaxException e) {
            // Let the daemon fail because there is no way we can recover from here!
            throw new RuntimeException(e);
        }
    }

    public static void cacheInformation(Node node) {

        if (node.getConnectionState() == ConnectionState.DELETING) return;

        HashMap<String, GameServerCacheModel> gameServers = new HashMap<>();

        for (GameServer gameServer : GameServer.getAllServers()) {

            GameType gameType = GameType.ARK_ASCENDED;
            if (gameServer instanceof AseServer) gameType = GameType.ARK_EVOLVED;

            GameServerCacheModel gameServerCacheModel = GameServerCacheModel.builder()
                    .friendlyName(gameServer.getFriendlyName())
                    .gameType(gameType)
                    .settings(gameServer.getSettings())
                    .build();
            if (gameServer.getInstallDir() == null) {
                log.error("Install directory is null for game server '{}'. Skipping...", gameServer.getFriendlyName());
                continue;
            }
            gameServerCacheModel.setInstallDir(gameServer.getInstallDir().toString());
            gameServers.put(gameServer.getServerId(), gameServerCacheModel);
        }

        CacheModel cacheModel = CacheModel.builder()
                .nodeName(node.getNodeName())
                .teamName(node.getTeamName())
                .serverPath(node.getServerPath())
                .isAutoUpdateEnabled(node.isAutoUpdateEnabled())
                .autoBackup(node.getAutoBackup())
                .gameServerCacheModelHashMap(gameServers)
                .manageFirewallAutomatically(node.isManageFirewallAutomatically())
                .serverRestartMessage(node.getServerRestartMessage())
                .serverStopMessage(node.getServerStopMessage())
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        try {
            File file = new File("./cache.json");
            if (!file.exists()) file.createNewFile();
            writer.writeValue(file, cacheModel);
        } catch (IOException e) {
            log.error("An unknown error occurred while caching information.", e);
        }
    }

    public static String getDaemonSetupName() {
        switch (OsUtils.OPERATING_SYSTEM) {
            case LINUX -> {
                return "gmc-daemon-setup.sh";
            }

            case WINDOWS -> {
                return "gmc-daemon-setup.exe";
            }

            default -> throw new IllegalStateException("Unsupported operating system: " + OsUtils.OPERATING_SYSTEM);
        }
    }

    private static String getSteamCmdArchiveName() {
        switch (OsUtils.OPERATING_SYSTEM) {
            case LINUX -> {
                return "steamcmd_linux.tar.gz";
            }

            case WINDOWS -> {
                return "steamcmd.zip";
            }

            default ->
                    throw new IllegalStateException("Unsupported operating system: " + OsUtils.OPERATING_SYSTEM);
        }
    }

    private static String getSteamCmdExecutable() {
        switch (OsUtils.OPERATING_SYSTEM) {
            case LINUX -> {
                return "steamcmd.sh";
            }

            case WINDOWS -> {
                return "steamcmd.exe";
            }

            default ->
                throw new IllegalStateException("Unsupported operating system: " + OsUtils.OPERATING_SYSTEM);
        }
    }
}
