package org.project.openbaton.plugin.utils;

import org.project.openbaton.catalogue.nfvo.EndpointType;
import org.project.openbaton.plugin.interfaces.agents.PluginReceiver;
import org.project.openbaton.plugin.interfaces.agents.PluginSender;

/**
 * Created by lto on 13/08/15.
 */
public class AgentBroker {
    public static PluginSender getSender(EndpointType senderType) {
        return null;
    }

    public static PluginReceiver getReceiver(EndpointType receiverType) {
        return null;
    }
}
