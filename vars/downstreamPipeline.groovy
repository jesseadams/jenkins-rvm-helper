import pipeline.helpers.RVMHelper

def call(String deployEnv, Map config) {
  stage("${deployEnv} Environment Deploy") {
    rvm = new RVMHelper()
    rvm.setup(config.rvmVersion, "${config.projectName}-" + deployEnv.toLowerCase())
    env.deployment_id = sh(returnStdout: true, script: 'echo $(date +%Y%m%d%H%M%S)-$(uuidgen | cut -d - -f 1)').trim()
  }
  stage("Promote container") {
    rvm.rake("promote:image:${config.projectContainerName}")
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
    slackSend color: "#14A805", message: "Deployed new ${env.JOB_NAME} container to ${deployEnv}"
  }
}
