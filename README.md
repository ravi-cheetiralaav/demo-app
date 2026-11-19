# demo-app

A Spring Boot (Java 21) sample app for Kubernetes. The web page shows a Hello World message, the app version, and details about the pod and node it runs on.

## Features

* `/` is an HTML page with the message, version, pod name, namespace, pod IP, pod UID, service account, node name, node IP, CPU/memory requests and limits, and JVM details.
* `/api/info` returns the same data as JSON.
* `/actuator/health/liveness` and `/actuator/health/readiness` serve the Kubernetes probes.

Pod and node details come from the [Kubernetes Downward API](https://kubernetes.io/docs/concepts/workloads/pods/downward-api/) as environment variables (see [k8s/deployment.yaml](k8s/deployment.yaml)). When run outside Kubernetes, these fields show `n/a`.

## Configuration

| Env var       | Default                        | Purpose                     |
|---------------|--------------------------------|-----------------------------|
| `APP_VERSION` | Maven `project.version`        | Version shown on the page   |
| `APP_MESSAGE` | `Hello World from Kubernetes!` | Greeting shown on the page  |

## Build and test locally

```bash
mvn verify                              # compile, run unit and integration tests, package
java -jar target/demo-app.jar           # http://localhost:8080
```

Automated tests:

* [RuntimeInfoServiceTest](src/test/java/com/example/demo/info/RuntimeInfoServiceTest.java) contains unit tests for reading Downward API values and the fallbacks.
* [DemoApplicationTests](src/test/java/com/example/demo/web/DemoApplicationTests.java) starts the full app on a random port with simulated pod env vars, then checks the HTML page, the JSON API, and the health probes.
* [scripts/smoke-test.sh](scripts/smoke-test.sh) and [scripts/smoke-test.ps1](scripts/smoke-test.ps1) are post-deployment smoke tests that run against the live cluster.

## Docker

```bash
docker build -t demo-app:1.0.0 --build-arg APP_VERSION=1.0.0 .
docker run --rm -p 8080:8080 demo-app:1.0.0
```

The image uses a multi-stage build (tests run during the build), a layered Spring Boot jar, a JRE-only Alpine base, and a non-root user (UID 10001).

## Deploy to Kubernetes

1. Push the image to a registry the cluster can pull from, then update `image:` in [k8s/deployment.yaml](k8s/deployment.yaml).

   ```bash
   docker tag demo-app:1.0.0 <registry>/demo-app:1.0.0
   docker push <registry>/demo-app:1.0.0
   ```

   For local clusters, load the image directly with `kind load docker-image demo-app:1.0.0` or `minikube image load demo-app:1.0.0`.

2. Apply the manifests:

   ```bash
   kubectl apply -f k8s/deployment.yaml -f k8s/service.yaml
   ```

3. Run the smoke test:

   ```bash
   ./scripts/smoke-test.sh demo 1.0.0          # bash
   ./scripts/smoke-test.ps1 -Namespace demo -ExpectedVersion 1.0.0   # PowerShell 7
   ```

   The smoke test waits for the rollout to finish, port-forwards the service, and checks that the probes respond. It confirms the page shows Hello World and detects Kubernetes, the version matches, and the reported pod name, node name, and namespace match real cluster objects.

4. Open the page:

   ```bash
   kubectl -n demo port-forward svc/demo-app 8080:80
   ```

   Then browse to <http://localhost:8080>. Refresh a few times: the service load-balances across the 3 replicas, so the pod name (and often the node) changes.

To roll out a new version, bump `APP_VERSION` and the image tag in the deployment. Then re-apply the manifests and run the smoke test with the new expected version.
