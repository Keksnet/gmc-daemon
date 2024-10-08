package de.swiftbyte.gmc.plugins.event.server;

import de.swiftbyte.gmc.plugins.event.GmcEvent;
import de.swiftbyte.gmc.server.GameServer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
public class ServerCrashLoopDetectedEvent implements GmcEvent {

    private final GameServer server;
    private final int restartCount;

    @Setter
    private boolean attemptNextRestart;

}
