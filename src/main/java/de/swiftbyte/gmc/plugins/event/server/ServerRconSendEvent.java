package de.swiftbyte.gmc.plugins.event.server;

import de.swiftbyte.gmc.plugins.event.CancellableEvent;
import de.swiftbyte.gmc.server.GameServer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
public class ServerRconSendEvent extends CancellableEvent {

    private final GameServer server;

    @Setter
    private String rconCommand;

}
