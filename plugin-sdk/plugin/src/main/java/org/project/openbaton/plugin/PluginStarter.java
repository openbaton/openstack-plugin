package org.project.openbaton.plugin;

import org.project.openbaton.plugin.utils.StartupPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

/**
 * Created by lto on 09/09/15.
 */
public class PluginStarter {

//    static volatile boolean keepRunning = true;
protected static Logger log = LoggerFactory.getLogger(PluginStarter.class);

    public static void run(Class clazz, final String name, final String registryIp){
        try {
            log.info("Starting plugin with name: " + name);
            log.debug("Registry ip: " + registryIp);
            log.debug("Class to register: " + clazz.getName());
            StartupPlugin.register(clazz, name, registryIp);

//            StartupPlugin.unregister(name, registryIp);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }

        final Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    mainThread.join();
                    log.info("Unregistering: " + name);
                    StartupPlugin.unregister(name, registryIp);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (NotBoundException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void run(Class clazz, final String name, final String registryIp, int port){
        try {
            log.info("Starting plugin with name: " + name);
            log.debug("Registry ip: " + registryIp);
            log.debug("Class to register: " + clazz.getName());
            StartupPlugin.register(clazz, name, registryIp, port);

//            StartupPlugin.unregister(name, registryIp);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }

        final Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    mainThread.join();
                    log.info("Unregistering: " + name);
                    StartupPlugin.unregister(name, registryIp);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (NotBoundException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
