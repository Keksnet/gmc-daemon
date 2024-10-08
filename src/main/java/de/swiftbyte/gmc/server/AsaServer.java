package de.swiftbyte.gmc.server;

import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.common.packet.entity.GameServerState;
import de.swiftbyte.gmc.common.packet.entity.ServerSettings;
import de.swiftbyte.gmc.common.packet.server.ServerDeletePacket;
import de.swiftbyte.gmc.plugins.PluginManager;
import de.swiftbyte.gmc.plugins.event.server.*;
import de.swiftbyte.gmc.service.BackupService;
import de.swiftbyte.gmc.service.FirewallService;
import de.swiftbyte.gmc.stomp.StompHandler;
import de.swiftbyte.gmc.utils.CommonUtils;
import de.swiftbyte.gmc.utils.NodeUtils;
import de.swiftbyte.gmc.utils.ServerUtils;
import de.swiftbyte.gmc.utils.action.AsyncAction;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import xyz.astroark.Rcon;
import xyz.astroark.exception.AuthenticationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Scanner;

@Slf4j
public class AsaServer extends GameServer {

    private static final String STEAM_CMD_ID = "2430930";

    @Setter
    private int rconPort;
    @Setter
    private String rconPassword;

    public AsaServer(String id, String friendlyName, ServerSettings settings, boolean overrideAutoStart) {

        super(id, friendlyName, settings);

        rconPassword = settings.getRconPassword();
        rconPort = settings.getRconPort();

        if (!overrideAutoStart) {
            PID = CommonUtils.getProcessPID(String.valueOf(installDir));
            if (PID == null && settings.isStartOnBoot()) start().queue();
            else if (PID != null) super.setState(GameServerState.ONLINE);
        }
    }

    public AsaServer(String id, String friendlyName, Path installDir, ServerSettings settings, boolean overrideAutoStart) {

        super(id, friendlyName, settings);
        this.installDir = installDir;

        rconPassword = settings.getRconPassword();
        rconPort = settings.getRconPort();

        if (!overrideAutoStart) {
            PID = CommonUtils.getProcessPID(String.valueOf(installDir));
            if (PID == null && settings.isStartOnBoot()) start().queue();
            else if (PID != null) super.setState(GameServerState.ONLINE);
        }
    }

    @Override
    public AsyncAction<Boolean> install() {
        return () -> {
            super.setState(GameServerState.CREATING);

            ServerInstallEvent event = new ServerInstallEvent(this);
            PluginManager.getInstance().dispatchEvent(event);

            if (event.isCancelled()) {
                // TODO: Abort server install gracefully
                log.debug("Server install was aborted by plugin");
                super.setState(GameServerState.UNKNOWN);
                return false;
            }

            String installCommand = "cmd /c start \"steamcmd\" \"" + CommonUtils.convertPathSeparator(NodeUtils.getSteamCmdPath().toAbsolutePath()) + "\""
                    + " +force_install_dir \"" + CommonUtils.convertPathSeparator(installDir.toAbsolutePath()) + "\""
                    + " +login anonymous +app_update " + STEAM_CMD_ID + " validate +quit";
            log.debug("Starting server installation with command " + installCommand);
            try {
                Process process = Runtime.getRuntime().exec(installCommand);

                Scanner scanner = new Scanner(process.getInputStream());

                while (scanner.hasNextLine()) {
                    scanner.nextLine();
                }

                if (process.exitValue() == 7 || process.exitValue() == 0) {
                    log.debug("Server was installed successfully!");

                    allowFirewallPorts();

                    super.setState(GameServerState.OFFLINE);
                } else {
                    log.error("Server installation returned error code " + process.exitValue() + ".");
                    return false;
                }

            } catch (IOException e) {
                log.error("An unknown exception occurred while installing the server '" + friendlyName + "'.", e);
                return false;
            }
            return true;
        };
    }

