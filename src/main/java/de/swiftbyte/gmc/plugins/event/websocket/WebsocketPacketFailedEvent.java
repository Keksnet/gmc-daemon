package de.swiftbyte.gmc.plugins.event.websocket;

import de.swiftbyte.gmc.common.packet.Packet;
import de.swiftbyte.gmc.plugins.event.GmcEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class WebsocketPacketFailedEvent implements GmcEvent {

    private String destination;
    private Packet packet;

}
