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

        stage('Compile, Test & JaCoCo Coverage') {
            steps {
                sh 'mvn clean test jacoco:report'
            }
            post {
                always {
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
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Deploy Artifact (.jar) to Nexus') {
            steps {
                sh 'mvn deploy -DskipTests'
            }
        }

        stage('Deploy Reports (JaCoCo & Surefire) to Nexus') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-credentials',
                                                  usernameVariable: 'NEXUS_USER',
                                                  passwordVariable: 'NEXUS_PASS')]) {
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
    }

    post {
        success {
            echo "CI/CD pipeline completed. Image tag: ${env.BUILD_NUMBER}"
        }
        failure {
            echo 'CI/CD pipeline failed. Check the stage logs.'
        }
    }
}
