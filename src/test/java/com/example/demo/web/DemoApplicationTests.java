package com.example.demo.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.demo.info.RuntimeInfo;

/**
 * Starts the full application on a random port and exercises it over HTTP,
 * simulating the Downward API environment variables set in k8s/deployment.yaml.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "APP_VERSION=9.9.9-test",
                "KUBERNETES_SERVICE_HOST=10.0.0.1",
                "POD_NAME=demo-app-7d9f-xyz",
                "POD_NAMESPACE=demo",
                "POD_IP=10.244.0.12",
                "NODE_NAME=worker-node-1",
                "HOST_IP=192.168.1.10",
                "POD_SERVICE_ACCOUNT=default"
        })
class DemoApplicationTests {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void homePageShowsHelloWorldVersionAndPodDetails() {
        ResponseEntity<String> response = rest.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("Hello World")
                .contains("v9.9.9-test")
                .contains("Running in Kubernetes")
                .contains("demo-app-7d9f-xyz")
                .contains("worker-node-1")
                .contains("10.244.0.12")
                .contains("192.168.1.10");
    }

    @Test
    void infoApiReturnsJson() {
        ResponseEntity<RuntimeInfo> response = rest.getForEntity("/api/info", RuntimeInfo.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RuntimeInfo info = response.getBody();
        assertThat(info).isNotNull();
        assertThat(info.version()).isEqualTo("9.9.9-test");
        assertThat(info.podNamespace()).isEqualTo("demo");
        assertThat(info.nodeName()).isEqualTo("worker-node-1");
    }

    @Test
    void livenessAndReadinessProbesAreUp() {
        for (String path : new String[] {"/actuator/health/liveness", "/actuator/health/readiness"}) {
            ResponseEntity<String> response = rest.getForEntity(path, String.class);
            assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).as(path).contains("\"status\":\"UP\"");
        }
    }
}
