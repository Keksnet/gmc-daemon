package de.swiftbyte.gmc.plugins.event.server;

import de.swiftbyte.gmc.plugins.event.CancellableEvent;
import de.swiftbyte.gmc.server.GameServer;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ServerAbandonEvent extends CancellableEvent {

    private final GameServer server;

}
