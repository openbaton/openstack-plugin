/*
 * Copyright (c) 2015 Fraunhofer FOKUS
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openbaton.plugin;

import org.openbaton.monitoring.interfaces.VirtualisedResourcesPerformanceManagement;
import org.openbaton.plugin.utils.StartupPlugin;
import org.openbaton.vim.drivers.interfaces.ClientInterfaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * Created by lto on 09/09/15.
 */
public class PluginStarter {

    protected static Logger log = LoggerFactory.getLogger(PluginStarter.class);
    private static Map<String, PluginListener> plugins = new HashMap<String, PluginListener>();
    private static Properties properties;
    private static ExecutorService executor;

    public static void run(Class clazz, final String name, final String registryIp) {
        String nm = "";
        try {
            log.info("Starting plugin with name: " + name);
            log.debug("Registry ip: " + registryIp);
            log.debug("Class to register: " + clazz.getName());
            Properties properties = new Properties();
            properties.load(clazz.getResourceAsStream("/plugin.conf.properties"));
            String inte = "";
            for (Class interf : clazz.getSuperclass().getInterfaces())
                if (interf.getName().equals(ClientInterfaces.class.getName())) {
                    inte = "vim-drivers";
                    break;
                } else if (interf.getName().equals(VirtualisedResourcesPerformanceManagement.class.getName())) {
                    inte = "monitor";
                    break;
                } else
                    inte = "unknown-interface";

            if (inte.equals("unknown-interface")) // no interface found
                throw new RuntimeException("The plugin class " + clazz.getSimpleName() + " needs to extend or VimDriver or Monitoring classes");

            nm = inte + "." + properties.getProperty("type", "unknown") + "." + name;
            StartupPlugin.register(clazz, nm, registryIp, 1099);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
        final String fullName = nm;
        final Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    mainThread.join();
                    log.info("Unregistering: " + fullName);
                    StartupPlugin.unregister(fullName, registryIp);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (NotBoundException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void run(Class clazz, final String name, final String registryIp, int port) {
        String nm = "";
        try {
            log.info("Starting plugin with name: " + name);
            log.debug("Registry ip: " + registryIp);
            log.debug("Class to register: " + clazz.getName());
            nm = getFinalName(clazz, name);
            StartupPlugin.register(clazz, nm, registryIp, port);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
        final String fullName = nm;
        final Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    mainThread.join();
                    log.info("Unregistering: " + fullName);
                    StartupPlugin.unregister(fullName, registryIp);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (NotBoundException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static String getFinalName(Class clazz, String name) throws IOException {
        properties = new Properties();
        properties.load(clazz.getResourceAsStream("/plugin.conf.properties"));
        String inte = "";
        for (Class interf : clazz.getSuperclass().getInterfaces())
            if (interf.getName().equals(ClientInterfaces.class.getName())) {
                inte = "vim-drivers";
                break;
            } else if (interf.getName().equals(VirtualisedResourcesPerformanceManagement.class.getName())) {
                inte = "monitor";
                break;
            } else
                inte = "unknown-interface";

        if (inte.equals("unknown-interface")) // no interface found
            throw new RuntimeException("The plugin class " + clazz.getSimpleName() + " needs to extend or VimDriver or Monitoring classes");
        return inte + "." + properties.getProperty("type", "unknown") + "." + name;
    }

    public static void registerPlugin(Class clazz, String name, String brokerIp, int port, int consumers) throws IOException, TimeoutException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {

        String username = properties.getProperty("username", "admin");
        String password = properties.getProperty("password", "openbaton");
        registerPlugin(clazz, name, brokerIp, port, consumers, username, password);

    }

    public static void registerPlugin(Class clazz, String name, String brokerIp, int port, int consumers, String username, String password) throws IOException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        executor = Executors.newFixedThreadPool(consumers);
        for (int i = 0 ; i<consumers ; i++ ) {
            PluginListener pluginListener = new PluginListener();
            pluginListener.setPluginId(getFinalName(clazz, name));
            pluginListener.setPluginInstance(clazz.getConstructor().newInstance());
            pluginListener.setBrokerIp(brokerIp);
            pluginListener.setBrokerPort(port);
            pluginListener.setUsername(username);
            pluginListener.setPassword(password);

            executor.execute(pluginListener);
        }
    }
}
