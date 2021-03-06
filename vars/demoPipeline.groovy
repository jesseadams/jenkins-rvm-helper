import pipeline.helpers.RVMHelper

def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  properties([pipelineTriggers([pollSCM('* * * * *')])])
  node {
    try {
      stage('Checkout SCM') {
        deleteDir()
        checkout scm

        rvm = new RVMHelper()
        rvm.setup(config.rvmVersion, "${config.projectName}-" + config.projectEnv.toLowerCase())
        env.deployment_id = sh(returnStdout: true, script: 'echo $(date +%Y%m%d%H%M%S)-$(uuidgen | cut -d - -f 1)').trim()
      }

      stage('Static Code Analysis') {
        rvm.rake('static-analysis')
      }

      if(config.projectDsl == 'angular') {
        stage('Unit Tests') {
          sh "npm install --save-dev @angular-devkit/build-angular"
          sh "sed -ie 's|singleRun: false|singleRun: true|g' src/karma.conf.js"
          sh 'npm run test'
        }
      } else {
        stage('Unit Tests') {

        }
      }

      if (config.runSonarScan) {
        stage("Sonar Qube Scan") {
          withSonarQubeEnv('SonarQube') {
            sh "${config.sonarScanCommand}"
          }
        }

        if (config.enforceSonarQuality) {
          stage('Verify Quality Gate') {
            timeout(time: 1, unit: 'HOURS') {
              def qg = waitForQualityGate()
              if (qg.status != 'OK') {
                error "Pipeline aborted due to quality gate failure: ${qg.status}"
              }
            }
          }
        }
      }

      buildArtifact(config.projectDsl, config.projectContainerName)

      stage('Deploy ECR Repository') {
        // Deploy ECR Repository
        rvm.rake("deploy:ecr DEPLOY_ENV=${config.projectEnv}")
      }

      stage("Build ${config.projectDescription} Image") {
        // Build Docker Image
        rvm.rake("build:image:${config.projectContainerName} DEPLOY_ENV=${config.projectEnv}")
      }

      stage("Push ${config.projectDescription} Image") {
        // Push Docker Image
        rvm.rake("push:image:${config.projectContainerName} DEPLOY_ENV=${config.projectEnv}")
      }

      stage("Setup Container Environment Variables") {
        // Setup Container Environment Variables
        rvm.rake("setup:secrets DEPLOY_ENV=${config.projectEnv}")
      }

      stage("Deploy ${config.projectDescription} ALB") {
        // Deploy Application Load Balancer
        rvm.rake("deploy:alb DEPLOY_ENV=${config.projectEnv}")
      }

      stage("Deploy ${config.projectDescription} Container") {
        // Deploy Container to ECS
        rvm.rake("deploy:container DEPLOY_ENV=${config.projectEnv}")
        slackSend color: "#14A805", message: "Deployed new ${env.JOB_NAME} container to DEV"
      }

      if(!config.downstreamEnv.isEmpty()) {
        //Build Downstream Prod Job
        config.downstreamEnv.each { env ->
          downstreamPipeline(env, config)
        }
      }
    }
    catch (exc) {
      slackSend color: "#DD3021", message: "Build Failed - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
      throw exc
    }
  }
}
