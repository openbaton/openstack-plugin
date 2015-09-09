package org.project.openbaton.plugin;

import org.project.openbaton.plugin.utils.StartupPlugin;

/**
 * Created by lto on 09/09/15.
 */
public class PluginStarter {

    public static void run(Class clazz, String name, int port){
        try {
            StartupPlugin.register(clazz, port,name);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    public static void run(Class clazz, String name, String host, int port){
        try {
            StartupPlugin.register(clazz,host,port,name);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }
}
