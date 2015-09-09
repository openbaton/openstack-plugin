package org.project.openbaton.plugin.interfaces.main;

import org.project.openbaton.catalogue.nfvo.EndpointType;
import org.project.openbaton.catalogue.nfvo.PluginAnswer;
import org.project.openbaton.catalogue.nfvo.PluginEndpoint;
import org.project.openbaton.catalogue.nfvo.PluginMessage;
import org.project.openbaton.clients.interfaces.ClientInterfaces;
import org.project.openbaton.plugin.exceptions.PluginException;
import org.project.openbaton.plugin.interfaces.agents.PluginSender;
import org.project.openbaton.plugin.utils.AgentBroker;
import org.project.openbaton.plugin.utils.StartupPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;

import javax.jms.MessageListener;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.rmi.Remote;
import java.util.Properties;

/**
 * Created by lto on 13/08/15.
 */
public abstract class Plugin implements MessageListener {

    protected static Logger log = LoggerFactory.getLogger(Plugin.class);

    @Autowired
    private ConfigurableApplicationContext context;

    protected PluginSender pluginSender;

    protected String concurrency;

    protected Object pluginInstance;

    private EndpointType senderType;
    private EndpointType receiverType;

    protected PluginEndpoint endpoint;
    protected String type;

    protected String interfaceName;

    @Autowired
    private AgentBroker agentBroker;

    protected void loadProperties() throws IOException {
        Properties properties = new Properties();
        properties.load(pluginInstance.getClass().getResourceAsStream("/plugin.conf.properties"));
        this.senderType = getEndpointType(properties.getProperty("sender-type", "JMS").trim());
        this.receiverType = getEndpointType(properties.getProperty("receiver-type", "JMS").trim());
        this.type = properties.getProperty("type");
//        pluginEndpoint = properties.getProperty("endpoint");
        concurrency = properties.getProperty("concurrency", "1");
        endpoint = new PluginEndpoint();
        endpoint.setEndpoint(properties.getProperty("endpoint"));
        endpoint.setEndpointType(receiverType);
        endpoint.setType(type);
        try {
            endpoint.setInterfaceVersion((String) pluginInstance.getClass().getField("interfaceVersion").get(pluginInstance));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        log.debug("Plugin instance is: " + pluginInstance);
        log.debug("Plugin instance class is: " + pluginInstance.getClass().getName());
        interfaceName = pluginInstance.getClass().getInterfaces()[0].getName();
        log.debug("interfaceName is: " + interfaceName);
        endpoint.setInterfaceClass(interfaceName);
        log.debug("Loaded properties: " + properties);
    }

    private EndpointType getEndpointType(String trim) {
        log.debug("Endpoint type is: " + trim);
        return EndpointType.valueOf(trim);
    }

    protected void setup() throws Exception {
        setPluginInstance();
        StartupPlugin.register((Class<Remote>) pluginInstance.getClass());
        try {
            loadProperties();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        pluginSender = agentBroker.getSender(senderType);
        register();
    }

    protected void shutdown(){
        unregister();
    }

    protected abstract void unregister();

    public void setPluginInstance(){
        pluginInstance = context.getBean(ClientInterfaces.class);
    }

    /**
     * This method will invoke the method defined in the <b>pluginMessage</b>.
     * it needs also to check for error coming from the nfvo.
     *
     * @param pluginMessage
     * @return the answer
     * @throws PluginException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    protected PluginAnswer onMethodInvoke(PluginMessage pluginMessage) throws PluginException, InvocationTargetException, IllegalAccessException {
        switch (pluginMessage.getMethodName()){
            case "ERROR":
                log.error("There was an error");
                log.error("message is: " + pluginMessage.getParameters().iterator().next());
                log.error("please wait the shutdown of the plugin......");

                try {
                    Thread.currentThread().sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Thread.currentThread().interrupt();

            case "CLOSE":
                log.debug("shutting down the NFVO");
                log.debug("please wait the shutdown of the plugin......");

                try {
                    Thread.currentThread().sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Thread.currentThread().interrupt();
                System.exit(0);

            default:
                Object result = null;
                log.debug(pluginMessage.getInterfaceClass().getName() + " == " + interfaceName);
                if (pluginMessage.getInterfaceClass().getName().equals(interfaceName)){
                    for (Method m : pluginInstance.getClass().getMethods()){
                        if (m.getName().equals(pluginMessage.getMethodName())){
                            log.debug("Method name is " + m.getName());
                            log.debug("Method parameter types are: ");
                            for (Type t : m.getParameterTypes()){
                                log.debug("\t*) " + t.toString());
                            }
                            log.debug("Actual Parameters are: " + pluginMessage.getParameters());
                            result =  m.invoke(pluginInstance, pluginMessage.getParameters().toArray());
                        }
                    }
                }else throw new PluginException("Wrong interface!");

                PluginAnswer pluginAnswer = new PluginAnswer();
                pluginAnswer.setAnswer((Serializable) result);
                pluginAnswer.setSelector(pluginMessage.getSelector());
                return pluginAnswer;
        }
    }

    protected abstract void register();
}
