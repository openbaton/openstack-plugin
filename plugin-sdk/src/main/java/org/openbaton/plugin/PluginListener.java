package org.openbaton.plugin;

import com.google.gson.*;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import org.openbaton.catalogue.nfvo.PluginAnswer;
import org.openbaton.exceptions.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
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

            while (!exit) {
                log.info("\nAwaiting RPC requests");
                QueueingConsumer.Delivery delivery = consumer.nextDelivery();

                BasicProperties props = delivery.getProperties();
                BasicProperties replyProps = new BasicProperties
                        .Builder()
                        .correlationId(props.getCorrelationId())
//                        .contentType("plain/text")
                        .build();

                String message = new String(delivery.getBody());

                log.debug("received: " + message);

                PluginAnswer answer = new PluginAnswer();

                answer.setAnswer(executeMethod(message));

                String response = gson.toJson(answer);

                log.debug("Answer is: " + response);
                log.debug("reply queue is: " + props.getReplyTo());

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
        } catch (NotFoundException e) {
            e.printStackTrace();
        }


    }

    private Serializable executeMethod(String pluginMessageString) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NotFoundException {

        JsonObject pluginMessageObject = gson.fromJson(pluginMessageString, JsonObject.class);

        List<Object> params = new ArrayList<Object>();

        for (JsonElement param : pluginMessageObject.get("parameters").getAsJsonArray()){
            Object p = gson.fromJson(param, Object.class);
            if (p!= null)
                params.add(p);
        }

        Class pluginClass = pluginInstance.getClass();

        log.debug("Params are: " + params);

        for (Method m : pluginClass.getMethods()){
            log.debug("Method checking is: " + m.getName() + " with " + m.getParameterTypes().length + " parameters");
            if (m.getName().equals(pluginMessageObject.get("methodName").getAsString()) && m.getParameterTypes().length == params.size()){
                if (!m.getReturnType().equals(Void.class)) {
                    if (params.size() != 0) {
                        params = getParameters(pluginMessageObject.get("parameters").getAsJsonArray(), m.getParameterTypes());
                        return (Serializable) m.invoke(pluginInstance, params.toArray());
                    }
                    else
                        return (Serializable) m.invoke(pluginInstance);
                }
                else{
                    if (params.size() != 0) {
                        params = getParameters(pluginMessageObject.get("parameters").getAsJsonArray(), m.getParameterTypes());
                        for (Object p : params){
                            log.debug("param class is: " + p.getClass());
                        }
                        m.invoke(pluginInstance, params.toArray());
                    }
                    else
                        m.invoke(pluginInstance);

                    return "{}";
                }
            }
        }

        throw new NotFoundException("method not found");

    }

    private List<Object> getParameters(JsonArray parameters, Class<?>[] parameterTypes) {
        List<Object> res = new LinkedList<Object>();
        for (int i =0 ; i < parameters.size(); i++){
            res.add( gson.fromJson(parameters.get(i), parameterTypes[i]) );
        }
        return res;
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
