package de.swiftbyte.gmc.server;

import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.common.entity.GameServerState;
import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.service.FirewallService;
import de.swiftbyte.gmc.utils.CommonUtils;
import de.swiftbyte.gmc.utils.OsUtils;
import de.swiftbyte.gmc.utils.ServerUtils;
import de.swiftbyte.gmc.utils.SettingProfileUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public class AsaServer extends ArkServer {

    private static final String STEAM_CMD_ID = "2430930";

    @Override
    public String getGameId() {
        return STEAM_CMD_ID;
    }

    public AsaServer(String id, String friendlyName, SettingProfile settings, boolean overrideAutoStart) {

        super(id, friendlyName, settings);

        SettingProfileUtils settingProfileUtils = new SettingProfileUtils(settings.getGameUserSettings());

        rconPassword = settingProfileUtils.getSetting("ServerSettings", "ServerAdminPassword", "gmc-rp-" + UUID.randomUUID());
        rconPort = settingProfileUtils.getSettingAsInt("ServerSettings", "RconPort", 27020);

        if (!overrideAutoStart) {
            PID = CommonUtils.getProcessPID(installDir + CommonUtils.convertPathSeparator("/ShooterGame/Binaries/Win64/"));
            if (PID == null && settings.getGmcSettings().isStartOnBoot()) start().queue();
            else if (PID != null) {
                log.debug("Server '{}' with PID {} is already running. Setting state to ONLINE.", PID, friendlyName);
                super.setState(GameServerState.ONLINE);
            }
        }
    }

    public AsaServer(String id, String friendlyName, Path installDir, SettingProfile settings, boolean overrideAutoStart) {

        super(id, friendlyName, settings);

        if (installDir != null) this.installDir = installDir;

        SettingProfileUtils settingProfileUtils = new SettingProfileUtils(settings.getGameUserSettings());

        rconPassword = settingProfileUtils.getSetting("ServerSettings", "ServerAdminPassword", "gmc-rp-" + UUID.randomUUID());
        rconPort = settingProfileUtils.getSettingAsInt("ServerSettings", "RconPort", 27020);

        if (!overrideAutoStart) {
            PID = CommonUtils.getProcessPID(this.installDir + CommonUtils.convertPathSeparator("/ShooterGame/Binaries/Win64/"));
            if (PID == null && settings.getGmcSettings().isStartOnBoot()) start().queue();
            else if (PID != null) {
                log.debug("Server '{}' with PID {} is already running. Setting state to ONLINE.", PID, friendlyName);
                super.setState(GameServerState.ONLINE);
            }
        }
    }

    @Override
    public List<Integer> getNeededPorts() {

        SettingProfileUtils spu = new SettingProfileUtils(settings.getGameUserSettings());

        int gamePort = spu.getSettingAsInt("SessionSettings", "Port", 7777);
        int rconPort = spu.getSettingAsInt("ServerSettings", "RCONPort", 27020);

        return List.of(gamePort, gamePort + 1, rconPort);
    }

    @Override
    public void allowFirewallPorts() {
        if (Node.INSTANCE.isManageFirewallAutomatically()) {
            log.debug("Adding firewall rules for server '{}'...", friendlyName);
            Path executablePath = Path.of(installDir + "/ShooterGame/Binaries/Win64/ArkAscendedServer.exe");
            FirewallService.allowPort(friendlyName, executablePath, getNeededPorts());
        }
    }

    @Override
    public void writeStartupScript() {
        switch (OsUtils.OPERATING_SYSTEM) {
            case WINDOWS -> writeStartupBatch();
            case LINUX -> writeStartupShellScript();
            default -> throw new UnsupportedOperationException("Unsupported operating system: " + OsUtils.OPERATING_SYSTEM);
        }
    }

    @SuppressWarnings("DuplicatedCode")
    private void writeStartupBatch() {
        SettingProfile settings = getSettings();

        if (CommonUtils.isNullOrEmpty(settings.getGmcSettings().getMap())) {
            log.error("Map is not set for server '{}'. Falling back to default map.", getFriendlyName());
            settings.getGmcSettings().setMap("TheIsland_WP");
        }

        SettingProfileUtils spu = new SettingProfileUtils(settings.getGameUserSettings());

        setRconPort(spu.getSettingAsInt("ServerSettings", "RCONPort", 27020));
        setRconPassword(spu.getSetting("ServerSettings", "ServerAdminPassword", "gmc-rp-" + UUID.randomUUID()));

        List<String> requiredLaunchParameters1 = getRequiredLaunchArgs1(settings.getGmcSettings().getMap());
        List<String> requiredLaunchParameters2 = getRequiredLaunchArgs2();

        String realStartPostArguments = ServerUtils.generateServerArgs(
                settings.getQuestionMarkParams() == null || settings.getQuestionMarkParams().isEmpty() ? new ArrayList<>() : ServerUtils.generateArgListFromMap(settings.getQuestionMarkParams()),
                settings.getHyphenParams() == null || settings.getQuestionMarkParams().isEmpty() ? new ArrayList<>() : ServerUtils.generateArgListFromMap(settings.getHyphenParams()),
                requiredLaunchParameters1,
                requiredLaunchParameters2
        );

        String changeDirectoryCommand = "cd /d \"" + CommonUtils.convertPathSeparator(getInstallDir()) + "\\ShooterGame\\Binaries\\Win64\"";

        String serverExeName = "ArkAscendedServer.exe";

        if (Files.exists(Path.of(getInstallDir() + "/ShooterGame/Binaries/Win64/AsaApiLoader.exe")))
            serverExeName = "AsaApiLoader.exe";

        String startCommand = "start \"" + getFriendlyName() + "\""
                + " \"" + CommonUtils.convertPathSeparator(getInstallDir() + "/ShooterGame/Binaries/Win64/" + serverExeName) + "\""
                + " " + realStartPostArguments;
        log.debug("Writing startup batch for server {} with command '{}'", getFriendlyName(), startCommand);

        try {
            FileWriter fileWriter = new FileWriter(getInstallDir() + "/start.bat");
            PrintWriter printWriter = new PrintWriter(fileWriter);

            printWriter.println(changeDirectoryCommand);
            printWriter.println(startCommand);
            printWriter.println("exit");
            printWriter.close();

        } catch (IOException e) {
            log.error("An unknown exception occurred while writing the startup batch for server '{}'.", getFriendlyName(), e);
        }
    }

    @SuppressWarnings("DuplicatedCode")
    private void writeStartupShellScript() {
        SettingProfile settings = getSettings();

        if (CommonUtils.isNullOrEmpty(settings.getGmcSettings().getMap())) {
            log.error("Map is not set for server '{}'. Falling back to default map.", getFriendlyName());
            settings.getGmcSettings().setMap("TheIsland_WP");
        }

        SettingProfileUtils spu = new SettingProfileUtils(settings.getGameUserSettings());

        setRconPort(spu.getSettingAsInt("ServerSettings", "RCONPort", 27020));
        setRconPassword(spu.getSetting("ServerSettings", "ServerAdminPassword", "gmc-rp-" + UUID.randomUUID()));

        List<String> requiredLaunchParameters1 = getRequiredLaunchArgs1(settings.getGmcSettings().getMap());
        List<String> requiredLaunchParameters2 = getRequiredLaunchArgs2();

        String realStartPostArguments = ServerUtils.generateServerArgs(
                settings.getQuestionMarkParams() == null || settings.getQuestionMarkParams().isEmpty() ? new ArrayList<>() : ServerUtils.generateArgListFromMap(settings.getQuestionMarkParams()),
                settings.getHyphenParams() == null || settings.getQuestionMarkParams().isEmpty() ? new ArrayList<>() : ServerUtils.generateArgListFromMap(settings.getHyphenParams()),
                requiredLaunchParameters1,
                requiredLaunchParameters2
        );

        String changeDirectoryCommand = "cd \"" + CommonUtils.convertPathSeparator(getInstallDir()) + "\\ShooterGame\\Binaries\\Win64\"";

        String serverExeName = "ArkAscendedServer.exe";

        if (Files.exists(Path.of(getInstallDir() + "/ShooterGame/Binaries/Win64/AsaApiLoader.exe")))
            serverExeName = "AsaApiLoader.exe";

        String startCommand = "wine \""
                + CommonUtils.convertPathSeparator(getInstallDir() + "/ShooterGame/Binaries/Win64/" + serverExeName) + "\" \\\n"
                + realStartPostArguments;
        log.debug("Writing startup batch for server {} with command '{}'", getFriendlyName(), startCommand);

        if (System.getenv("NO_UNSTABLE_LINUX_WARNING") == null) {
            log.warn("###################################");
            log.warn("# ASA COMES WITHOUT LINUX SUPPORT #");
            log.warn("###################################");
            log.warn("Please note that ASA does not support Linux and may not work as expected.");
            log.warn("If you encounter any issues, please report them to the developers.");
            log.warn("ASA will be started using Wine, if you don't have wine please edit the start script.");
        }

        try {
            FileWriter fileWriter = new FileWriter(getInstallDir() + "/start.sh");
            PrintWriter printWriter = new PrintWriter(fileWriter);

            printWriter.println(changeDirectoryCommand);
            printWriter.println(startCommand);
            printWriter.println("exit");
            printWriter.close();

        } catch (IOException e) {
            log.error("An unknown exception occurred while writing the startup shell script for server '{}'.", getFriendlyName(), e);
        }
    }

    private static List<String> getRequiredLaunchArgs1(String map) {
        return new ArrayList<>(List.of(
                map,
                "RCONEnabled=True"
        ));
    }

    private static List<String> getRequiredLaunchArgs2() {
        return new ArrayList<>(List.of(
                "oldconsole"
        ));
    }
}
