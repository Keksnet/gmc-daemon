package de.swiftbyte.gmc.plugins;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public abstract class GmcPlugin {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    // This field gets populated during the initialization process.
    private final PluginMetaData metaData = PluginMetaData.empty();

    public abstract void onStartup();

    public abstract void onShutdown();

    public final Logger getLogger() {
        return this.logger;
    }

}
