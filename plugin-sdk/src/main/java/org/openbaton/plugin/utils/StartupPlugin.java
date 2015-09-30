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

package org.openbaton.plugin.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;

/**
 * Created by lto on 09/09/15.
 */
public class StartupPlugin {
    protected static Logger log = LoggerFactory.getLogger(StartupPlugin.class);

    private static <T extends Remote> Remote getStub(Class<T> plugin) throws InstantiationException, IllegalAccessException, java.lang.reflect.InvocationTargetException, NoSuchMethodException, RemoteException {
        Remote service = plugin.getConstructor().newInstance();
        return UnicastRemoteObject.exportObject(service,0);
    }

    public static <T extends Remote> void register(Class<T> clazz, String name, String registryIp, int port) throws InvocationTargetException, NoSuchMethodException, RemoteException, InstantiationException, IllegalAccessException, MalformedURLException {
        int i=0;
        log.info("Trying to connect to " + registryIp + ":" + port + " for 100 sec...");
        while (true)
            try {
                i++;
                Naming.rebind("//" + registryIp + ":" + port + "/" + name, getStub(clazz));
                break;
            }catch (ConnectException e){
                if (i == 50){
                    throw e;
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        log.debug("Remote service bound");
    }

    public static void unregister(String name, String registryIp, int port) throws RemoteException, NotBoundException, MalformedURLException {
        Naming.unbind("//" + registryIp + ":" + port + "/" + name);
    }

    public static void unregister(String name, String registryIp) throws RemoteException, NotBoundException, MalformedURLException {
        Naming.unbind("//" + registryIp + ":1099/" + name);
    }
}
