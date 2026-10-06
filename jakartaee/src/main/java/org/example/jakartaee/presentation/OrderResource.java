package org.example.jakartaee.presentation;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.jakartaee.application.SubmitOrderUseCase;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {
    @Inject
    private SubmitOrderUseCase submitOrderUseCase;

    @POST
    public Response submitOrder(OrderRequestDto requestDto) {
        String orderId = submitOrderUseCase.execute(requestDto.customerID());
        return Response.status(Response.Status.CREATED).entity(orderId).build();
    }
}
