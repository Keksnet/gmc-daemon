package de.swiftbyte.gmc.plugins.event.node;

import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.common.packet.node.NodeHeartbeatPacket;
import de.swiftbyte.gmc.plugins.event.GmcEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NodeHeartbeatEvent implements GmcEvent {

    private final Node node;
    private final NodeHeartbeatPacket heartbeatPacket;

}
