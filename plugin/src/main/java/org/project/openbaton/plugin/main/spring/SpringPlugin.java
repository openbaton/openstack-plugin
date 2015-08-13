package org.project.openbaton.plugin.main.spring;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.project.openbaton.catalogue.nfvo.PluginAnswer;
import org.project.openbaton.catalogue.nfvo.PluginMessage;
import org.project.openbaton.plugin.exceptions.PluginException;
import org.project.openbaton.plugin.interfaces.main.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;

import javax.annotation.PostConstruct;
import javax.jms.*;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by lto on 13/08/15.
 */
@EnableAutoConfiguration
@ComponentScan(basePackages = "org.project.openbaton")
@EnableJms
public class SpringPlugin extends Plugin implements MessageListener, JmsListenerConfigurer {

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private ConfigurableApplicationContext context;

    @Autowired
    private JmsListenerContainerFactory<?> jmsListenerContainerFactory;
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Bean
    ConnectionFactory connectionFactory() {
        return new ActiveMQConnectionFactory();
    }

    @Bean
    JmsListenerContainerFactory<?> jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setCacheLevelName("CACHE_CONNECTION");
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrency(concurrency);
        return factory;
    }

    @Override
    public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
        registrar.setContainerFactory(jmsListenerContainerFactory);
        SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
        endpoint.setDestination(pluginEndpoint);
        endpoint.setMessageListener(this);
        endpoint.setConcurrency(concurrency);
        endpoint.setId(String.valueOf(Thread.currentThread().getId()));
        registrar.registerEndpoint(endpoint);
    }

    private MessageCreator getObjectMessageCreator(final Serializable message) {
        MessageCreator messageCreator = new MessageCreator() {
            @Override
            public Message createMessage(Session session) throws JMSException {
                ObjectMessage objectMessage = session.createObjectMessage(message);
                return objectMessage;
            }
        };
        return messageCreator;
    }

    @Override
    public void onMessage(Message message) {
        PluginMessage msg = null;
        try {
            msg = (PluginMessage) ((ObjectMessage) message).getObject();
        } catch (JMSException e) {
            e.printStackTrace();
            System.exit(1);
        }
        PluginAnswer answer = null;
        try {
            answer = this.onMethodInvoke(msg);
        } catch (PluginException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        this.jmsTemplate.send(pluginEndpoint, getObjectMessageCreator(answer));
    }

    @PostConstruct
    private void init(){
        setup();
    }

    @Override
    protected void register() {
        log.debug("Registering plugin: " + endpoint);
        this.jmsTemplate.send("plugin-register", getObjectMessageCreator(endpoint));
    }

}
