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
    }

    post {
        success {
            echo 'CI/CD pipeline completed successfully.'
        }
        failure {
            echo 'CI/CD pipeline failed. Check the stage logs.'
        }
    }
}
