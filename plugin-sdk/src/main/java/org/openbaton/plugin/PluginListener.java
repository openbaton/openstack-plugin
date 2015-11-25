package org.openbaton.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import org.openbaton.catalogue.nfvo.PluginAnswer;
import org.openbaton.catalogue.nfvo.PluginMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Created by lto on 25/11/15.
 */
public class PluginListener implements Runnable {

    private static final String exchange = "plugin-exchange";
    private String pluginId;
    private Object pluginInstance;
    private Logger log;
    private QueueingConsumer consumer;
    private Channel channel;
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private boolean exit = false;
    private String brokerIp;
    private int brokerPort;
    private String username;
    private String password;
    private Connection connection;

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public Object getPluginInstance() {
        return pluginInstance;
    }

    public void setPluginInstance(Object pluginInstance) {
        this.pluginInstance = pluginInstance;
        log = LoggerFactory.getLogger(pluginInstance.getClass().getName());
    }

    public boolean isExit() {
        return exit;
    }

    public void setExit(boolean exit) {
        this.exit = exit;
    }

    @Override
    public void run() {

        try {
            initRabbitMQ();

            log.info("Awaiting RPC requests");
            while (!exit) {
                QueueingConsumer.Delivery delivery = consumer.nextDelivery();

                BasicProperties props = delivery.getProperties();
                BasicProperties replyProps = new BasicProperties
                        .Builder()
                        .correlationId(props.getCorrelationId())
//                        .contentType("plain/text")
                        .build();

                String message = new String(delivery.getBody());

                //Parse the message

                PluginMessage pluginMessage = gson.fromJson(message, PluginMessage.class);

                PluginAnswer answer = new PluginAnswer();

                answer.setAnswer(executeMethod(pluginMessage));

                String response = gson.toJson(answer);

                channel.basicPublish(exchange, props.getReplyTo(), replyProps, response.getBytes());

                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
            channel.close();
            connection.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }


    }

    private Serializable executeMethod(PluginMessage pluginMessage) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class pluginClass = pluginInstance.getClass();

        List<Class> parameterTypes = new ArrayList<Class>();
        for (Serializable param : pluginMessage.getParameters()) {
            parameterTypes.add(param.getClass());
        }
        Class[] cls = new Class[0];
        Method method = pluginClass.getMethod(pluginMessage.getMethodName(), parameterTypes.toArray(cls));

        return (Serializable) method.invoke(pluginInstance, pluginMessage.getParameters());
    }

    private void initRabbitMQ() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(brokerIp);
        factory.setPort(brokerPort);
        factory.setPassword(password);
        factory.setUsername(username);

        connection = factory.newConnection();
        channel = connection.createChannel();

        channel.exchangeDeclare(exchange, "topic");
        channel.queueDeclare(pluginId, false, false, true, null);
        channel.queueBind(pluginId, exchange, pluginId);

        channel.basicQos(1);

        consumer = new QueueingConsumer(channel);
        channel.basicConsume(pluginId, false, consumer);
    }

    public String getBrokerIp() {
        return brokerIp;
    }

    public void setBrokerIp(String brokerIp) {
        this.brokerIp = brokerIp;
    }

    public int getBrokerPort() {
        return brokerPort;
    }

    public void setBrokerPort(int brokerPort) {
        this.brokerPort = brokerPort;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
