package de.swiftbyte.gmc.plugins.event.server;

import de.swiftbyte.gmc.plugins.event.CancellableEvent;
import de.swiftbyte.gmc.server.GameServer;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ServerRestartEvent extends CancellableEvent {

    private final GameServer server;
    private final String reason;

    public ServerRestartEvent(GameServer server, String reason) {
        this.server = server;
        this.reason = reason;
    }

}
