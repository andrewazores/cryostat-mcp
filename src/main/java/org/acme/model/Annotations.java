package org.acme.model;

import java.util.Map;

public record Annotations(Map<String, String> platform, Map<String, String> cryostat) {}
