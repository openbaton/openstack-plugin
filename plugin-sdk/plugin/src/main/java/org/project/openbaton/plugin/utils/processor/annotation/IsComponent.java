package org.project.openbaton.plugin.utils.processor.annotation;

/**
 * Created by lto on 14/08/15.
 */
import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.SOURCE)
@Inherited
@Target({ElementType.TYPE})
public @interface IsComponent { }
