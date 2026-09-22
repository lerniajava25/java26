package org.example;

import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import static org.reflections.scanners.Scanners.SubTypes;
import static org.reflections.scanners.Scanners.TypesAnnotated;

public class Main {

    public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        // -------------------------------------------------------------------------
        // 1. Direct Reflection (Compile-time known class)
        // -------------------------------------------------------------------------
        // When the class name is known at compile time, obtain its Class<?> instance
        // via a class literal (.class) or from an existing object reference (.getClass()).
        Class<A> classInfo = A.class;

        // Example alternative if an instance is already present:
        // A a = new A(new B());
        // Class<? extends A> classInfo2 = a.getClass();

        // Inspect and print all annotations declared on class A
        System.out.println("Annotations on " + classInfo.getSimpleName() + ":");
        Annotation[] annotations = classInfo.getAnnotations();
        for (Annotation annotation : annotations) {
            System.out.println(" - " + annotation.annotationType().getName());
        }

        System.out.println();

        // -------------------------------------------------------------------------
        // 2. Classpath Scanning (Runtime discovery of unknown classes)
        // -------------------------------------------------------------------------
        // When class names are not known in advance, scan the classpath/package
        // to dynamically discover classes decorated with a specific annotation (@MyDemo).
        // Alternatives include ClassGraph (https://github.com/classgraph/classgraph)
        // or implementing a custom ClassLoader/bytecode scanner.
        System.out.println("Scanning package 'org.example' for @MyDemo annotated classes...");
        Reflections reflections = new Reflections("org.example");

        // Query for all types annotated with @MyDemo in the scanned package
        Set<Class<?>> annotated =
                reflections.get(SubTypes.of(TypesAnnotated.with(MyDemo.class)).asClass());

        // -------------------------------------------------------------------------
        // 3. Dynamic Inspection and Instantiation
        // -------------------------------------------------------------------------
        // Iterate through discovered classes, inspect their constructors, and create instances.
        for (Class<?> clazz : annotated) {
            System.out.println("\nDiscovered class: " + clazz.getSimpleName());

            try {
                // Look for a parameterless constructor
                Constructor<?> ctor = clazz.getConstructor();
                Object obj = ctor.newInstance();
                System.out.println(" -> Successfully instantiated: " + obj.getClass().getSimpleName());
            } catch (NoSuchMethodException e) {
                // Handle classes that require constructor arguments (such as A(B b))
                System.out.println(" -> No parameterless constructor available for " + clazz.getSimpleName()
                        + " (requires arguments)");
            }
        }
    }
}
