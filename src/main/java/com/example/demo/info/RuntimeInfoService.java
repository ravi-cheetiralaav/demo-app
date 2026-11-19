package com.example.demo.info;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Collects application and Kubernetes pod details. Pod details are injected as
 * environment variables through the Kubernetes Downward API (see k8s/deployment.yaml).
 */
@Service
public class RuntimeInfoService {

    static final String UNKNOWN = "n/a";

    private final Environment env;
    private final Clock clock;
    private final Instant startedAt;

    @Autowired
    public RuntimeInfoService(Environment env) {
        this(env, Clock.systemUTC());
    }

    RuntimeInfoService(Environment env, Clock clock) {
        this.env = env;
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    public RuntimeInfo current() {
        Runtime runtime = Runtime.getRuntime();
        return new RuntimeInfo(
                env.getProperty("app.message", "Hello World!"),
                env.getProperty("app.version", UNKNOWN),
                env.containsProperty("KUBERNETES_SERVICE_HOST"),
                env.getProperty("HOSTNAME", localHostname()),
                get("POD_NAME"),
                get("POD_NAMESPACE"),
                get("POD_IP"),
                get("POD_UID"),
                get("NODE_NAME"),
                get("HOST_IP"),
                get("POD_SERVICE_ACCOUNT"),
                get("CPU_REQUEST"),
                get("CPU_LIMIT"),
                get("MEMORY_REQUEST"),
                get("MEMORY_LIMIT"),
                System.getProperty("java.version"),
                runtime.availableProcessors(),
                runtime.maxMemory() / (1024 * 1024),
                startedAt,
                clock.instant());
    }

    private String get(String key) {
        String value = env.getProperty(key);
        return (value == null || value.isBlank()) ? UNKNOWN : value;
    }

    private static String localHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return UNKNOWN;
        }
    }
}
