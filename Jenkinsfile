
pipeline {
    agent any

    options {
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    tools {
        maven 'mymaven'
    }

    triggers {
        githubPush()
    }

    stages {

        stage('Checkout code') {
            steps {
                checkout scm
            }
        }

        stage('Check tools') {
            steps {
                sh '''
                    set -e
                    helm version --short
                    ansible --version | head -1
                    docker --version
                    kubectl version --client
                '''
            }
        }

        stage('Compile, Test & JaCoCo Coverage') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    sh 'mvn clean install jacoco:report'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('SonarQube Analysis') {
    steps {
        withSonarQubeEnv(
            installationName: 'MySonarQubeServer',
            credentialsId: 'sonarqube-token'
        ) {
            sh '''
                mvn org.sonarsource.scanner.maven:sonar-maven-plugin:5.3.0.6276:sonar \
                    -Dsonar.projectKey=country-service \
                    -Dsonar.projectName=country-service \
                    -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
            '''
        }
    }
}

        stage('Quality Gate') {
            steps {
                script {
                    // Give SonarQube a little time to finish processing.
                    sleep(time: 20, unit: 'SECONDS')

                    // Temporary non-blocking gate:
                    // if the webhook is unavailable, continue as UNSTABLE
                    // instead of leaving the entire pipeline stuck.
                    catchError(
                        buildResult: 'UNSTABLE',
                        stageResult: 'UNSTABLE'
                    ) {
                        timeout(time: 2, unit: 'MINUTES') {
                            def qg = waitForQualityGate(
                                abortPipeline: false
                            )

                            echo "Quality Gate status: ${qg.status}"

                            if (qg.status != 'OK') {
                                currentBuild.result = 'UNSTABLE'
                            }
                        }
                    }
                }
            }
        }

        stage('Deploy Artifact (.jar) to Nexus') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    sh 'mvn deploy -DskipTests'
                }
            }
        }

        stage('Deploy Reports (JaCoCo & Surefire) to Nexus') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'nexus-credentials',
                        usernameVariable: 'NEXUS_USER',
                        passwordVariable: 'NEXUS_PASS'
                    )
                ]) {
                    sh '''
                        set -e
                        set +x

                        BASE="http://localhost:8081/repository/jacoco-reports/country-service/build-${BUILD_NUMBER}"

                        upload_dir() {
                            src="$1"
                            dest="$2"

                            if [ ! -d "$src" ]; then
                                echo "Missing report directory: $src"
                                return 1
                            fi

                            find "$src" -type f | while read -r file; do
                                relative_path="${file#"$src"/}"

                                curl -sS -f \
                                  -u "$NEXUS_USER:$NEXUS_PASS" \
                                  --upload-file "$file" \
                                  "$BASE/$dest/$relative_path"
                            done
                        }

                        upload_dir target/site/jacoco jacoco
                        upload_dir target/surefire-reports surefire

                        echo "Reports uploaded to Nexus."
                    '''
                }
            }
        }

        stage('Docker Build, Push & Deploy to Kubernetes (Ansible)') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 'kubeconfig-file',
                        variable: 'KUBECONFIG_FILE'
                    ),
                    string(
                        credentialsId: 'ansible-vault-pass',
                        variable: 'VAULT_PASS'
                    )
                ]) {
                    timeout(time: 10, unit: 'MINUTES') {
                        sh '''
                            set -e
                            set +x

                            VAULT_FILE="$(mktemp)"
                            trap 'rm -f "$VAULT_FILE"' EXIT

                            printf '%s' "$VAULT_PASS" > "$VAULT_FILE"

                            ansible-playbook playbookCICD.yml \
                              --vault-password-file "$VAULT_FILE" \
                              -e image_tag="$BUILD_NUMBER" \
                              -e kubeconfig_path="$KUBECONFIG_FILE"
                        '''
                    }
                }
            }
        }

        stage('Check Kubernetes deployment') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 'kubeconfig-file',
                        variable: 'KUBECONFIG'
                    )
                ]) {
                    sh '''
                        set -e

                        for i in $(seq 1 12); do
                            if curl -sf http://localhost:30008/getcountries; then
                                echo "Application is responding."
                                exit 0
                            fi

                            echo "Waiting for application... attempt $i/12"
                            sleep 5
                        done

                        echo "Application did not become ready."
                        exit 1
                    '''
                }
            }
        }

        stage('Monitoring: kube-state-metrics') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 'kubeconfig-file',
                        variable: 'KUBECONFIG'
                    )
                ]) {
                    timeout(time: 7, unit: 'MINUTES') {
                        sh '''
                            set -e

                            helm repo add prometheus-community \
                              https://prometheus-community.github.io/helm-charts \
                              --force-update

                            helm repo update

                            helm upgrade --install kube-state-metrics \
                              prometheus-community/kube-state-metrics \
                              -n monitoring \
                              --create-namespace \
                              --set service.type=NodePort \
                              --set service.nodePort=30080 \
                              --wait \
                              --timeout 5m
                        '''
                    }
                }

                sh '''
                    set -e

                    for i in $(seq 1 12); do
                        if curl -sf http://localhost:30080/metrics \
                            | grep -q "^kube_pod_status_phase"; then
                            echo "kube-state-metrics is responding."
                            exit 0
                        fi

                        echo "Waiting for kube-state-metrics... attempt $i/12"
                        sleep 5
                    done

                    echo "kube-state-metrics metrics endpoint unavailable."
                    exit 1
                '''
            }
        }

        stage('Monitoring: Prometheus') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 'kubeconfig-file',
                        variable: 'KUBECONFIG'
                    )
                ]) {
                    timeout(time: 7, unit: 'MINUTES') {
                        sh '''
                            set -e

                            helm upgrade --install prometheus \
                              prometheus-community/prometheus \
                              -n monitoring \
                              -f monitoring/prom-values.yaml \
                              --wait \
                              --timeout 5m
                        '''
                    }
                }
            }
        }

        stage('Monitoring: Grafana') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 'kubeconfig-file',
                        variable: 'KUBECONFIG'
                    ),
                    string(
                        credentialsId: 'grafana-admin-password',
                        variable: 'GRAFANA_PASS'
                    )
                ]) {
                    timeout(time: 7, unit: 'MINUTES') {
                        sh '''
                            set -e
                            set +x

                            helm repo add grafana \
                              https://grafana.github.io/helm-charts \
                              --force-update

                            helm repo update

                            helm upgrade --install grafana \
                              grafana/grafana \
                              -n monitoring \
                              -f monitoring/grafana-values.yaml \
                              --set-string adminPassword="$GRAFANA_PASS" \
                              --wait \
                              --timeout 5m
                        '''
                    }
                }
            }
        }

        stage('Verify') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 'kubeconfig-file',
                        variable: 'KUBECONFIG'
                    )
                ]) {
                    sh '''
                        set -e

                        echo "===== Kubernetes resources ====="
                        kubectl get pods -n monitoring
                        kubectl get services -n monitoring

                        echo "===== Helm releases ====="
                        helm list -n monitoring

                        echo "===== Application ====="
                        curl -sf http://localhost:30008/getcountries > /dev/null

                        echo "===== kube-state-metrics ====="
                        curl -sf http://localhost:30080/metrics \
                          | grep -m 1 "^kube_pod_status_phase"

                        echo "Verification completed."
                    '''
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }

        success {
            echo "CI/CD pipeline completed. Image tag: ${env.BUILD_NUMBER}. Grafana: NodePort 30300."
        }

        unstable {
            echo 'Pipeline completed with an UNSTABLE result. Check the Quality Gate.'
        }

        failure {
            echo 'CI/CD pipeline failed. Check the stage logs.'
        }
    }
}
