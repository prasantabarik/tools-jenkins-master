def call(body) {

    def config = [: ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    pipeline {
        agent {
            label 'jenkins-slave-utility'
        }
        parameters {
            choice choices: 'Snapshot\nRelease', description: '', name: 'BuildType'
            booleanParam defaultValue: false, description: '', name: 'Deploy'
            choice choices: 'development\ntest', description: '', name: 'Environment'
            choice choices: 'services-local-dev.ah.nl\ntechnology-dep-tst.ah.nl', description: '', name: 'NodeName'
            choice choices: 'mulesoft', description: '', name: 'Namespace'
            choice choices: '1\n2\n3\n4', description: '', name: 'NumberOfReplicas'
        }

        stages {
            stage('Initialize') {
                steps {
                    script {
                        // skip first build to load parameters from Jenkinsfile.
                        if (!currentBuild.getPreviousBuild()) {
                            echo "Refresh parameters only..."
                            currentBuild.result = 'UNSTABLE'
                        }
                    }
                }
            }
            stage('Checkout Source Code') {
                when {
                    expression {
                        currentBuild.result != 'UNSTABLE'
                    }
                }
                steps {
                    // Get source code from the bitbucket repository
                    git branch: env.BRANCH_NAME, credentialsId: config.scmCredentials, url: config.scmUrl
                    echo "scm URL ${config.scmUrl}"
                }
            }
            stage('Build') {
                when {
                    expression {
                        currentBuild.result != 'UNSTABLE'
                    }
                }
                steps {
                    // Run the maven build
                    container('jenkins-slave-utility') {
                        mavenBuild(config, env.BuildType)
                    }

                }
            }
            stage('Code Analysis') {
                when {
                    expression {
                        currentBuild.result != 'UNSTABLE'
                    }
                }
                steps {

                    // Run the sonarqube analysis
                    container('jenkins-slave-utility') {
                        sonarqubeAnalysis(config, env.BuildType)
                    }
                }
            }
            stage('Deploy') {
                when {
                    allOf {
                        expression {
                            currentBuild.result != 'UNSTABLE'
                        }
                        expression {
                            "${env.Deploy}" == 'true'
                        }

                    }
                }
                steps {

                    // Deploy Artifact to AKS
                    container('jenkins-slave-utility') {
                        deploy(config, env.Deploy, env.Environment, env.NodeName, env.NumberOfReplicas, env.Namespace)
                    }

                }

            }
        }

        post {
            always {
                echo "done."
                cleanWs() /* clean up our workspace */
            }
        }
    }
}
