package org.project.openbaton.plugin.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * Created by lto on 09/09/15.
 */
public class StartupPlugin {
    protected static Logger log = LoggerFactory.getLogger(StartupPlugin.class);

    private static <T extends Remote> Remote getStub(Class<T> plugin) throws InstantiationException, IllegalAccessException, java.lang.reflect.InvocationTargetException, NoSuchMethodException, RemoteException {
        Remote service = plugin.getConstructor().newInstance();
        return UnicastRemoteObject.exportObject(service,0);
    }

//    public static <T extends Remote> void register(Class<T> clazz, String name, String registryIp) throws InvocationTargetException, NoSuchMethodException, RemoteException, InstantiationException, IllegalAccessException, MalformedURLException {
//        Naming.rebind("//" + registryIp + ":1099/" + name, getStub(clazz));
//        log.debug("Remote service bound");
//    }

    public static <T extends Remote> void register(Class<T> clazz, String name, String registryIp, int port) throws InvocationTargetException, NoSuchMethodException, RemoteException, InstantiationException, IllegalAccessException, MalformedURLException {
        Naming.rebind("//" + registryIp + ":" + port + "/" + name, getStub(clazz));
        log.debug("Remote service bound");
    }

    public static void unregister(String name, String registryIp, int port) throws RemoteException, NotBoundException, MalformedURLException {
        Naming.unbind("//" + registryIp + ":" + port + "/" + name);
    }

    public static void unregister(String name, String registryIp) throws RemoteException, NotBoundException, MalformedURLException {
        Naming.unbind("//" + registryIp + ":1099/" + name);
    }
}
