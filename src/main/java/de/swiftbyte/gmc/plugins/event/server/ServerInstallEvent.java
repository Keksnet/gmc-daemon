package de.swiftbyte.gmc.plugins.event.server;

import de.swiftbyte.gmc.plugins.event.CancellableEvent;
import de.swiftbyte.gmc.server.GameServer;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ServerInstallEvent extends CancellableEvent {

    private final GameServer server;

    public ServerInstallEvent(GameServer server) {
        this.server = server;
    }

}
