package org.acme;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.model.HealthResponse;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/cryostat")
public class CryostatResource {

    @Inject @RestClient CryostatClient cryostat;

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public HealthResponse health() {
        return cryostat.health();
    }
}
