package org.project.openbaton.plugin.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;

/**
 * Created by lto on 09/09/15.
 */
@Service
@Scope
public class StartupPlugin {
    protected static Logger log = LoggerFactory.getLogger(StartupPlugin.class);

    public static <T extends Remote> void register(Class<T> plugin) throws Exception {
        try {
            Properties properties = new Properties();
            properties.load(plugin.getResourceAsStream("/plugin.conf.properties"));

            T service = plugin.getConstructor().newInstance();
            T stub = (T) UnicastRemoteObject.exportObject(service, 0);
            Registry registry = LocateRegistry.createRegistry(Integer.parseInt(properties.getProperty("registry-port","19345")));

            registry.rebind(properties.getProperty("plugin-name","plugin"), stub);
            log.debug("Remote service bound");
        } catch (Exception e) {
            log.error("Remote service exception:");
            e.printStackTrace();
        }
    }
}
