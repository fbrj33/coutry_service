
pipeline {
    agent any

    tools {
        maven 'mymaven'
    }

    triggers {
        githubPush()
    }

    stages {

        stage('Checkout code') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/fbrj33/coutry_service'
            }
        }

        stage('Build and test (Maven, JUnit)') {
            steps {
                sh 'mvn clean install jacoco:report'
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
                sleep(time: 20, unit: 'SECONDS')
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Deploy using Ansible playbook') {
            steps {
                withKubeConfig(credentialsId: 'kubeconfig-file') {
                    ansiblePlaybook(
                        installation: 'myansible',
                        inventory: 'hosts',
                        playbook: 'playbookCICD.yml',
                        vaultCredentialsId: 'ansible-vault-password',
                        extras: "-e image_tag=${BUILD_NUMBER}",
                        colorized: true
                    )
                }
            }
        }

        stage('Check Kubernetes deployment') {
            steps {
                withKubeConfig(credentialsId: 'kubeconfig-file') {
                    sh '''
                        kubectl rollout status deployment/my-country-service --timeout=120s
                        kubectl get pods -l app=country-service
                        kubectl get endpoints my-country-service
                    '''
                }
            }
        }

        stage('Monitoring: kube-state-metrics (Helm)') {
            steps {
                withKubeConfig(credentialsId: 'kubeconfig-file') {
                    sh '''
                        helm repo add prometheus-community \
                            https://prometheus-community.github.io/helm-charts --force-update

                        helm upgrade --install kube-state-metrics \
                            prometheus-community/kube-state-metrics \
                            --kube-context docker-desktop \
                            --namespace monitoring \
                            --create-namespace \
                            --set service.type=NodePort \
                            --set service.nodePort=30080 \
                            --wait \
                            --timeout 5m

                        kubectl get pods -n monitoring
                        kubectl get svc -n monitoring
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
            echo 'Pipeline executed successfully: app deployed and monitored.'
        }

        failure {
            echo 'Pipeline execution failed!'
        }
    }
}
