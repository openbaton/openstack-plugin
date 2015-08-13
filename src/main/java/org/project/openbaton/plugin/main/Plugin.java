package org.project.openbaton.plugin.main;

import org.project.openbaton.catalogue.nfvo.EndpointType;
import org.project.openbaton.plugin.interfaces.PluginReceiver;
import org.project.openbaton.plugin.interfaces.PluginSender;
import org.project.openbaton.plugin.utils.AgentBroker;

/**
 * Created by lto on 13/08/15.
 */
public abstract class Plugin {

    private PluginSender pluginSender;
    private PluginReceiver pluginReceiver;



    protected void setup(EndpointType senderType, EndpointType receiverType){
        pluginSender = AgentBroker.getSender(senderType);
        pluginReceiver = AgentBroker.getReceiver(receiverType);
    }

    private Object onMethodInvoke()

}
