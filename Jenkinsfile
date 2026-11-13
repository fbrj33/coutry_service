pipeline {
    agent any

    tools {
        maven 'mymaven'
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
                    helm version --short
                    ansible --version | head -1
                    docker --version
                '''
            }
        }

        stage('Compile, Test & JaCoCo Coverage') {
            steps {
                // Compiles code, runs test suite, and generates JaCoCo coverage report
                sh 'mvn clean test jacoco:report'
            }
            post {
                always {
                    // Publishes test results within Jenkins UI
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv(installationName: 'MySonarQubeServer', credentialsId: 'sonarqube-token') {
                    sh 'mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectKey=country-service -Dsonar.projectName=country-service'
                }
            }
        }

        stage('Quality Gate') {
            steps {
                // Requires a SonarQube webhook to http://<jenkins>:8080/sonarqube-webhook/
                // Delete this stage if you have not configured the webhook
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Deploy Artifact (.jar) to Nexus') {
            steps {
                // Deploys main artifact using credentials configured in /var/lib/jenkins/.m2/settings.xml
                sh 'mvn deploy -DskipTests'
            }
        }

        stage('Deploy Reports (JaCoCo & Surefire) to Nexus') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-credentials',
                                                  usernameVariable: 'NEXUS_USER',
                                                  passwordVariable: 'NEXUS_PASS')]) {
                    // Triple single quotes: Groovy does not interpolate, the shell reads the variables
                    sh '''
                        set +x
                        BASE="http://localhost:8081/repository/jacoco-reports/country-service/build-${BUILD_NUMBER}"

                        upload_dir() {
                            src="$1"
                            dest="$2"
                            list="$(mktemp)"
                            (cd "$src" && find . -type f | sed 's|^\\./||') > "$list"
                            while read -r f; do
                                curl -sS -f -u "$NEXUS_USER:$NEXUS_PASS" \
                                     --upload-file "$src/$f" "$BASE/$dest/$f"
                            done < "$list"
                            rm -f "$list"
                        }

                        upload_dir target/site/jacoco jacoco
                        upload_dir target/surefire-reports surefire
                    '''
                }
            }
        }

        stage('Docker Build, Push & Deploy to Kubernetes (Ansible)') {
            steps {
                withCredentials([
                    file(credentialsId: 'kubeconfig-file', variable: 'KUBECONFIG_FILE'),
                    string(credentialsId: 'ansible-vault-pass', variable: 'VAULT_PASS')
                ]) {
                    sh '''
                        set +x
                        VAULT_FILE="$(mktemp)"
                        echo "$VAULT_PASS" > "$VAULT_FILE"
                        ansible-playbook playbookCICD.yml \
                          --vault-password-file "$VAULT_FILE" \
                          -e image_tag=${BUILD_NUMBER} \
                          -e kubeconfig_path="$KUBECONFIG_FILE"
                        rm -f "$VAULT_FILE"
                    '''
                }
            }
        }

        stage('Monitoring: kube-state-metrics') {
            steps {
                withCredentials([file(credentialsId: 'kubeconfig-file', variable: 'KUBECONFIG')]) {
                    sh '''
                        helm repo add prometheus-community https://prometheus-community.github.io/helm-charts --force-update
                        helm repo update
                        helm upgrade --install kube-state-metrics prometheus-community/kube-state-metrics \
                          -n monitoring --create-namespace --wait --timeout 5m
                    '''
                }
            }
        }

        stage('Monitoring: Prometheus') {
            steps {
                withCredentials([file(credentialsId: 'kubeconfig-file', variable: 'KUBECONFIG')]) {
                    sh '''
                        helm upgrade --install prometheus prometheus-community/prometheus \
                          -n monitoring -f monitoring/prom-values.yaml --wait --timeout 5m
                    '''
                }
            }
        }

        stage('Monitoring: Grafana') {
            steps {
                withCredentials([
                    file(credentialsId: 'kubeconfig-file', variable: 'KUBECONFIG'),
                    string(credentialsId: 'grafana-admin-password', variable: 'GRAFANA_PASS')
                ]) {
                    sh '''
                        set +x
                        helm repo add grafana https://grafana.github.io/helm-charts --force-update
                        helm repo update
                        helm upgrade --install grafana grafana/grafana \
                          -n monitoring -f monitoring/grafana-values.yaml \
                          --set-string adminPassword="$GRAFANA_PASS" --wait --timeout 5m
                    '''
                }
            }
        }

        stage('Verify') {
            steps {
                withCredentials([file(credentialsId: 'kubeconfig-file', variable: 'KUBECONFIG')]) {
                    sh '''
                        helm list -n monitoring
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "CI/CD pipeline completed. Image tag: ${env.BUILD_NUMBER}. Grafana: NodePort 30300."
        }
        failure {
            echo 'CI/CD pipeline failed. Check the stage logs.'
        }
    }
}
