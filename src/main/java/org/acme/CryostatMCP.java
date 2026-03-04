package org.acme;

import io.quarkiverse.mcp.server.Tool;
import io.quarkus.qute.Qute;
import jakarta.inject.Inject;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RestClient;

public class CryostatMCP {

    @Inject @RestClient CryostatRESTClient cryostat;

    @Tool(description = "Get Cryostat server health and configuration")
    String getHealth() {
        return Qute.fmt(
                """
                Cryostat server version: {h.cryostatVersion}
                Git commit hash: {h.build.git.hash}
                """,
                Map.of("h", cryostat.health()));
    }
}
