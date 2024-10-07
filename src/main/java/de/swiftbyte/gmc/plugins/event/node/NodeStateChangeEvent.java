package de.swiftbyte.gmc.plugins.event.node;

import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.plugins.event.GmcEvent;
import de.swiftbyte.gmc.utils.ConnectionState;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NodeStateChangeEvent implements GmcEvent {

    private final Node node;
    private final ConnectionState oldState;
    private final ConnectionState newState;

}
