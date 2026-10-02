package org.example.jakartaee.processor;

import com.google.auto.service.AutoService;
import org.example.jakartaee.cross.ReadLock;
import org.example.jakartaee.cross.WriteLock;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

@SupportedAnnotationTypes({
        "org.example.jakartaee.cross.ReadLock",
        "org.example.jakartaee.cross.WriteLock"
})
@SupportedSourceVersion(SourceVersion.RELEASE_25)
@AutoService(Processor.class)
public class LockedAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // Kolla alla element som har @org.example.jakartaee.cross.ReadLock
        for (Element element : roundEnv.getElementsAnnotatedWith(ReadLock.class)) {
            if (element.getAnnotation(WriteLock.class) != null) {
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        "A method cannot have both @org.example.jakartaee.cross.ReadLock and @org.example.jakartaee.cross.WriteLock",
                        element
                );
            }
        }

        // Alternativt kan du även spegelvända eller bryta ut kontrollen,
        // men ovanstående hittar metoder som har @org.example.jakartaee.cross.ReadLock och kontrollerar om @org.example.jakartaee.cross.WriteLock också finns.

        return false; // Låt andra processorer (om det finns några) få kika på annotationerna
    }
}
