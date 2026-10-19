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
                    // Runs SonarQube analysis using explicit plugin identifier
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
                withCredentials([usernamePassword(credentialsId: 'nexus-credentials', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
                    // Double quotes (") allow Groovy to substitute ${NEXUS_USER} and ${NEXUS_PASS}
                    sh "mvn wagon:upload -Dwagon.fromDir=target/site/jacoco -Dwagon.url=http://localhost:8081/repository/maven-releases/reports/jacoco/ -Dwagon.username=${NEXUS_USER} -Dwagon.password=${NEXUS_PASS}"
                    
                    sh "mvn wagon:upload -Dwagon.fromDir=target/surefire-reports -Dwagon.url=http://localhost:8081/repository/maven-releases/reports/surefire/ -Dwagon.username=${NEXUS_USER} -Dwagon.password=${NEXUS_PASS}"
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
