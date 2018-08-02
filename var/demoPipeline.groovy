import pipeline.helpers.RVMHelper

def call(Map params) {
  String projectName = params.projectName
  String projectDescription = params.projectDescription
  String projectEnv = params.projectEnv
  String projectDsl = params.projectDsl
  String projectContainerName = params.projectContainerName
  String rvmVersion = params.rvmVersion ?: '2.5.1'
  boolean runSonarScan = params.runSonarScan ?: false
  String sonarScanCommand = params.sonarScanCommand ?: ''
  boolean enableSlack = params.enableSlack ?: true
  List downstreamEnv = params.downstreamEnv

  properties([pipelineTriggers([pollSCM('* * * * *')])])
  node {
    stage('Checkout SCM') {
      checkout scm

      rvm = new RVMHelper()
      rvm.setup(rvmVersion, "${projectName}-" + projectEnv.toLowerCase())
      env.deployment_id = sh(returnStdout: true, script: 'echo $(date +%Y%m%d%H%M%S)-$(uuidgen | cut -d - -f 1)').trim()
    }

    stage('Static Code Analysis') {
      rvm.rake('static-analysis')
    }

    stage('Unit Tests') {

    }

    if (runSonarScan) {
      stage("Sonar Qube Scan") {
        withSonarQubeEnv('SonarQube') {
          sh "${sonarScanCommand}"
        }
      }
      stage("Verify Quality Gate") {
        timeout(time: 1, unit: 'HOURS') {
          waitForQualityGate abortPipeline: true
        }
      }
    }

    dslContainerBuild(projectDsl, projectContainerName)

    stage('Deploy ECR Repository') {
      // Deploy ECR Repository
      rvm.rake("deploy:ecr DEPLOY_ENV=${projectEnv}")
    }

    stage('Build ${projectDescription} Image') {
      // Build Docker Image
      rvm.rake("build:image:${projectContainerName} DEPLOY_ENV=${projectEnv}")
    }

    stage('Push ${projectDescription} Image') {
      // Push Docker Image
      rvm.rake("push:image:${projectContainerName} DEPLOY_ENV=${projectEnv}")
    }

    stage('Setup Container Environment Variables') {
      // Setup Container Environment Variables
      rvm.rake("setup:secrets DEPLOY_ENV=${projectEnv}")
    }

    stage("Deploy ${projectDescription} ALB") {
      // Deploy Application Load Balancer
      rvm.rake("deploy:alb DEPLOY_ENV=${projectEnv}")
    }

    stage('Deploy ${projectDescription} Container') {
      // Deploy Container to ECS
      rvm.rake("deploy:container DEPLOY_ENV=${projectEnv}")
    }

    if(!downstreamEnv.isEmpty()) {
      //Build Downstream Prod Job
      downstreamEnv.each { env ->
        downstreamPipeline(env, params)
      }
    }
  }
}