    @Override
    public AsyncAction<Boolean> delete() {
        return () -> {
            try {
                if (state != GameServerState.OFFLINE && state != GameServerState.CREATING) {
                    stop(false).complete();
                }
                super.setState(GameServerState.DELETING);
                Thread.sleep(5000);
                FileUtils.deleteDirectory(installDir.toFile());
                FirewallService.removePort(friendlyName);
                BackupService.deleteAllBackupsByServer(this);
                GameServer.removeServerById(serverId);
                updateScheduler.cancel(false);
                NodeUtils.cacheInformation(Node.INSTANCE);

                ServerDeletePacket packet = new ServerDeletePacket();
                packet.setServerId(serverId);
                StompHandler.send("/app/server/delete", packet);

            } catch (IOException e) {
                log.error("An unknown exception occurred while deleting the server '" + friendlyName + "'.", e);
                return false;
            } catch (InterruptedException e) {
                log.error("An unknown exception occurred while deleting the server '" + friendlyName + "'.", e);
            }
            return true;
        };
    }

    @Override
    public AsyncAction<Boolean> abandon() {
        return () -> {
            ServerAbandonEvent e = new ServerAbandonEvent(this);
            PluginManager.getInstance().dispatchEvent(e);

            if (e.isCancelled()) {
                log.debug("Abandoning server {} was aborted by plugin", this.serverId);
                return false;
            }

            GameServer.removeServerById(serverId);
            updateScheduler.cancel(false);
            NodeUtils.cacheInformation(Node.INSTANCE);
            return true;
        };
    }

    @Override
    public AsyncAction<Boolean> start() {
        return () -> {
            ServerUtils.killServerProcess(PID);

            super.setState(GameServerState.INITIALIZING);

            ServerStartupEvent event = new ServerStartupEvent(this);
            PluginManager.getInstance().dispatchEvent(event);
            if (event.isCancelled()) {
                log.debug("Server startup was aborted by plugin");
                super.setState(GameServerState.OFFLINE);
                return false;
            }

            if (!Files.exists(installDir)) {
                super.setState(GameServerState.OFFLINE);
                install().queue();
                return false;
            }

            new Thread(() -> {
                ServerUtils.writeAsaStartupBatch(this);
                try {
                    log.debug("cmd /c start \"" + CommonUtils.convertPathSeparator(installDir + "/start.bat\""));
                    serverProcess = Runtime.getRuntime().exec("cmd /c start /min \"" + "\" \"" + CommonUtils.convertPathSeparator(installDir + "/start.bat\""));
                    Scanner scanner = new Scanner(serverProcess.getInputStream());
                    while (scanner.hasNextLine()) {
                    }

                    boolean serverCrashed = (state != GameServerState.OFFLINE && state != GameServerState.STOPPING);

                    ServerCrashEvent e = new ServerCrashEvent(this, 0, serverCrashed && settings.isRestartOnCrash());
                    if (serverCrashed) {
                        PluginManager.getInstance().dispatchEvent(e);
                    }

                    if (e.isAttemptNextRestart()) {
                        super.setState(GameServerState.RESTARTING);
                    } else {
                        super.setState(GameServerState.OFFLINE);
                    }

                } catch (IOException e) {
                    log.error("An unknown exception occurred while starting the server '" + friendlyName + "'.", e);
                }
            }).start();

            return true;
        };
    }

