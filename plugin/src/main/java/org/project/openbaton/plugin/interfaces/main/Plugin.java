package org.project.openbaton.plugin.interfaces.main;

import org.project.openbaton.catalogue.nfvo.EndpointType;
import org.project.openbaton.catalogue.nfvo.PluginAnswer;
import org.project.openbaton.catalogue.nfvo.PluginEndpoint;
import org.project.openbaton.catalogue.nfvo.PluginMessage;
import org.project.openbaton.plugin.exceptions.PluginException;
import org.project.openbaton.plugin.interfaces.agents.PluginReceiver;
import org.project.openbaton.plugin.interfaces.agents.PluginSender;
import org.project.openbaton.plugin.utils.AgentBroker;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

/**
 * Created by lto on 13/08/15.
 */
public abstract class Plugin {

    private PluginSender pluginSender;
    private PluginReceiver pluginReceiver;

    protected static String pluginEndpoint;
    protected static String concurrency;

    protected Object pluginInstance;

    private EndpointType senderType;
    private EndpointType receiverType;

    protected PluginEndpoint endpoint;
    protected String type;

    protected void loadProperties() throws IOException {
        Properties properties = new Properties();
        properties.load(pluginInstance.getClass().getResourceAsStream("plguin.conf.properties"));
        this.senderType = EndpointType.valueOf(properties.getProperty("sender-type"));
        this.receiverType = EndpointType.valueOf(properties.getProperty("receiver-type"));
        this.type = properties.getProperty("type");
        pluginEndpoint = properties.getProperty("endpoint");
        concurrency = properties.getProperty("concurrency", "1");
        endpoint = new PluginEndpoint();
        endpoint.setEndpointType(receiverType);
        endpoint.setType(type);
    }

    protected void setup(){
        setPluginInstance();
        try {
            loadProperties();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        pluginSender = AgentBroker.getSender(senderType);
        pluginReceiver = AgentBroker.getReceiver(receiverType);
    }

    protected abstract void setPluginInstance();

    protected PluginAnswer onMethodInvoke(PluginMessage pluginMessage) throws PluginException, InvocationTargetException, IllegalAccessException {
        Object result = null;
        if (pluginMessage.getInterfaceClass().getName().equals(pluginInstance.getClass().getName())){
            for (Method m : pluginInstance.getClass().getMethods()){
                if (m.getName().equals(pluginMessage.getMethodName())){
                    result =  m.invoke(pluginInstance, pluginMessage.getParameters());
                }
            }
        }else throw new PluginException("Wrong interface!");

        PluginAnswer pluginAnswer = new PluginAnswer();
        pluginAnswer.setAnswer((Serializable) result);
        return pluginAnswer;
    }

    protected abstract void register();
}
