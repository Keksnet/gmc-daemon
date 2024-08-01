package de.swiftbyte.gmc.utils;

import org.springframework.lang.NonNull;

public class OsUtils {

    @NonNull
    public final static OperatingSystem OPERATING_SYSTEM = getOperatingSystem();

    public enum OperatingSystem {
        WINDOWS,
        MAC,
        LINUX
    }

    public static boolean hasForcedOs() {
        return System.getenv("GMC_FORCE_OS") != null;
    }

    @NonNull
    public static String getOsName() {
        String forceOs = System.getenv("GMC_FORCE_OS");
        if (forceOs != null) {
            return forceOs;
        }

        return System.getProperty("os.name");
    }

    @NonNull
    private static OperatingSystem getOperatingSystem() {
        if (getOsName().contains("Windows")) {
            return OperatingSystem.WINDOWS;
        } else if (getOsName().contains("Mac")) {
            return OperatingSystem.MAC;
        } else if (getOsName().contains("Linux")) {
            return OperatingSystem.LINUX;
        }

        // Default to Windows
        return OperatingSystem.WINDOWS;
    }

}