    @Override
    public AsyncAction<Boolean> stop(boolean isRestart) {
        return () -> {
            if (state == GameServerState.OFFLINE) return true;
            super.setState(GameServerState.STOPPING);

            if (!isRestart) {
                if (!CommonUtils.isNullOrEmpty(Node.INSTANCE.getServerStopMessage())) {
                    sendRconCommand("serverchat " + Node.INSTANCE.getServerStopMessage());
                } else {
                    sendRconCommand("serverchat server ist stopping...");
                    log.debug("Sending stop message to server '" + friendlyName + "'...");
                }
            }

            try {
                Thread.sleep(7000);
                sendRconCommand("serverchat 3");
                Thread.sleep(1000);
                sendRconCommand("serverchat 2");
                Thread.sleep(1000);
                sendRconCommand("serverchat 1");
                Thread.sleep(1000);
                sendRconCommand("serverchat STOP");
            } catch (InterruptedException e) {
                log.warn("Failed to send stop message to server '" + friendlyName + "'.");
            }
            if (sendRconCommand("saveworld") == null) {
                log.debug("No connection to server '" + friendlyName + "'. Killing process...");
                ServerUtils.killServerProcess(PID);
            } else {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ignored) {
                    ServerUtils.killServerProcess(PID);
                }
                sendRconCommand("doexit");
            }

            synchronized (this) {
                while (state != GameServerState.OFFLINE) {
                    try {
                        this.wait(1000);
                    } catch (InterruptedException e) {
                        log.error("An unknown exception occurred while stopping the server '" + friendlyName + "'.", e);
                    }
                }
                log.debug("Server '" + friendlyName + "' is offline.");
                return true;
            }
        };
    }

    private int restartCounter = 0;

    @Override
    public void update() {
        if (PID == null) PID = CommonUtils.getProcessPID(String.valueOf(installDir));

        switch (state) {
            case INITIALIZING -> {
                if (sendRconCommand("ping") == null) {
                    log.debug("Server '" + friendlyName + "' is still initializing...");
                } else {
                    log.debug("Server '" + friendlyName + "' is ready!");
                    super.setState(GameServerState.ONLINE);
                }
            }
            case ONLINE -> {
                restartCounter = 0;
                String listPlayersResponse = sendRconCommand("listplayers");

                if (listPlayersResponse == null) {

                    log.warn("Server crash detected! Restarting server...");

                    ServerUtils.killServerProcess(PID);

                    ServerCrashEvent e = new ServerCrashEvent(this, restartCounter, settings.isRestartOnCrash());
                    PluginManager.getInstance().dispatchEvent(e);

                    if (e.isAttemptNextRestart()) {
                        log.debug("Restarting server '" + friendlyName + "'...");
                        super.setState(GameServerState.RESTARTING);
                    } else {
                        super.setState(GameServerState.OFFLINE);
                    }
                } else {
                    String[] listPlayersResponseArray = listPlayersResponse.split("\n");
                    currentOnlinePlayers = listPlayersResponseArray.length - 2;
                }
            }
            case RESTARTING -> {
                boolean attemptNextRestart = restartCounter < 3;
                if (PluginManager.PLUGIN_SYSTEM_ENABLED && restartCounter > 0) {
                    // Only if the server did not start properly previously
                    ServerCrashLoopDetectedEvent e = new ServerCrashLoopDetectedEvent(this, restartCounter, attemptNextRestart);
                    PluginManager.getInstance().dispatchEvent(e);

                    // Write the modified value back
                    // This allows for custom crash loop code to be executed
                    attemptNextRestart = e.isAttemptNextRestart();
                }

                if (attemptNextRestart) {
                    log.error("Server '" + friendlyName + "' crashed 3 times in a row. Restarting is aborted!");
                    super.setState(GameServerState.OFFLINE);
                    return;
                }

                restartCounter++;
                log.debug("Server '" + friendlyName + "' is restarting...");
                new Thread(() -> {
                    ServerUtils.killServerProcess(PID);
                    start().complete();
                }).start();
            }
            case STOPPING -> {
                if (CommonUtils.getProcessPID(String.valueOf(installDir)) == null)
                    super.setState(GameServerState.OFFLINE);
            }
            case OFFLINE -> restartCounter = 0;
        }

        if(state != GameServerState.ONLINE) {
            currentOnlinePlayers = 0;
        }
    }

    @Override
    public String sendRconCommand(String command) {
        ServerRconSendEvent sendEvent = new ServerRconSendEvent(this, command);
        PluginManager.getInstance().dispatchEvent(sendEvent);

        if (sendEvent.isCancelled()) {
            log.debug("Sending rcon command {} was aborted by plugin", command);
            return null;
        }

        try {
            if (rconPort == 0 || CommonUtils.isNullOrEmpty(rconPassword)) return null;
            long startTimestamp = System.currentTimeMillis();

            Rcon rcon = new Rcon("127.0.0.1", rconPort, rconPassword.getBytes());
            String rconResponse = rcon.command(command);

            long endTimestamp = System.currentTimeMillis();
            ServerRconReceiveEvent receiveEvent = new ServerRconReceiveEvent(this, Duration.ofMillis(endTimestamp - startTimestamp), rconResponse);
            PluginManager.getInstance().dispatchEvent(receiveEvent);

            return receiveEvent.getMessage();
        } catch (IOException e) {
            log.debug("Server '" + friendlyName + "' is offline.");
        } catch (AuthenticationException e) {
            log.error("Rcon authentication failed for server '" + friendlyName + "'.");
        }

        return null;
    }
}
