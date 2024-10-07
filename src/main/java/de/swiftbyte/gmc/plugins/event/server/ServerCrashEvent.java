package de.swiftbyte.gmc.plugins.event.server;

import de.swiftbyte.gmc.plugins.event.GmcEvent;
import de.swiftbyte.gmc.server.GameServer;
import lombok.Data;

@Data
public class ServerCrashEvent implements GmcEvent {

    private final GameServer server;

    public ServerCrashEvent(GameServer server) {
        this.server = server;
    }

}
