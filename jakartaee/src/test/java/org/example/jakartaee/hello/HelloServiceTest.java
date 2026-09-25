package org.example.jakartaee.hello;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelloServiceTest {

    HelloService helloService = new HelloService();

    @Test
    void concatReturnsConcatenatedString() {
        assertThat(helloService.concat("a", "b")).isEqualTo("ab");
    }

    @Test
    void sayHelloShouldReturnHelloWorld() {
        assertThat(helloService.sayHello()).isEqualTo("Hello, World!");
    }
}
