package de.swiftbyte.gmc.plugins.event.websocket;

import de.swiftbyte.gmc.common.packet.Packet;
import de.swiftbyte.gmc.plugins.event.GmcEvent;
import lombok.Data;

@Data
public class WebsocketPacketSentEvent implements GmcEvent {

    private Packet packet;

    public WebsocketPacketSentEvent(Packet packet) {
        this.packet = packet;
    }
}
