package de.swiftbyte.gmc.plugins.event.websocket;

import de.swiftbyte.gmc.common.packet.Packet;
import de.swiftbyte.gmc.plugins.event.CancellableEvent;
import de.swiftbyte.gmc.plugins.event.GmcEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class WebsocketPacketSendEvent extends CancellableEvent {

    private String destination;
    private Packet packet;

}
