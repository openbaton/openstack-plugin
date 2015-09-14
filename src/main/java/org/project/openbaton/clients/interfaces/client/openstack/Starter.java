package org.project.openbaton.clients.interfaces.client.openstack;

import org.project.openbaton.plugin.PluginStarter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by lto on 10/09/15.
 */
public class Starter {

    private static Logger log = LoggerFactory.getLogger(PluginStarter.class);

    public static void main(String[] args) {
        log.info("params are: pluginName registryIp registryPort\ndefault is openstack-plugin localhost 1099");

        if (args.length > 1)
            PluginStarter.run(OpenstackClient.class, args[0] ,args[1], Integer.parseInt(args[2]));
        else
            PluginStarter.run(OpenstackClient.class, "openstack-plugin", "localhost");
    }
}
