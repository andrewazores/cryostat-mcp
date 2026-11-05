/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.mcp.k8s;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.cryostat.mcp.CryostatMCP;
import io.cryostat.mcp.CryostatServerVersions;
import io.cryostat.mcp.CryostatToolMetadata;
import io.cryostat.mcp.k8s.PodNameResolver.TargetInfo;
import io.cryostat.mcp.model.ArchivedRecordingDescriptor;
import io.cryostat.mcp.model.AsyncProfilerStatus;
import io.cryostat.mcp.model.EventTemplate;
import io.cryostat.mcp.model.RecordingDescriptor;
import io.cryostat.mcp.model.graphql.StoppedRecording;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.Duration;
import io.quarkiverse.mcp.server.MetaField;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class K8sOrientedTools {

    @Inject CryostatMCPInstanceManager instanceManager;
    @Inject PodNameResolver podNameResolver;
    @Inject ArchivedRecordingSynthesizer synthesizer;

    @Tool(
            description =
                    "List the available JDK Flight Recorder Event Templates for a given"
                            + " application.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    @MetaField(
            prefix = CryostatToolMetadata.META_PREFIX,
            name = CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME,
            value = CryostatServerVersions.V4_0)
    public List<EventTemplate> listEventTemplates(
            @ToolArg(description = "The namespace of application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application", required = true)
                    String podName) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        long targetId = podNameResolver.resolvePodNameToTargetId(namespace, podName);
        return mcp.listTargetEventTemplates(targetId);
    }

    @Tool(description = "Get a list of active JDK Flight Recordings present in the Target JVM.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    @MetaField(
            prefix = CryostatToolMetadata.META_PREFIX,
            name = CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME,
            value = CryostatServerVersions.V4_0)
    public List<RecordingDescriptor> getActiveRecordingsList(
            @ToolArg(description = "The namespace of the application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application", required = true)
                    String podName) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        long targetId = podNameResolver.resolvePodNameToTargetId(namespace, podName);
        return mcp.listTargetActiveRecordings(targetId);
    }

    @Tool(
            description =
                    "Start a Flight Recording on the given application. The optional asprofEvents"
                        + " parameter enables async-profiler's \"jfrsync\" mode alongside the usual"
                        + " JFR event template, on Cryostat Agent-connected applications only"
                        + " (requires Cryostat 4.3 or newer). Event names must come from"
                        + " listAsyncProfilerEventTypes for this same application -- they are"
                        + " dynamic per application/async-profiler version/OS and must never be"
                        + " guessed or reused from a different application. Call"
                        + " applicationSupportsAsyncProfiler first to check feasibility. Enabling a"
                        + " cpu/alloc/lock-related asprof event silently disables the corresponding"
                        + " built-in JDK event(s) in the selected template for the overlapping"
                        + " categories -- the template's settings there are superseded, not merged"
                        + " with the asprof event. This tool does not detect or prevent conflicts"
                        + " with any other concurrent async-profiler usage on the same application"
                        + " (e.g. a standalone profile or another jfrsync recording already using"
                        + " the application's one available profiling session) -- such a conflict"
                        + " simply fails this tool call with whatever error Cryostat returns.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    @MetaField(
            prefix = CryostatToolMetadata.META_PREFIX,
            name = CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME,
            value = CryostatServerVersions.V4_0)
    public RecordingDescriptor startFlightRecording(
            @ToolArg(description = "The namespace of application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application", required = true)
                    String podName,
            @ToolArg(
                            description = "The Event Template to use for recording",
                            required = true,
                            defaultValue = "Continuous")
                    String eventTemplate,
            @ToolArg(
                            description =
                                    "The recording's duration. '0s' means continuous and ongoing,"
                                        + " otherwise a Kubernetes duration specifier can be"
                                        + " provided. Fixed-length recordings will be automatically"
                                        + " archived upon completion.",
                            required = true,
                            defaultValue = "0s")
                    String duration,
            @ToolArg(
                            description =
                                    "async-profiler event names to enable in jfrsync mode alongside"
                                        + " this recording. Cryostat Agent-connected applications"
                                        + " only; requires Cryostat 4.3 or newer. Omit for a plain"
                                        + " JFR recording.",
                            required = false)
                    List<String> asprofEvents)
            throws ParseException, JsonProcessingException {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        long targetId = podNameResolver.resolvePodNameToTargetId(namespace, podName);
        String recordingName = UUID.randomUUID().toString();
        long durationSeconds = Duration.parse(duration).getDuration().getSeconds();
        List<String> events = asprofEvents == null ? List.of() : asprofEvents;
        return mcp.startTargetRecording(
                targetId, recordingName, eventTemplate, "TARGET", durationSeconds, events);
    }

    @Tool(
            description =
                    "Get an application's raw async-profiler status: whether the profiler is"
                        + " currently stopped/running, and which async-profiler event types are"
                        + " available, grouped by category. Throws for applications that are not"
                        + " Cryostat Agent-connected (expected, not a bug). May also fail"
                        + " (including a slow timeout) if the application's agent is"
                        + " Agent-connected but does not have async-profiler loaded at all. Prefer"
                        + " applicationSupportsAsyncProfiler for a quick yes/no check.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    @MetaField(
            prefix = CryostatToolMetadata.META_PREFIX,
            name = CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME,
            value = CryostatServerVersions.V4_2)
    public AsyncProfilerStatus getAsyncProfilerStatus(
            @ToolArg(description = "The namespace of the application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application", required = true)
                    String podName) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        long targetId = podNameResolver.resolvePodNameToTargetId(namespace, podName);
        return mcp.getTargetAsyncProfilerStatus(targetId);
    }

    @Tool(
            description =
                    "List the async-profiler event types available on an application, grouped by"
                        + " category. The category keys are stable, but the event names within each"
                        + " category are dynamic -- they depend on the application's loaded"
                        + " async-profiler build, JVM version, and OS/kernel configuration, and can"
                        + " differ between applications. Always query this fresh for the specific"
                        + " application you intend to use with startFlightRecording's asprofEvents"
                        + " parameter; never assume or reuse a list from a different application.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    @MetaField(
            prefix = CryostatToolMetadata.META_PREFIX,
            name = CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME,
            value = CryostatServerVersions.V4_2)
    public Map<String, List<String>> listAsyncProfilerEventTypes(
            @ToolArg(description = "The namespace of the application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application", required = true)
                    String podName) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        long targetId = podNameResolver.resolvePodNameToTargetId(namespace, podName);
        return mcp.listTargetAsyncProfilerEventTypes(targetId);
    }

    @Tool(
            description =
                    "Check whether an application currently has async-profiler available and"
                        + " usable. This only checks async-profiler's availability in isolation --"
                        + " a true result does not guarantee a subsequent startFlightRecording call"
                        + " with asprofEvents will succeed, since another standalone profile or"
                        + " jfrsync recording may already be using the application's one available"
                        + " profiling session. Call this before passing asprofEvents to"
                        + " startFlightRecording. Note: a false result can currently take several"
                        + " seconds to arrive for a Cryostat Agent-connected application whose"
                        + " agent lacks async-profiler, due to a known upstream reliability gap in"
                        + " the status check.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    @MetaField(
            prefix = CryostatToolMetadata.META_PREFIX,
            name = CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME,
            value = CryostatServerVersions.V4_2)
    public Boolean applicationSupportsAsyncProfiler(
            @ToolArg(description = "The namespace of the application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application", required = true)
                    String podName) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        long targetId = podNameResolver.resolvePodNameToTargetId(namespace, podName);
        return mcp.targetSupportsAsyncProfiler(targetId);
    }

    @Tool(
            description =
                    "Create a snapshot of the current active recording data in the Target JVM,"
                        + " archive it to persistent storage, and delete the intermediate active"
                        + " snapshot. Returns the descriptor of the resulting archived recording.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    @MetaField(
            prefix = CryostatToolMetadata.META_PREFIX,
            name = CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME,
            value = CryostatServerVersions.V4_0)
    public ArchivedRecordingDescriptor archiveRecording(
            @ToolArg(description = "The namespace of the application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application.", required = true)
                    String podName) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        PodNameResolver.TargetInfo targetInfo = podNameResolver.resolveTarget(namespace, podName);
        return mcp.archiveTargetRecording(targetInfo.targetId(), targetInfo.jvmId());
    }

    @Tool(description = "Stop an active JDK Flight Recording on the given application.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    @MetaField(
            prefix = CryostatToolMetadata.META_PREFIX,
            name = CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME,
            value = CryostatServerVersions.V4_0)
    public StoppedRecording stopRecording(
            @ToolArg(description = "The namespace of the application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application.", required = true)
                    String podName,
            @ToolArg(description = "The name of the recording to stop.", required = true)
                    String recordingName) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        long targetId = podNameResolver.resolvePodNameToTargetId(namespace, podName);
        return mcp.stopTargetRecording(targetId, recordingName);
    }

    @Tool(
            description =
                    "Get a list of archived Flight Recordings retrieved from the given target JVM.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    @MetaField(
            prefix = CryostatToolMetadata.META_PREFIX,
            name = CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME,
            value = CryostatServerVersions.V4_0)
    public List<ArchivedRecordingDescriptor> getArchivedRecordingsList(
            @ToolArg(description = "The namespace of the application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application.", required = true)
                    String podName) {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        String jvmId = podNameResolver.resolvePodNameToJvmId(namespace, podName);
        return mcp.listTargetArchivedRecordings(jvmId).stream()
                .flatMap(dir -> dir.recordings().stream())
                .toList();
    }

    @Tool(
            description =
                    "Get the automated analysis report for a JVM's archived Flight Recordings that"
                        + " intersect the given time range. If a single recording covers the range"
                        + " it is used directly; if multiple recordings intersect the range they"
                        + " are synthesized into a single recording first. If no range is given"
                        + " this simply returns the most recent report from cache, or else triggers"
                        + " a fresh on-demand analysis assuming that at least one active recording"
                        + " is available.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    @MetaField(
            prefix = CryostatToolMetadata.META_PREFIX,
            name = CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME,
            value = CryostatServerVersions.V4_2_1)
    public String getAnalysisReport(
            @ToolArg(description = "The namespace of the application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application.", required = true)
                    String podName,
            @ToolArg(
                            description =
                                    "Filter events after this timestamp (ISO 8601). Defaults to the"
                                            + " Unix epoch.",
                            required = false)
                    Optional<String> fromTimestamp,
            @ToolArg(
                            description =
                                    "Filter events before this timestamp (ISO 8601). Defaults to"
                                            + " now.",
                            required = false)
                    Optional<String> toTimestamp)
            throws IOException, UnsatisfiableRangeException {
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        TargetInfo target = podNameResolver.resolveTarget(namespace, podName);
        boolean latest = fromTimestamp.isEmpty() && toTimestamp.isEmpty();
        if (!latest) {
            Date from = Date.from(fromTimestamp.map(Instant::parse).orElse(Instant.EPOCH));
            Date to = Date.from(toTimestamp.map(Instant::parse).orElseGet(Instant::now));
            ArchivedRecordingDescriptor recording =
                    synthesizer.synthesize(namespace, target, from, to);
            return mcp.getArchivedReport(target.jvmId(), recording.name());
        }
        return mcp.getTargetAnalysisReport(target.targetId());
    }

    @Tool(
            description =
                    "List the JFR event types present in archived Flight Recordings for the given"
                        + " application that intersect the specified time range. If a single"
                        + " recording covers the range it is used directly; if multiple recordings"
                        + " intersect the range they are synthesized into a single recording"
                        + " first.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    @MetaField(
            prefix = CryostatToolMetadata.META_PREFIX,
            name = CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME,
            value = CryostatServerVersions.V4_2_1)
    public List<List<String>> listJfrEvents(
            @ToolArg(description = "The namespace of the application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application.", required = true)
                    String podName,
            @ToolArg(
                            description = "Filter events after this timestamp (ISO 8601).",
                            required = true)
                    String fromTimestamp,
            @ToolArg(
                            description = "Filter events before this timestamp (ISO 8601).",
                            required = true)
                    String toTimestamp)
            throws IOException, UnsatisfiableRangeException {
        Date from = Date.from(Instant.parse(fromTimestamp));
        Date to = Date.from(Instant.parse(toTimestamp));
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        TargetInfo target = podNameResolver.resolveTarget(namespace, podName);
        ArchivedRecordingDescriptor recording = synthesizer.synthesize(namespace, target, from, to);
        return mcp.listArchivedRecordingEventTypes(target.jvmId(), recording.name());
    }

    @Tool(
            description =
                    "List the field names available on a JFR event type in archived Flight"
                        + " Recordings for the given application that intersect the specified time"
                        + " range. If a single recording covers the range it is used directly; if"
                        + " multiple recordings intersect the range they are synthesized into a"
                        + " single recording first. Use listEventTypesInRecordings first if the"
                        + " event type name is not known.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    @MetaField(
            prefix = CryostatToolMetadata.META_PREFIX,
            name = CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME,
            value = CryostatServerVersions.V4_2_1)
    public List<List<String>> getJfrSchema(
            @ToolArg(description = "The namespace of the application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application.", required = true)
                    String podName,
            @ToolArg(
                            description = "Filter events after this timestamp (ISO 8601).",
                            required = true)
                    String fromTimestamp,
            @ToolArg(
                            description = "Filter events before this timestamp (ISO 8601).",
                            required = true)
                    String toTimestamp,
            @ToolArg(
                            description =
                                    "The JFR event type name, for example"
                                            + " jdk.ObjectAllocationSample.",
                            required = true)
                    String eventType)
            throws IOException, UnsatisfiableRangeException {
        Date from = Date.from(Instant.parse(fromTimestamp));
        Date to = Date.from(Instant.parse(toTimestamp));
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        TargetInfo target = podNameResolver.resolveTarget(namespace, podName);
        ArchivedRecordingDescriptor recording = synthesizer.synthesize(namespace, target, from, to);
        return mcp.listArchivedRecordingEventFields(target.jvmId(), recording.name(), eventType);
    }

    @Tool(
            description =
                    "List event rows for a JFR event type in archived Flight Recordings for the"
                        + " given application that intersect the specified time range. If a single"
                        + " recording covers the range it is used directly; if multiple recordings"
                        + " intersect the range they are synthesized into a single recording first."
                        + " Returns up to limit rows from the selected event type. If columns is"
                        + " omitted or empty, all fields are returned.")
    @MetaField(
            prefix = ToolLevelFilter.TOOL_LEVEL_META_PREFIX,
            name = ToolLevelFilter.TOOL_LEVEL_META_NAME,
            value = "HIGH")
    @MetaField(
            prefix = CryostatToolMetadata.META_PREFIX,
            name = CryostatToolMetadata.MIN_CRYOSTAT_VERSION_META_NAME,
            value = CryostatServerVersions.V4_2_1)
    public List<List<String>> getJfrEvents(
            @ToolArg(description = "The namespace of the application.", required = true)
                    String namespace,
            @ToolArg(description = "The podName of the application.", required = true)
                    String podName,
            @ToolArg(
                            description = "Filter events after this timestamp (ISO 8601).",
                            required = true)
                    String fromTimestamp,
            @ToolArg(
                            description = "Filter events before this timestamp (ISO 8601).",
                            required = true)
                    String toTimestamp,
            @ToolArg(
                            description =
                                    "The JFR event type name, for example"
                                            + " jdk.ObjectAllocationSample.",
                            required = true)
                    String eventType,
            @ToolArg(
                            description =
                                    "Field names to return for each event row. Omit or pass an"
                                            + " empty array to return all fields.")
                    List<String> columns,
            @ToolArg(
                            description = "The maximum number of event rows to return.",
                            defaultValue = "100")
                    int limit)
            throws IOException, UnsatisfiableRangeException {
        Date from = Date.from(Instant.parse(fromTimestamp));
        Date to = Date.from(Instant.parse(toTimestamp));
        CryostatMCP mcp = instanceManager.createInstance(namespace);
        TargetInfo target = podNameResolver.resolveTarget(namespace, podName);
        ArchivedRecordingDescriptor recording = synthesizer.synthesize(namespace, target, from, to);
        return mcp.listArchivedRecordingEvents(
                target.jvmId(), recording.name(), eventType, columns, limit);
    }
}
