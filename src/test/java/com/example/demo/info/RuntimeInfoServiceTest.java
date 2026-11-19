package com.example.demo.info;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RuntimeInfoServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void readsPodDetailsFromDownwardApiEnvironment() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.version", "2.3.4")
                .withProperty("app.message", "Hi")
                .withProperty("KUBERNETES_SERVICE_HOST", "10.0.0.1")
                .withProperty("HOSTNAME", "demo-app-abc")
                .withProperty("POD_NAME", "demo-app-abc")
                .withProperty("POD_NAMESPACE", "demo")
                .withProperty("CLUSTER_NAME", "gdc-ag-cluster-1")
                .withProperty("NODE_NAME", "aks-nodepool1-0")
                .withProperty("CPU_REQUEST", "100")
                .withProperty("CPU_LIMIT", "400");

        RuntimeInfo info = new RuntimeInfoService(env, clock).current();

        assertThat(info.version()).isEqualTo("2.3.4");
        assertThat(info.message()).isEqualTo("Hi");
        assertThat(info.runningInKubernetes()).isTrue();
        assertThat(info.clusterName()).isEqualTo("gdc-ag-cluster-1");
        assertThat(info.podName()).isEqualTo("demo-app-abc");
        assertThat(info.podNamespace()).isEqualTo("demo");
        assertThat(info.nodeName()).isEqualTo("aks-nodepool1-0");
        assertThat(info.cpuLimit()).isEqualTo("400");
        assertThat(info.cpuRequestPercent()).isEqualTo(25);
        assertThat(info.timestamp()).isEqualTo(clock.instant());
    }

    @Test
    void fallsBackToPlaceholdersOutsideKubernetes() {
        MockEnvironment env = new MockEnvironment().withProperty("POD_NAME", "  ");

        RuntimeInfo info = new RuntimeInfoService(env, clock).current();

        assertThat(info.runningInKubernetes()).isFalse();
        assertThat(info.podName()).isEqualTo(RuntimeInfoService.UNKNOWN);
        assertThat(info.nodeName()).isEqualTo(RuntimeInfoService.UNKNOWN);
        assertThat(info.clusterName()).isEqualTo(RuntimeInfoService.UNKNOWN);
        assertThat(info.version()).isEqualTo(RuntimeInfoService.UNKNOWN);
        assertThat(info.memoryRequestPercent()).isZero();
        assertThat(info.hostname()).isNotBlank();
    }
}
