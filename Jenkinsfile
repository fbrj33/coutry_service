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

        stage('Deploy Artifact (.jar) to Nexus') {
            steps {
                sh 'mvn deploy -DskipTests'
            }
        }

        stage('Deploy Reports (JaCoCo & Surefire) to Nexus') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'nexus-credentials', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
                    // Upload JaCoCo reports via standard HTTP
                    sh 'mvn wagon:upload -Dwagon.fromDir=target/site/jacoco -Dwagon.url=http://localhost:8081/repository/maven-releases/reports/jacoco/ -Dwagon.username=${NEXUS_USER} -Dwagon.password=${NEXUS_PASS}'
                    
                    // Upload Surefire reports via standard HTTP
                    sh 'mvn wagon:upload -Dwagon.fromDir=target/surefire-reports -Dwagon.url=http://localhost:8081/repository/maven-releases/reports/surefire/ -Dwagon.username=${NEXUS_USER} -Dwagon.password=${NEXUS_PASS}'
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
