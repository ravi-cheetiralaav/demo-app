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
                .withProperty("POD_IP", "10.244.1.7")
                .withProperty("NODE_NAME", "aks-nodepool1-0")
                .withProperty("HOST_IP", "10.224.0.4")
                .withProperty("CPU_LIMIT", "1");

        RuntimeInfo info = new RuntimeInfoService(env, clock).current();

        assertThat(info.version()).isEqualTo("2.3.4");
        assertThat(info.message()).isEqualTo("Hi");
        assertThat(info.runningInKubernetes()).isTrue();
        assertThat(info.podName()).isEqualTo("demo-app-abc");
        assertThat(info.podNamespace()).isEqualTo("demo");
        assertThat(info.podIp()).isEqualTo("10.244.1.7");
        assertThat(info.nodeName()).isEqualTo("aks-nodepool1-0");
        assertThat(info.hostIp()).isEqualTo("10.224.0.4");
        assertThat(info.cpuLimit()).isEqualTo("1");
        assertThat(info.timestamp()).isEqualTo(clock.instant());
    }

    @Test
    void fallsBackToPlaceholdersOutsideKubernetes() {
        MockEnvironment env = new MockEnvironment().withProperty("POD_NAME", "  ");

        RuntimeInfo info = new RuntimeInfoService(env, clock).current();

        assertThat(info.runningInKubernetes()).isFalse();
        assertThat(info.podName()).isEqualTo(RuntimeInfoService.UNKNOWN);
        assertThat(info.nodeName()).isEqualTo(RuntimeInfoService.UNKNOWN);
        assertThat(info.version()).isEqualTo(RuntimeInfoService.UNKNOWN);
        assertThat(info.hostname()).isNotBlank();
    }
}
