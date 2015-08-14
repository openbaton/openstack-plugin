package org.project.openbaton.plugin.main.spring;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.project.openbaton.catalogue.nfvo.PluginAnswer;
import org.project.openbaton.catalogue.nfvo.PluginMessage;
import org.project.openbaton.plugin.exceptions.PluginException;
import org.project.openbaton.plugin.interfaces.main.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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

import javax.annotation.PostConstruct;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by lto on 13/08/15.
 */
@SpringBootApplication
@ComponentScan(basePackages = "org.project.openbaton")
@EnableJms
public class SpringPlugin extends Plugin implements JmsListenerConfigurer {

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
        endpoint.setDestination(this.endpoint.getEndpoint());
        endpoint.setMessageListener(this);
        endpoint.setConcurrency(concurrency);
        endpoint.setId(String.valueOf(Thread.currentThread().getId()));
        registrar.registerEndpoint(endpoint);
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

        pluginSender.send(endpoint.getEndpoint(), answer);
    }

    @PostConstruct
    private void init(){
        setup();
    }

    @Override
    protected void register() {
        log.debug("Registering plugin: " + endpoint);
        this.pluginSender.send("plugin-register", endpoint);
    }

    public static void main(String[] args) {
        SpringApplication.run(SpringPlugin.class);
    }
}
