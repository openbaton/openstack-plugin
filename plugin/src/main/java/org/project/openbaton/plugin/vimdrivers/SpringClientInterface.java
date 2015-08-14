package org.project.openbaton.plugin.vimdrivers;

import org.project.openbaton.clients.interfaces.ClientInterfaces;
import org.project.openbaton.plugin.utils.processor.annotation.IsComponent;

import java.io.Serializable;

/**
 * Created by lto on 14/08/15.
 */
@IsComponent
public abstract class SpringClientInterface implements ClientInterfaces, Serializable {

}
