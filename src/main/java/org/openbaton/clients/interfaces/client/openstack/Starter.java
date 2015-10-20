/*
 * Copyright (c) 2015 Fraunhofer FOKUS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openbaton.clients.interfaces.client.openstack;

import org.openbaton.plugin.PluginStarter;
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
            PluginStarter.run(OpenstackClient.class, args[0], args[1], Integer.parseInt(args[2]));
        else
            PluginStarter.run(OpenstackClient.class, "openstack-plugin", "localhost");
    }
}
