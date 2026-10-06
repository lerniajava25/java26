package org.example.jakartaee.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class ResourceNotFoundMapper implements ExceptionMapper<ResourceNotFound> {
    Logger logger = LoggerFactory.getLogger(ResourceNotFoundMapper.class);

    @Override
    public Response toResponse(ResourceNotFound exception) {
        logger.warn("Resource not found: " + exception.getMessage());
        return Response.status(Response.Status.NOT_FOUND)
                .entity("This resource couldn't be found: " + exception.getMessage()).build();
    }
}
