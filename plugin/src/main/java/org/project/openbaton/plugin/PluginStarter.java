package org.project.openbaton.plugin;

import org.project.openbaton.plugin.utils.StartupPlugin;

/**
 * Created by lto on 09/09/15.
 */
public class PluginStarter {

    public static void run(Class clazz, String name, String registryIp){
        try {
            StartupPlugin.register(clazz, name, registryIp);

//            StartupPlugin.unregister(name, registryIp);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }

    }
}
