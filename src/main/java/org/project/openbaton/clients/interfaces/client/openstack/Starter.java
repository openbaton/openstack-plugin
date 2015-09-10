package org.project.openbaton.clients.interfaces.client.openstack;

import org.project.openbaton.plugin.PluginStarter;

/**
 * Created by lto on 10/09/15.
 */
public class Starter {
    public static void main(String[] args) {
        PluginStarter.run(OpenstackClient.class, "openstack-plugin", "localhost");
    }
}
