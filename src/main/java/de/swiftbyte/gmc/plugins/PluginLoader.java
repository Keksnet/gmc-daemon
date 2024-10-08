package de.swiftbyte.gmc.plugins;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Slf4j
public class PluginLoader {

    private static PluginLoader instance;
    private final List<GmcPlugin> loadedPlugins = new ArrayList<>();

    private PluginLoader() {
    }

    public static PluginLoader getInstance() {
        if (instance == null) {
            instance = new PluginLoader();
        }

        return instance;
    }

    public void executePluginShutdown() {
        for (GmcPlugin plugin : loadedPlugins) {
            try {
                plugin.onShutdown();
                log.info("Successfully shut down plugin {}", plugin.getClass().getName());
            } catch (Exception e) {
                log.error("Failed to execute plugin shutdown", e);
            }
        }
    }

    public <T extends GmcPlugin> T getPlugin(Class<T> pluginClass) {
        for (GmcPlugin plugin : loadedPlugins) {
            if (plugin.getClass().getName().equals(pluginClass.getName())) {
                return (T) plugin;
            }
        }

        throw new IllegalArgumentException("Plugin class " + pluginClass.getName() + " not found");
    }

    public List<GmcPlugin> getLoadedPlugins() {
        return new ArrayList<>(this.loadedPlugins);
    }

    public void loadPlugins() throws IOException {
        findPlugins().stream().map(path -> {
            log.info("Loading plugin {}", path);
            try {
                GmcPlugin plugin = loadPlugin(path);
                if (plugin == null) {
                    log.error("Failed to load plugin {}", path);
                    return null;
                }

                plugin.getLogger().info("Successfully loaded plugin {}", plugin.getMetaData().getName());
                return plugin;
            } catch (IOException e) {
                log.error("Failed to load plugin", e);
            }

            return null;
        }).filter(Objects::nonNull).forEach(plugin -> {
            plugin.getLogger().info("Initializing plugin...");
            try {
                plugin.onStartup();
                loadedPlugins.add(plugin);
                plugin.getLogger().info("Plugin was loaded successfully");
            } catch (Exception e) {
                log.error("Failed to load plugin", e);
            }
        });
    }

    private List<Path> findPlugins() throws IOException {
        Path pluginsDir = Paths.get("plugins");
        if (!Files.exists(pluginsDir)) {
            return List.of();
        }

        return Files.list(pluginsDir)
                .filter(Files::isRegularFile)
                .map(Path::toAbsolutePath)
                .toList();
    }

    private <T extends GmcPlugin> T loadPlugin(Path pluginPath) throws IOException {
        PluginMetaData metaData;
        try (JarFile jarFile = new JarFile(pluginPath.toFile())) {
            JarEntry entry = jarFile.getJarEntry("gmc-plugin.properties");

            if (entry == null) {
                log.error("Failed to load plugin: gmc-plugin.properties was not present!");
                return null;
            }

            try (InputStream is = jarFile.getInputStream(entry)) {
                metaData = getPluginConfig(is);
            }
        }

        Class<?> pluginClass = null;
        try {
            URLClassLoader pluginClassLoader = new URLClassLoader(new URL[]{pluginPath.toUri().toURL()}, ClassLoader.getSystemClassLoader());
            pluginClass = Class.forName(metaData.getMainClass(), true, pluginClassLoader);
        } catch (SecurityException e) {
            log.error("Security manager does not allow external plugin loading.", e);
        } catch (ClassNotFoundException e) {
            log.error("Plugin class not found.", e);
        }

        if (pluginClass == null) {
            log.error("Could not load main class {} from {}", metaData.getMainClass(), metaData.getName());
            return null;
        }

        try {
            if (GmcPlugin.class.isAssignableFrom(pluginClass)) {
                // Get a plugin instance
                Constructor<T> noArgsConstructor = (Constructor<T>) pluginClass.getDeclaredConstructor();
                T plugin = noArgsConstructor.newInstance();

                // Populate the metaData field
                Field metaDataField = GmcPlugin.class.getDeclaredField("metaData");
                boolean accessible = metaDataField.canAccess(plugin);
                metaDataField.setAccessible(true);
                metaDataField.set(plugin, metaData);
                metaDataField.setAccessible(accessible);

                return plugin;
            }

            log.warn("Could not load plugin {}. mainClass {} is not an instance of GmcPlugin.", pluginPath, metaData.getMainClass());
        } catch (NoSuchMethodException e) {
            log.error("Default plugin constructor not found.", e);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchFieldException e) {
            log.error("Failed to initialize the plugin using reflection.", e);
        }

        return null;
    }

    private PluginMetaData getPluginConfig(InputStream inputStream) throws IOException {
        Properties properties = new Properties();
        properties.load(inputStream);

        PluginMetaData config = new PluginMetaData(
                properties.getProperty("mainClass"),
                properties.getProperty("name"),
                properties.getProperty("version"),
                properties.getProperty("author"),
                properties.getProperty("description"));

        if (!config.validate()) {
            throw new IllegalArgumentException("Invalid plugin configuration");
        }

        return config;
    }

}
