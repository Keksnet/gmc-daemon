package de.swiftbyte.gmc.plugins;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;

@Slf4j
public class PluginLoader {

    private static PluginLoader instance;

    public static PluginLoader getInstance() {
        if (instance == null) {
            instance = new PluginLoader();
        }

        return instance;
    }

    private PluginLoader() {}

    private final List<GmcPlugin> loadedPlugins = new ArrayList<>();

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

                plugin.getLogger().info("Successfully loaded plugin {}", path);
                return plugin;
            } catch (MalformedURLException e) {
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

    private <T extends GmcPlugin> T loadPlugin(Path pluginPath) throws MalformedURLException {
        try (URLClassLoader pluginClassLoader = new URLClassLoader(new URL[]{ pluginPath.toUri().toURL() }, ClassLoader.getSystemClassLoader())) {
            InputStream inputStream = pluginClassLoader.getResourceAsStream("gmc-plugin.properties");
            if (inputStream == null) {
                log.warn("Could not load plugin {}. gmc-plugin.properties could not be read.", pluginPath);
                return null;
            }

            PluginMetaData config = getPluginConfig(inputStream);
            inputStream.close();

            Class<?> pluginClass = Class.forName(config.getMainClass(), true, pluginClassLoader);

            Arrays.stream(config.getDEBUG_loadClasses()).forEach(className -> {
                try {
                    Class<?> clazz = Class.forName(className, true, pluginClassLoader);
                    log.info("Successfully loaded class {}", className);
                } catch (ClassNotFoundException e) {
                    log.error("Failed to load class {}", className, e);
                }
            });

            log.info("Classloader: {} <=> {}", pluginClassLoader, Thread.currentThread().getContextClassLoader());

            if (GmcPlugin.class.isAssignableFrom(pluginClass)) {
                // Get a plugin instance
                Constructor<T> noArgsConstructor = (Constructor<T>) pluginClass.getDeclaredConstructor();
                T plugin = noArgsConstructor.newInstance();

                // Populate the metaData field
                Field metaDataField = GmcPlugin.class.getDeclaredField("metaData");
                boolean accessible = metaDataField.canAccess(plugin);
                metaDataField.setAccessible(true);
                metaDataField.set(plugin, config);
                metaDataField.setAccessible(accessible);

                return plugin;
            }

            log.warn("Could not load plugin {}. mainClass {} is not an instance of GmcPlugin.", pluginPath, config.getMainClass());
        } catch (SecurityException e) {
            log.error("Security manager does not allow external plugin loading.", e);
        } catch (ClassNotFoundException e) {
            log.error("Plugin class not found.", e);
        } catch (NoSuchMethodException e) {
            log.error("Default plugin constructor not found.", e);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchFieldException e) {
            log.error("Failed to initialize the plugin using reflection.", e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    private PluginMetaData getPluginConfig(InputStream inputStream) throws IOException {
        Properties properties = new Properties();
        properties.load(inputStream);

        String loadClasses = properties.getProperty("x-debug.loadClasses");
        String[] DEBUG_loadClasses = new String[0];
        if (loadClasses != null) {
            DEBUG_loadClasses = loadClasses.split(",");
        }

        PluginMetaData config = new PluginMetaData(
                properties.getProperty("mainClass"),
                properties.getProperty("name"),
                properties.getProperty("version"),
                properties.getProperty("author"),
                properties.getProperty("description"),
                DEBUG_loadClasses);

        if (!config.validate()) {
            throw new IllegalArgumentException("Invalid plugin configuration");
        }

        return config;
    }

}
