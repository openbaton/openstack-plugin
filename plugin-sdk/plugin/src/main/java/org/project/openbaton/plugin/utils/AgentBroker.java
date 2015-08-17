package org.project.openbaton.plugin.utils;

import org.project.openbaton.catalogue.nfvo.EndpointType;
import org.project.openbaton.plugin.interfaces.agents.PluginSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * Created by lto on 13/08/15.
 */
@Service
@Scope
public class AgentBroker {

    @Autowired
    private ConfigurableApplicationContext context;

    public PluginSender getSender(EndpointType senderType) {
        return (PluginSender) context.getBean(senderType.toString().toLowerCase() + "PluginSender");
    }
}
