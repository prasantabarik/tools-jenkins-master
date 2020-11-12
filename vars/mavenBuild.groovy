def call(config, buildType) {
    // Run the maven build
    try {
        withEnv(["GIT_TRACE=true"]) {
            withCredentials([string(credentialsId: 'mule-secure-key', variable: 'secure_key')]) {
                configFileProvider([configFile(fileId: config.mavenConfigId, variable: 'MAVEN_SETTINGS')]) {
                    if ("Release" == buildType) {
                        echo "Building Release"
                         sh '''git config --global user.name "Maven Builder"
                               git config --global user.email "maven.builder@ah.nl"'''
                 
                        sh "/usr/share/maven/bin/mvn   -s $MAVEN_SETTINGS -Dusername=git -Dsecure.key=${secure_key} -Dmule.env=local release:clean release:prepare release:perform"
                    } else {
                        echo "Building Snapshot"
                        sh "/usr/share/maven/bin/mvn   -s $MAVEN_SETTINGS -Dsecure.key=${secure_key} -Dmule.env=local clean deploy"
                    }
                }
            }
        }
    } catch (err) {
        echo "in catch block"
        echo "Caught: ${err}"
        currentBuild.result = 'FAILURE'
        throw err
    }
}
