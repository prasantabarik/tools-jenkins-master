#!/usr/bin/groovy

podTemplate(label: 'mulesoft-deployment', 
  serviceAccount: 'jenkins-slave',
  // activeDeadlineSeconds: 30,
  // idleMinutes: 1,
  containers: [
    containerTemplate(
      name: 'jnlp',
      image: 'weeus01prdacraksc.azurecr.io/jenkins_slave_utils:1.1.0',
      args: '${computer.jnlpmac} ${computer.name}',
      ttyEnabled: true
    )
  ],
  volumes: [
        hostPathVolume(mountPath: '/var/run/docker.sock', 
        hostPath: '/var/run/docker.sock')
  ]
)
{
  node ('mulesoft-deployment') {
    //pipeline / domain defined.
    //def helmChartRegistry = 'weeus01prdacraksc.azurecr.io/helm/mulesoft-runtime:0.1.0'
    //def helmChartRepository = 'helm/mulesoft-runtime'    //use in case of AKS registry usage
    def helmChartRepository = "${env.WORKSPACE}/ah-mulesoft-runtime-chart/"
    def gitHelmChart = "https://github.com/RoyalAholdDelhaize/ah-mulesoft-runtime-chart.git"
    def configApiURL = "https://config-management-ahit-integration.ocp.ah.nl/config-management/v1/configuration-items"

    def localPath = "${env.WORKSPACE}/Helmfile"
    def snowPath = "${env.WORKSPACE}/SNOWfile.json"
    def helmfileContent = '';
    def snowfileContent = '';
    def IngressPath = "${healthcheckURL}".split('/healthcheck')[0]

    stage('Fetch Config files from project') {
      if("${scmPath}" != "") {
        git url: scmPath,
            credentialsId: 'github_user',
            branch: 'master'
        
        echo "SNOWPath: ${snowPath}"
        snowfileContent = readFile("${snowPath}")
      } else {
        echo "No scmPath for a project with a Helmfile given, going with defaults."
      }
      
      echo "LocalPath: ${localPath}"
      sh "test -f ${localPath} || touch ${localPath}"
      helmfileContent = readFile("${localPath}")
    }

    stage('CI Creation'){
        if (NodeName.contains("-dev") || NodeName.contains("-tst") || NodeName.contains("-acc") || ("${purge}" == "true") ) {
            echo "CI creation not required"
        }
        else {
            def tag = "${artifactId}-${artifactVersion}"
            def url ="${scmPath}"
            echo "SCM URL: ${url} and Tag: ${tag}"
          
            def status = ""

            checkout scm: [$class: 'GitSCM', userRemoteConfigs: [[url: "${url}", credentialsId: 'github_user']], branches: [[name: "${tag}"]]], poll: false
            withCredentials([usernamePassword(credentialsId: 'config-management-api', passwordVariable: 'client_secret', usernameVariable: 'client_id')]) {
              status = updateCMDB("${client_id}","${client_secret}","${configApiURL}", "${snowfileContent}")
            }
            cleanWs() //clean up workspace
            if (status.equals("FAILURE")){ 
              currentBuild.result = 'UNSTABLE'   
            }
            if (status.equals("ERROR")){ 
              timeout(time: 5, unit: 'MINUTES'){
                input id: 'ProceedWithDeploy', message: 'CI creation failed! would you like to proceed with deployment', ok: 'Yes', submitterParameter: 'approver'
                currentBuild.result = 'UNSTABLE' //If approved, mark build as unstable
              }
            }
        }
    }

    stage('Fetch Helm Chart') {
      git url: "${gitHelmChart}",
          credentialsId: 'github_user',
          branch: 'master'
    }

    stage ('Deploy with Helm') { 
      container('jnlp') {
        

        //write Helmfile
        writeFile file: "${localPath}",
                  text: "${helmfileContent}"
        sh "test -f ${localPath} || touch ${localPath}"

        //deploy the artifact with the helm chart
        withCredentials([string(credentialsId: "${environment}.url", variable: "serverUrl")]) {
          withKubeConfig([credentialsId: "${environment}.token", serverUrl: "${serverUrl}"]) {
            if("${purge}" == "true") {
              echo "Purging service ${ServiceName}"
              sh "helm uninstall ${ServiceName} --namespace ${Namespace} "
            } else {
              echo "Deploying service ${ServiceName} in ${environment} environment into namespace ${Namespace} on node ${NodeName}"
              sh "helm upgrade --install -f Helmfile --set filebeat.enable=true --set maven.groupId=${groupId} --set maven.artifactId=${artifactId} --set maven.artifactVersion=${artifactVersion} --set replicaCount=${replicaCount} --set ingress.hosts[0].host=${NodeName} --set ingress.tls[0].hosts[0]=${NodeName} --set ingress.hosts[0].paths[0]=${IngressPath} --namespace ${Namespace} ${ServiceName} ${helmChartRepository}"
            }
          }
        }
      }
    } // end Stage
  }
}
