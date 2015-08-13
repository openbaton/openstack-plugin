package org.project.openbaton.plugin.interfaces;

import java.io.Serializable;

/**
 * Created by lto on 13/08/15.
 */
public interface PluginSender{
    void send(String destination, Serializable message);
}
