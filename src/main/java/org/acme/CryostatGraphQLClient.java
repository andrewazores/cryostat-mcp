package org.acme;

import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import java.util.List;
import org.acme.model.DiscoveryNode;
import org.acme.model.DiscoveryNodeFilter;

@GraphQLClientApi(configKey = "cryostat")
public interface CryostatGraphQLClient {
    List<DiscoveryNode> targetNodes(DiscoveryNodeFilter filter);
}
