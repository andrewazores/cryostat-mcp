package org.acme.model;

import java.util.Map;

public record Target(
        long id,
        String connectUrl,
        String alias,
        String jvmId,
        Map<String, String> labels,
        Annotations annotations) {}
