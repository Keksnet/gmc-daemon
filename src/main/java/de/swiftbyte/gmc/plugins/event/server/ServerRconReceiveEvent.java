package de.swiftbyte.gmc.plugins.event.server;

import de.swiftbyte.gmc.plugins.event.GmcEvent;
import de.swiftbyte.gmc.server.GameServer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
@AllArgsConstructor
public class ServerRconReceiveEvent implements GmcEvent {

    private final GameServer server;
    private final Duration requestDuration;

    @Setter
    private String message;

}
