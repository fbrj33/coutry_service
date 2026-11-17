# My Country Service

A Spring Boot microservice built, tested, analysed and deployed through a full CI/CD pipeline (GitHub, Jenkins, SonarQube, Ansible, Docker, Kubernetes) and monitored with Prometheus and Grafana.

## Table of contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Tech stack](#tech-stack)
4. [Project structure](#project-structure)
5. [Run locally](#run-locally)
6. [CI/CD pipeline](#cicd-pipeline)
7. [Deployment on Kubernetes](#deployment-on-kubernetes)
8. [Monitoring](#monitoring)
9. [Troubleshooting](#troubleshooting)
10. [Author](#author)

## Overview

`my-country-service` is a microservice that exposes country data over HTTP on port **8082**. The main goal of the project is the delivery chain around it: every push to `main` is built, tested, analysed for quality, and deployed automatically to a Kubernetes cluster.

## Architecture

```
GitHub ──push──> Jenkins ──> Maven build + JUnit/JaCoCo
                    │
                    ├──> SonarQube analysis + Quality Gate
                    ├──> Ansible playbook ──> Docker image ──> Docker Hub
                    │                    └──> Kubernetes (namespace: jenkins)
                    └──> Helm: kube-state-metrics (optional stage)

Kubernetes ──> kube-state-metrics ──> Prometheus ──> Grafana (dashboard 13332)
```

## Tech stack

| Area | Tool |
|---|---|
| Language / build | Java, Maven |
| Tests / coverage | JUnit, Mockito, JaCoCo |
| Code quality | SonarQube |
| CI/CD | Jenkins (declarative pipeline) |
| Configuration / deploy | Ansible (with Ansible Vault) |
| Containers | Docker, Docker Hub (`farahbenrejab/my-country-service`) |
| Orchestration | Kubernetes |
| Packaging of monitoring | Helm |
| Monitoring | kube-state-metrics, Prometheus, Grafana |

## Project structure

```
.
├── src/                  # Application code and tests (com.countryservice)
├── pom.xml               # Maven build
├── Dockerfile            # Container image
├── docker-compose.yml    # Local run with Docker
├── deployment.yaml       # Kubernetes Deployment
├── service.yaml          # Kubernetes Service (NodePort 30007)
├── playbookCICD.yml      # Ansible deployment playbook
├── vault.yml             # Encrypted secrets (Ansible Vault)
├── hosts                 # Ansible inventory
└── Jenkinsfile           # CI/CD pipeline definition
```

## Run locally

### Prerequisites

- Java and Maven (Maven is installed in Jenkins as `mymaven`)
- Docker (optional)

### With Maven

```bash
mvn clean install
mvn spring-boot:run
```

The service listens on `http://localhost:8082`.

### With Docker

```bash
docker build -t my-country-service .
docker run -p 8082:8082 my-country-service
```

Or with Docker Compose:

```bash
docker compose up --build
```

### Run the tests

```bash
mvn clean test jacoco:report
```

The coverage report is generated in `target/site/jacoco/`.

## CI/CD pipeline

The pipeline is defined in the `Jenkinsfile`. It runs automatically on every push to `main` (GitHub webhook trigger).

| # | Stage | What it does |
|---|---|---|
| 1 | Checkout code | Clones the `main` branch from GitHub |
| 2 | Build and test | `mvn clean install jacoco:report`, publishes JUnit results |
| 3 | SonarQube Analysis + Quality Gate | Sends the analysis and coverage to SonarQube, fails the build if the quality gate fails |
| 4 | Deploy using Ansible playbook | Runs `playbookCICD.yml` with the build number as image tag |
| 5 | Check Kubernetes deployment | Waits for the rollout and prints pods and endpoints |
| 6 | Monitoring: kube-state-metrics (Helm) | Optional, installs the exporter (see below) |

Pipeline settings: no concurrent builds, 25-minute timeout, last 5 builds kept, workspace cleaned after each run.

### Jenkins requirements

- Tools: Maven (`mymaven`), Ansible (`myansible`), Helm, `kubectl`
- Credentials: `sonarqube-token`, `kubeconfig-file`, `ansible-vault-password`
- SonarQube server configured as `MySonarQubeServer`

## Deployment on Kubernetes

| Item | Value |
|---|---|
| Namespace | `jenkins` |
| Deployment | `my-country-service` (3 replicas) |
| Image | `farahbenrejab/my-country-service:<build number>` |
| Container port | `8082` |
| Service type | NodePort `30007` |

Useful commands:

```bash
kubectl -n jenkins get pods -l app=country-service
kubectl -n jenkins rollout status deployment/my-country-service
kubectl -n jenkins get endpoints my-country-service
```

Access the service at `http://localhost:30007`.

## Monitoring

Pod state is collected by **kube-state-metrics**, scraped by **Prometheus**, and displayed in **Grafana** with dashboard **13332**.

### 1. Install kube-state-metrics

In Jenkins, click **Build with Parameters**, tick **INSTALL_MONITORING**, and run the build. The stage runs:

```bash
helm upgrade --install kube-state-metrics \
    oci://ghcr.io/prometheus-community/charts/kube-state-metrics \
    --version 9.0.1 \
    --kube-context docker-desktop \
    --namespace monitoring --create-namespace \
    --set service.type=NodePort \
    --set service.nodePort=30080 \
    --wait --timeout 5m
```

The chart is pulled directly from the OCI registry, so no `helm repo add` is needed (the repo index is large and can time out on slow networks).

Check it:

```bash
kubectl --context docker-desktop get pods,svc -n monitoring
curl http://localhost:30080/metrics | head
```

### 2. Prometheus scrape job

```yaml
scrape_configs:
  - job_name: kube-state-metrics
    static_configs:
      - targets: ["localhost:30080"]
        labels:
          cluster: kind
```

The target must be **UP** at `http://localhost:9090/targets`.

### 3. Grafana

1. Add Prometheus as a data source.
2. Import dashboard **13332** (kube-state-metrics-v2).
3. Select `cluster = kind`, `namespace = jenkins`, then a running pod.

Pod names change at every deployment, so re-select the pod after each pipeline run.

## Troubleshooting

| Problem | Cause and fix |
|---|---|
| kube-state-metrics target is DOWN (`connection refused`) | Nothing listens on port 30080. Check the service with `kubectl get svc -n monitoring`, or run `kubectl port-forward -n monitoring svc/kube-state-metrics 30080:8080` |
| Helm: `context deadline exceeded ... reading body` | Slow network while downloading the repo index. Use the OCI chart (already done in the Jenkinsfile) |
| Jenkins runs an old Jenkinsfile | Check the repo, branch and Script Path in the job configuration, and start a fresh build (not Replay or Restart from Stage) |
| Grafana shows "No data" for a pod | The pod name is stale. Reload the page and select a current pod |
| "Pods restart" and "Evicted" panels show "No data" | Normal when nothing restarted or was evicted |

## Author

**Farah Ben Rejab**
- GitHub: [fbrj33](https://github.com/fbrj33)
- LinkedIn: [farah-ben-rejab](https://www.linkedin.com/in/farah-ben-rejab-aab210368/)
