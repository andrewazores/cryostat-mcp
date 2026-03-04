package org.acme;

import io.quarkus.rest.client.reactive.ClientBasicAuth;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.acme.model.HealthResponse;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(
        configKey = "cryostat",
        baseUri = "http://localhost:8181" // this should always be overridden
        )
// FIXME real Cryostat deployments may use Basic or Bearer auth
@ClientBasicAuth(username = "${cryostat.auth.username}", password = "${cryostat.auth.password}")
public interface CryostatRESTClient {

    @GET
    @Path("/health")
    HealthResponse health();
}
