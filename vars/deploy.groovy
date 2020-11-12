def call(config,deploy,environment,nodeName,replicaCount,namespace) {
	
  try {

        if ("true" == "${deploy}") {
            def groupId = readMavenPom().getGroupId()
            def artifactId = readMavenPom().getArtifactId()
            def artifactVersion = readMavenPom().getVersion()
            def helmChartRepository = "${env.WORKSPACE}/ah-mulesoft-runtime-chart/"
            def gitHelmChart = "https://github.com/RoyalAholdDelhaize/ah-mulesoft-runtime-chart.git"
            def localPath =  "${env.WORKSPACE}/Helmfile"
            def IngressPath = "${config.healthCheckUrl}".split('/healthcheck')[0]
            def helmfileContent = ''

            if ("Release" == buildType) {
                artifactVersion = sh(returnStdout: true, script: "git describe --abbrev=0 | sed 's;.*-;;'").trim()
            }
            echo "Deploying API:"
            echo "  ServiceName:      ${config.serviceName}"
            echo "  GroupId:          ${groupId}"
            echo "  ArtifactId:       ${artifactId}"
            echo "  ArtifactVersion:  ${artifactVersion}"
            echo "  NumberOfReplicas: ${numberOfReplicas}"
            echo "  IngressPath:      ${IngressPath}"
         

		git url: "${gitHelmChart}", credentialsId: "${config.scmCredentials}", branch: 'master'
           
	    //read Helmfile
            echo "LocalPath: ${localPath}"
            sh "test -f ${localPath} || touch ${localPath}"
            helmfileContent = readFile("${localPath}")

            //write Helmfile
            writeFile file: "${localPath}",text: "${helmfileContent}"
            sh "test -f ${localPath} || touch ${localPath}"

            //deploy the artifact with the helm chart
            withCredentials([string(credentialsId: "${environment}.url", variable: "serverUrl")]) {
                withKubeConfig([credentialsId: "${environment}.token", serverUrl: "${serverUrl}"]) {
                    echo "Deploying service ${config.serviceName} in ${environment} environment into namespace ${namespace} on node ${nodeName}"
                   sh "ls ${env.WORKSPACE}"
                    sh "helm upgrade --install --debug -f Helmfile --set filebeat.enable=true --set maven.groupId=${groupId} --set maven.artifactId=${artifactId} --set maven.artifactVersion=${artifactVersion} --set replicaCount=${replicaCount} --set ingress.hosts[0].host=${nodeName} --set ingress.tls[0].hosts[0]=${nodeName} --set ingress.hosts[0].paths[0]=${IngressPath} --namespace ${namespace} ${config.serviceName} ${helmChartRepository}"
                    sh "kubectl -n ${namespace} get pods | grep ${config.serviceName}"
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
