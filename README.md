---
title: demo-app
description: Spring Boot sample app that shows its version and Kubernetes pod, node and cluster details, with Docker, Kubernetes, JFrog Artifactory and GitLab CI instructions
---

A Spring Boot (Java 17) sample app for Kubernetes. The web page shows the headline "Java web app running on GDC ag Kubernetes", the app version, the cluster name, and details about the pod and node it runs on.

## Features

* `/` is an HTML page with the message, version, cluster name, CPU/memory requests and limits, and JVM details. It is fully self-contained (inline CSS/SVG/JS, no CDN), so it works in air-gapped clusters. IP addresses and the service account are deliberately not shown.
* `/api/info` returns the same data as JSON, plus the pod name, namespace, pod UID and node name.
* `/actuator/health/liveness` and `/actuator/health/readiness` serve the Kubernetes probes.

Pod and node details come from the [Kubernetes Downward API](https://kubernetes.io/docs/concepts/workloads/pods/downward-api/) as environment variables (see [k8s/deployment.yaml](k8s/deployment.yaml)). When run outside Kubernetes, these fields show `n/a`.

## Configuration

| Env var        | Default                                     | Purpose                                                               |
|----------------|---------------------------------------------|-----------------------------------------------------------------------|
| `APP_VERSION`  | Maven `project.version`                     | Version shown on the page                                             |
| `APP_MESSAGE`  | `Java web app running on GDC ag Kubernetes` | Headline shown on the page                                            |
| `CLUSTER_NAME` | `n/a`                                       | Cluster name shown on the page (Kubernetes doesn't expose it to pods) |

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

Maven builds the jar, and the [Dockerfile](Dockerfile) only packages it. The image build does not run Maven or download dependencies.

```bash
mvn -B clean verify
docker build -t demo-app:1.1.3 --build-arg APP_VERSION=1.1.3 .
docker run --rm -p 8080:8080 demo-app:1.1.3
```

The Dockerfile copies `target/*.jar` into a JRE-only Alpine image (Java 17) and runs it as a non-root user (UID 10001, the same value as `runAsUser` in the deployment). Run the Maven build first, because the image build fails when `target/` has no jar. The `.dockerignore` file lets only `target/*.jar` into the build context.

The only external image the Dockerfile pulls is the `eclipse-temurin` base image. The optional `BASE_REGISTRY` build argument points it at JFrog Artifactory. See [Build and run without internet access](#build-and-run-without-internet-access).

## Deploy to Kubernetes

1. The deployment pulls `ravicmcchee/demo-app:1.1.3` from Docker Hub. To publish your own build, tag and push it, then update `image:` in [k8s/deployment.yaml](k8s/deployment.yaml).

   ```bash
   docker tag demo-app:1.1.3 ravicmcchee/demo-app:1.1.3
   docker push ravicmcchee/demo-app:1.1.3
   ```

   For local clusters, you can instead load a local image with `kind load docker-image demo-app:1.1.3` or `minikube image load demo-app:1.1.3` and set `image:` to `demo-app:1.1.3`.

2. Apply the manifests:

   ```bash
   kubectl apply -f k8s/deployment.yaml -f k8s/service.yaml
   ```

3. Run the smoke test:

   ```bash
   ./scripts/smoke-test.sh demo-app 1.1.3          # bash
   ./scripts/smoke-test.ps1 -Namespace demo-app -ExpectedVersion 1.1.3   # PowerShell 7
   ```

   The smoke test waits for the rollout to finish, port-forwards the service, and checks that the probes respond. It confirms the page shows the app message and detects Kubernetes, the version matches, and the reported pod name, node name, and namespace match real cluster objects.

4. Open the page:

   ```bash
   kubectl -n demo-app port-forward svc/demo-app 8080:80
   ```

   Then browse to <http://localhost:8080>. Refresh a few times: the service load-balances across the 3 replicas, so the pod name (and often the node) changes.

To roll out a new version, bump `APP_VERSION` and the image tag in the deployment. Then re-apply the manifests and run the smoke test with the new expected version.

## Build and run without internet access

In a network with no internet, everything the build and the cluster need comes from JFrog Artifactory. The Artifactory repositories are readable anonymously, so building the image, pulling the base image and pulling the app image onto the cluster need no credentials. The app itself needs no internet at runtime, because the page uses only inline CSS, SVG and JavaScript.

### What must come from Artifactory

| Needed for                                 | Normally comes from                  | Replace with (example repository)           |
|--------------------------------------------|--------------------------------------|---------------------------------------------|
| Maven dependencies and plugins (Maven job) | Maven Central                        | Maven virtual repository, `maven-virtual`   |
| Base image (`eclipse-temurin`)             | Docker Hub                           | Docker virtual repository, `docker-virtual` |
| The `demo-app` image                       | `ravicmcchee/demo-app` on Docker Hub | Docker local repository, `docker-local`     |

The Maven repository must proxy Maven Central, including plugins. The Docker repository must proxy Docker Hub. Replace `artifactory.example.com` and the repository names below with your own.

### Build the jar

Maven downloads dependencies, so it needs the Artifactory Maven repository as a mirror. In the GitLab pipeline the shared Maven template handles this. On your own machine, add a mirror to `~/.m2/settings.xml`:

```xml
<settings>
  <mirrors>
    <mirror>
      <id>artifactory</id>
      <mirrorOf>*</mirrorOf>
      <url>https://artifactory.example.com/artifactory/maven-virtual</url>
    </mirror>
  </mirrors>
</settings>
```

Then run `mvn -B clean verify`. No credentials are needed while the repository allows anonymous reads.

### Build the image

```bash
docker build \
  --build-arg BASE_REGISTRY=artifactory.example.com/docker-virtual/ \
  -t artifactory.example.com/docker-local/demo-app:1.1.3 .
```

`BASE_REGISTRY` is a prefix for the `eclipse-temurin` base image and must end with `/`. When it is empty, the build pulls from Docker Hub. The build needs no other network access, because it only copies the jar from `target/`.

### Push and deploy

```bash
docker login artifactory.example.com
docker push artifactory.example.com/docker-local/demo-app:1.1.3
```

Anonymous access covers reads. Pushing normally still needs credentials for the local repository.

Then change `image:` in [k8s/deployment.yaml](k8s/deployment.yaml) to `artifactory.example.com/docker-local/demo-app:1.1.3` and apply the manifests as usual. The deployment needs no `imagePullSecrets` while pulls are anonymous. The cluster nodes must be able to reach Artifactory.

### Troubleshooting

* `Could not transfer artifact` or `Non-resolvable parent POM`: check the Maven mirror URL, confirm the repository is reachable from the build machine, and confirm anonymous read is enabled.
* TLS or certificate errors: if Artifactory uses an internal certificate authority, add that CA to the trust store of the JDK that runs Maven.
* Base image not found: check that `BASE_REGISTRY` ends with `/`. If Artifactory does not find official images, try adding `library/` to the end of the prefix.
* `COPY failed` for `target/*.jar`: the Maven build did not run before the image build, or the jar was not passed into the Docker build job.
* Nothing downloads even though Artifactory is reachable: if Artifactory has no internet access itself, the artifacts and images must be loaded into its repositories first.

## GitLab CI pipeline

[.gitlab-ci.yml](.gitlab-ci.yml) declares the stage order and includes two shared templates from `avn-rc-group/cicd-templates`.

| Template           | File                                                                      | Covers                                     |
|--------------------|---------------------------------------------------------------------------|--------------------------------------------|
| Java Maven service | `gitlab-platform-templates/workflows/applications/java-maven-service.yml` | Build, test and package the jar with Maven |
| Docker service     | `gitlab-platform-templates/workflows/applications/docker-service.yml`     | Build the image from the jar and push it   |

The stages run in this order: `build`, `test`, `sast-secret-scan`, `package`, `publish`, `docker-build`, `container-security`, `docker-push`, `provenance`, `release-bundle` and `verification`. The `provenance` job is overridden to need `docker-push`, so provenance is generated only after the image is pushed.

The flow is Maven first, then Docker. Maven produces `target/demo-app.jar`, and the Dockerfile copies it. The templates are maintained outside this repository, so check these points against them:

* The Maven jobs use JDK 17 or newer. The app compiles to Java 17 bytecode, so either works.
* The `docker-build` job receives `target/*.jar` from the Maven jobs, as an artifact or through `needs`.
* The `docker-build` job uses the repository root as the build context and the `Dockerfile` in it.
* Extra build arguments the template passes, such as `JAR_FILE`, are fine. Arguments the Dockerfile does not declare only produce a warning.

> [!NOTE]
> The templates live in a private project, so the pipeline was not run here. The jar build, the image build from that jar and the running container were each verified locally.
