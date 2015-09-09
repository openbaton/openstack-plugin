package org.project.openbaton.plugin.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * Created by lto on 09/09/15.
 */
public class StartupPlugin {
    protected static Logger log = LoggerFactory.getLogger(StartupPlugin.class);

    public static <T extends Remote> void register(Class<T> plugin, int port, String name) throws Exception {
        T stub = getStub(plugin);
        Registry registry = LocateRegistry.createRegistry(port);

        registry.rebind(name, stub);
        log.debug("Remote service bound");
    }
    public static <T extends Remote> void register(Class<T> plugin, String host, int port, String name) throws Exception {
        try {
            T stub = getStub(plugin);

            Registry registry = LocateRegistry.getRegistry(host, port);

            registry.rebind(name, stub);
            log.debug("Remote service bound");
        } catch (Exception e) {
            log.error("Remote service exception:");
            e.printStackTrace();
        }
    }

    private static <T extends Remote> T getStub(Class<T> plugin) throws InstantiationException, IllegalAccessException, java.lang.reflect.InvocationTargetException, NoSuchMethodException, RemoteException {
        T service = plugin.getConstructor().newInstance();
        return (T) UnicastRemoteObject.exportObject(service, 0);
    }
}
