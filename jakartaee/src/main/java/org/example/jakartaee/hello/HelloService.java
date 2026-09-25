package org.example.jakartaee.hello;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class HelloService {

    public String sayHello() {
        return "Hello, World!";
    }

    public String concat(String a, String b) {
        return a + b;
    }
}
