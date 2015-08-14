package org.project.openbaton.plugin.exceptions;

/**
 * Created by lto on 13/08/15.
 */
public class PluginException extends Exception{
    public PluginException(String message) {
        super(message);
    }

    public PluginException(String message, Throwable cause) {
        super(message, cause);
    }

    public PluginException(Throwable cause) {
        super(cause);
    }
}
