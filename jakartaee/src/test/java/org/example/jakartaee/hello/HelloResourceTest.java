package org.example.jakartaee.hello;

import org.junit.jupiter.api.Test;

class HelloResourceTest {


    @Test
    void helloWorld() {
        HelloService helloService = new HelloService();
        HelloResource helloResource = new HelloResource(helloService);

        helloResource.hello();
    }


}
