package de.swiftbyte.gmc.plugins.event.server;

import de.swiftbyte.gmc.common.packet.entity.GameServerState;
import de.swiftbyte.gmc.plugins.event.GmcEvent;
import de.swiftbyte.gmc.server.GameServer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
public class ServerStateChangeEvent implements GmcEvent {

    private final GameServer server;
    private final GameServerState oldState;

    @Setter
    private GameServerState newState;

}
