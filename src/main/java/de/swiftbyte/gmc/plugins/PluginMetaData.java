package de.swiftbyte.gmc.plugins;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PluginMetaData {

    @NonNull
    public final String mainClass;

    @NonNull
    public final String name;

    @NonNull
    public final String version;

    public final String author;

    public final String description;

    public boolean validate() {
        return mainClass != null && name != null && version != null;
    }

    public static PluginMetaData empty() {
        return new PluginMetaData("", "dummy", "0.0.0", null, null);
    }

}
