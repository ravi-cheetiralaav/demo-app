package com.example.demo.info;

import java.time.Instant;

public record RuntimeInfo(
        String message,
        String version,
        boolean runningInKubernetes,
        String hostname,
        String podName,
        String podNamespace,
        String podIp,
        String podUid,
        String nodeName,
        String hostIp,
        String serviceAccount,
        String cpuRequest,
        String cpuLimit,
        String memoryRequest,
        String memoryLimit,
        String javaVersion,
        int availableProcessors,
        long maxHeapMb,
        Instant startedAt,
        Instant timestamp) {
}
