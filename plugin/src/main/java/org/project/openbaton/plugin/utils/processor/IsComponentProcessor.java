package org.project.openbaton.plugin.utils.processor;

import org.springframework.stereotype.Component;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

/**
 * Created by lto on 14/08/15.
 */
@SupportedAnnotationTypes("IsComponent")
public class IsComponentProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        System.out.print("HERE!");
        for (TypeElement typeElement : annotations) {
            System.out.print("HERE!");
            Component annotation = typeElement.getAnnotation(Component.class);
            System.out.print("" + annotation);
            if (annotation == null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "The Plugin Class is not annotated with @Component", typeElement);
            }
        }

        // All IsComponent annotations are handled by this Processor.
        return true;
    }
}