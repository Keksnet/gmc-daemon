package de.swiftbyte.gmc.plugins.event.server;

import de.swiftbyte.gmc.common.packet.entity.GameServerState;
import de.swiftbyte.gmc.plugins.event.GmcEvent;
import de.swiftbyte.gmc.server.GameServer;
import lombok.Data;

@Data
public class ServerStateChangeEvent implements GmcEvent {

    private final GameServer server;
    private final GameServerState oldState;
    private final GameServerState newState;

    public ServerStateChangeEvent(GameServer server, GameServerState oldState, GameServerState newState) {
        this.server = server;
        this.oldState = oldState;
        this.newState = newState;
    }

}
