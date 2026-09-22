package org.example;


import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import static org.reflections.scanners.Scanners.SubTypes;
import static org.reflections.scanners.Scanners.TypesAnnotated;

public class Main {

    static void main() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        //Vi vet klassnamnet A
        var classInfo = A.class;
        //Vi har en object reference och vill ha klassinfon
        //var a = new A();
        //var classInfo2 = a.getClass();

        var annotations = classInfo.getAnnotations();
        for (Annotation annotation : annotations) {
            System.out.println(annotation.annotationType().getName());
        }

        //Leta efter klasser med annotation MyDemo
        //Vi vet inte klassnamnet?
        //Provar med reflections library
        //Alternativt https://github.com/classgraph/classgraph
        //Alternativt Implement custom scanner in our project
        Reflections reflections = new Reflections("org.example");

        Set<Class<?>> annotated =
                reflections.get(SubTypes.of(TypesAnnotated.with(MyDemo.class)).asClass());

        for (Class<?> clazz : annotated) {
            System.out.println(clazz.getSimpleName());
            var ctor = clazz.getConstructor();

            //Skapa instancer av dessa
            var obj = ctor.newInstance();
        }
    }
}
