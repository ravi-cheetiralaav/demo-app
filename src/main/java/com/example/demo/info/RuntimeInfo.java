package com.example.demo.info;

import java.time.Instant;

public record RuntimeInfo(
        String message,
        String version,
        boolean runningInKubernetes,
        String clusterName,
        String hostname,
        String podName,
        String podNamespace,
        String podUid,
        String nodeName,
        String cpuRequest,
        String cpuLimit,
        String memoryRequest,
        String memoryLimit,
        String javaVersion,
        int availableProcessors,
        long maxHeapMb,
        Instant startedAt,
        Instant timestamp) {

    /** Request as a percentage of limit, for the page's progress bars; 0 when either value is unknown. */
    public int cpuRequestPercent() {
        return percent(cpuRequest, cpuLimit);
    }

    public int memoryRequestPercent() {
        return percent(memoryRequest, memoryLimit);
    }

    private static int percent(String request, String limit) {
        try {
            double r = Double.parseDouble(request);
            double l = Double.parseDouble(limit);
            return l <= 0 ? 0 : (int) Math.min(100, Math.round(r * 100 / l));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
