package org.acme.model.graphql;

import java.util.Map;

public record Annotations(Map<String, String> platform, Map<String, String> cryostat) {}
