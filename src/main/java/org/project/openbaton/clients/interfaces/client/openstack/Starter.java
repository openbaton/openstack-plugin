package org.project.openbaton.clients.interfaces.client.openstack;

import org.project.openbaton.plugin.PluginStarter;
import java.io.IOException;
import java.util.Properties;

/**
 * Created by lto on 10/09/15.
 */
public class Starter {
	    public static void main(String[] args) {
		            Properties properties = new Properties();
			            try {
					                properties.load(Starter.class.getResourceAsStream("/plugin.conf.properties"));
							        } catch (IOException e) {
									            e.printStackTrace();
										                System.exit(1);
												        }
				            PluginStarter.run(OpenstackClient.class, "openstack-plugin", properties.getProperty("registry-ip", "localhost"));
					        }
}
