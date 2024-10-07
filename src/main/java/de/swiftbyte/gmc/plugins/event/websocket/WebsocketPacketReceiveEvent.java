package de.swiftbyte.gmc.plugins.event.websocket;

import de.swiftbyte.gmc.common.packet.Packet;
import de.swiftbyte.gmc.plugins.event.GmcEvent;
import lombok.Data;

@Data
public class WebsocketPacketReceiveEvent implements GmcEvent {

    private Packet websocketPacket;

    public WebsocketPacketReceiveEvent(Packet websocketPacket) {
        this.websocketPacket = websocketPacket;
    }

}
