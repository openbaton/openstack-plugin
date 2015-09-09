package org.project.openbaton.plugin;

import org.project.openbaton.plugin.utils.StartupPlugin;

import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

/**
 * Created by lto on 09/09/15.
 */
public class PluginStarter {

    public static void run(Class clazz, String name, String registryIp){
        try {
            StartupPlugin.register(clazz, name, registryIp);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }finally {
            try {
                StartupPlugin.unregister(name, registryIp);
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (NotBoundException e) {
                e.printStackTrace();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

    }
}
