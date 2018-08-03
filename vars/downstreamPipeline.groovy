import pipeline.helpers.RVMHelper

def call(String deployEnv, Map config) {
  node {
    try {
      stage('Checkout SCM') {
        checkout scm
        rvm = new RVMHelper()
        rvm.setup(config.rvmVersion, "${config.projectName}-" + deployEnv.toLowerCase())
        env.deployment_id = sh(returnStdout: true, script: 'echo $(date +%Y%m%d%H%M%S)-$(uuidgen | cut -d - -f 1)').trim()
      }

      stage("Setup ${deployEnv} Container Environment Variables") {
        // Setup Container Environment Variables
        rvm.rake("setup:secrets DEPLOY_ENV=${deployEnv}")
      }

      stage("Deploy ${deployEnv} ${config.projectDescription} ALB") {
        // Deploy Application Load Balancer
        rvm.rake("deploy:alb DEPLOY_ENV=${deployEnv}")
      }

      stage("Deploy ${deployEnv} ${config.projectDescription} Container") {
        // Deploy Container to ECS
        rvm.rake("deploy:container DEPLOY_ENV=${deployEnv}")
      }
    }
    catch (exc) {
      slackSend color: "#DD3021", message: "Build Failed - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
    }
  }
}
