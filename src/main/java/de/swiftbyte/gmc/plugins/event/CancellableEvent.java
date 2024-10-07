package de.swiftbyte.gmc.plugins.event;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class CancellableEvent implements GmcEvent {

    private boolean cancelled = false;

}
