package org.acme;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.io.InputStream;
import java.util.List;
import org.acme.model.EventTemplate;
import org.acme.model.Health;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(
        configKey = "cryostat",
        baseUri = "http://localhost:8181" // this should always be overridden
        )
@ClientHeaderParam(
        name = "Authorization",
        value = "{org.acme.CryostatRESTClient.authorizationHeader}")
public interface CryostatRESTClient {

    @GET
    @Path("/health")
    Health health();

    @GET
    @Path("/api/v4/targets/{targetId}/event_templates")
    List<EventTemplate> targetEventTemplates(long targetId);

    @GET
    @Path("/api/v4/targets/{targetId}/event_templates/{templateType}/{templateName}")
    InputStream targetEventTemplate(long targetId, String templateType, String templateName);

    default String authorizationHeader(@ConfigProperty(name = "cryostat.auth.value") String auth) {
        return auth;
    }
}
