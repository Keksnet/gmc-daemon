package de.swiftbyte.gmc.plugins.event;

import lombok.Getter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Getter
public class ListenerInfo {

    private final EventListener eventListener;
    private final Method method;
    private final int priority;

    public ListenerInfo(EventListener eventListener, Method method) {
        this.eventListener = eventListener;
        this.method = method;

        if (method.getParameterTypes().length == 0) {
            throw new IllegalArgumentException("Method must have at least one parameter");
        }

        if (!method.isAnnotationPresent(GmcEventHandler.class)) {
            throw new IllegalArgumentException("Method must be annotated with @GmcEventHandler");
        }

        this.priority = method.getAnnotation(GmcEventHandler.class).priority();
    }

    public void dispatchEvent(GmcEvent event) throws InvocationTargetException, IllegalAccessException {
        this.method.invoke(this.eventListener, event);
    }

}
