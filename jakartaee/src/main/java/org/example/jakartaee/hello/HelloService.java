package org.example.jakartaee.hello;

import jakarta.enterprise.context.ApplicationScoped;
import org.example.jakartaee.cross.Log;
import org.example.jakartaee.cross.ReadLock;
import org.example.jakartaee.cross.WriteLock;

@ApplicationScoped
public class HelloService {

    @Log
    @ReadLock
    public String sayHello() {
        return "Hello, World!";
    }

    @WriteLock
    @ReadLock
    public String concat(String a, String b) {
        return a + b;
    }
}
