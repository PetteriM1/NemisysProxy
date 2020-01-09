pipeline {
    agent any
    tools {
        maven 'Maven 3'
        jdk 'Java 8'
    }
    options {
        buildDiscarder(logRotator(artifactNumToKeepStr: '5'))
    }
    stages {
        stage ('Build') {
            steps {
                sh 'mvn clean package'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/Nemisys-PM1E.jar', fingerprint: true
                }
            }
        }

        stage ('Deploy') {
            steps {
                sh 'mvn clean package'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/Nemisys-PM1E.jar', fingerprint: true
                }
            }
        }
    }

    post {
        always {
            deleteDir()
        }
    }
}
