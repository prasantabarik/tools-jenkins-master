#!/usr/bin/env groovy

def call(config, buildType) {
    // Run the sonarqube analysis
    withCredentials([string(credentialsId: 'sonarqube-project-key', variable: 'project')]) {
        withCredentials([string(credentialsId: 'sonarqube-login-key', variable: 'login')]) {
            configFileProvider([configFile(fileId: config.mavenConfigId, variable: 'MAVEN_SETTINGS')]) {
                artifactId = readMavenPom().getArtifactId()
                if ("Release" == buildType) {
                    echo "${artifactId}"
                    dir("target/checkout/${artifactId}") {
                        sh "/usr/share/maven/bin/mvn -s $MAVEN_SETTINGS sonar:sonar -Dsonar.host.url=https://sonar.ah.nl -Dsonar.login=${login} -Dsonar.projectKey=${project}-${artifactId}"
                    }
                } else {
                    sh "/usr/share/maven/bin/mvn -s $MAVEN_SETTINGS sonar:sonar -Dsonar.host.url=https://sonar.ah.nl -Dsonar.login=${login} -Dsonar.projectKey=${project}-${artifactId}"
                }
            }
        }
    }
}
