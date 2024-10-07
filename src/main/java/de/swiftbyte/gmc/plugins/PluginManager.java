package de.swiftbyte.gmc.plugins;

import de.swiftbyte.gmc.plugins.event.EventListener;
import de.swiftbyte.gmc.plugins.event.GmcEvent;
import de.swiftbyte.gmc.plugins.event.GmcEventHandler;
import de.swiftbyte.gmc.plugins.event.ListenerInfo;
import de.swiftbyte.gmc.utils.ConfigUtils;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

@Slf4j
public class PluginManager {

    private static PluginManager instance;

    public static PluginManager getInstance() {
        if (instance == null) {
            instance = new PluginManager();
        }

        return instance;
    }

    public final static boolean PLUGIN_SYSTEM_ENABLED =
            Boolean.parseBoolean(ConfigUtils.get("experimental.plugins.enabled", "false"));

    // Fully qualified class name -> list of registered event listeners
    private final Map<String, List<ListenerInfo>> eventListeners = new HashMap<>();

    private PluginManager() {
    }

    public void registerEventListener(EventListener listener) {
        Arrays.stream(listener.getClass().getDeclaredMethods())
                .filter(Objects::nonNull)
                .filter(m -> m.canAccess(listener))
                .filter(m -> m.isAnnotationPresent(GmcEventHandler.class))
                .filter(m -> m.getParameterCount() == 1)
                .filter(m -> GmcEvent.class.isAssignableFrom(m.getParameterTypes()[0]))
                .map(m -> new ListenerInfo(listener, m))
                .forEach(i -> {
                    String eventIdentifier = i.getMethod().getParameterTypes()[0].getName();
                    List<ListenerInfo> listenerInfos = eventListeners.get(eventIdentifier);
                    if (listenerInfos == null || listenerInfos.isEmpty()) {
                        listenerInfos = new ArrayList<>();
                    }

                    listenerInfos.add(i);

                    eventListeners.put(eventIdentifier, listenerInfos);
                });
    }

    public void unregisterEventListener(EventListener listener) {
        eventListeners.keySet().forEach(k -> {
            List<ListenerInfo> listeners = eventListeners.get(k);
            if (listeners == null || listeners.isEmpty()) {
                return;
            }

            listeners = listeners
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(i -> !i.getEventListener().equals(listener))
                    .toList();

            eventListeners.put(k, listeners);
        });
    }

    public <T extends GmcEvent> void dispatchEvent(T event) {
        List<ListenerInfo> listeners = eventListeners.get(event.getClass().getName());
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        listeners
                .stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(ListenerInfo::getPriority))
                .forEach(listener -> {
            try {
                listener.dispatchEvent(event);
            } catch (InvocationTargetException | IllegalAccessException e) {
                log.error("Could not invoke event handler {} in {}", listener.getMethod().getName(), listener.getEventListener().getClass().getName());
            }
        });
    }

}