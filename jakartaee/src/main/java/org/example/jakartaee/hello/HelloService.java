package org.example.jakartaee.hello;

import jakarta.enterprise.context.ApplicationScoped;
import org.example.jakartaee.cross.Log;
import org.example.jakartaee.cross.ReadLock;
import org.example.jakartaee.cross.WriteLock;
import org.example.jakartaee.exceptions.ResourceNotFound;

@ApplicationScoped
public class HelloService {

    @Log
    @ReadLock
    public String sayHello() {
        throw new ResourceNotFound("Hello World");
        //return "Hello, World!";
    }

    @WriteLock
    //@ReadLock
    public String concat(String a, String b) {
        return a + b;
    }
}
