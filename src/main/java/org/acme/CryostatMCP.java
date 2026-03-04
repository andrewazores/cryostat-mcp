package org.acme;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.util.List;
import org.acme.model.DiscoveryNode;
import org.acme.model.DiscoveryNodeFilter;
import org.acme.model.EventTemplate;
import org.acme.model.Health;
import org.eclipse.microprofile.rest.client.inject.RestClient;

public class CryostatMCP {

    @Inject @RestClient CryostatRESTClient rest;
    @Inject CryostatGraphQLClient graphql;

    @Tool(description = "Get Cryostat server health and configuration")
    Health getHealth() {
        return rest.health();
    }

    @Tool(
            description =
                    """
                    Get a list of all discovered Target applications. Each Target belongs to a Discovery Node. In a Kubernetes context
                    the Discovery Node will be a Pod or equivalent object. Searching for the Target associated with a particular Pod
                    can be done by querying this endpoint with the Pod's name as a filter input. If no filter inputs are provided,
                    the full list of all discovered Targets will be returned. Otherwise, if any filter inputs are provided, then only
                    Targets which match all of the given inputs will be returned.
                    """)
    List<DiscoveryNode> listTargets(
            @ToolArg(
                            description =
                                    """
                                    List of Discovery Node IDs to match. Discovery Nodes matching any of the given IDs will be selected.
                                    """,
                            required = false)
                    List<Long> ids,
            @ToolArg(
                            description =
                                    """
                                    List of Target IDs to match. Targets matching any of the given IDs will be selected.
                                    """)
                    List<Long> targetIds,
            @ToolArg(
                            description =
                                    """
                                    List of Discovery Node names to match. Discovery Nodes matching any of the given names will be selected.
                                    """,
                            required = false)
                    List<String> names,
            @ToolArg(
                            description =
                                    """
                                    List of Discovery Node label selectors to match. Discovery Nodes matching any of the given label selectors will be selected.
                                    Label selectors use the Kubernetes selector syntax: "my-label=foo", "my-label != bar", "env in (prod, stage)", "!present".
                                    """,
                            required = false)
                    List<String> labels) {
        return graphql.targetNodes(DiscoveryNodeFilter.from(ids, targetIds, names, labels));
    }

    @Tool(
            description =
                    "List the available JDK Flight Recorder Event Templates for a given Target.")
    List<EventTemplate> listTargetEventTemplates(
            @ToolArg(description = "The Target's ID.", required = true) long targetId) {
        return rest.targetEventTemplates(targetId);
    }

    @Tool(description = "Get a specific .jfc (XML) JDK Flight Recorder Event Template definition.")
    InputStream getTargetEventTemplate(
            @ToolArg(description = "The Target's ID.", required = true) long targetId,
            @ToolArg(description = "The event template's templateType.", required = true)
                    String templateType,
            @ToolArg(description = "The event template's name.", required = true)
                    String templateName) {
        return rest.targetEventTemplate(targetId, templateType, templateName);
    }
}
