package org.example.jakartaee.today;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDate;

@Path("/today")
public class TodayResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getToday() {
        return LocalDate.now().toString();
    }
}
