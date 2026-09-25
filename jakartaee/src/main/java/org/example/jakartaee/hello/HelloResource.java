package org.example.jakartaee.hello;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/hello-world")
public class HelloResource {

    private HelloService helloService;

    public HelloResource() {
    }

    @Inject
    public HelloResource(HelloService helloService) {
        this.helloService = helloService;
    }

    @GET
    @Produces("text/plain")
    public String hello() {
        return helloService.sayHello();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response sayHello(@Valid HelloRequestDTO requestDTO) {
        return Response.status(Response.Status.CREATED)
                .entity(requestDTO)
                .build();
    }
}
