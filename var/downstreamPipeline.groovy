import pipeline.helpers.RVMHelper

def call(Map params) {
  String projectName = params.projectName
  String projectDescription = params.projectDescription
  String projectEnv = params.projectEnv
  String rvmVersion = params.rvmVersion ?: '2.5.1'

  node {
    stage('Checkout SCM') {
      checkout scm
      rvm = new RVMHelper()
      rvm.setup(rvmVersion, "${projectName}-" + projectEnv.toLowerCase())
      env.deployment_id = sh(returnStdout: true, script: 'echo $(date +%Y%m%d%H%M%S)-$(uuidgen | cut -d - -f 1)').trim()
    }

    stage("Setup ${projectEnv} Container Environment Variables") {
      // Setup Container Environment Variables
      rvm.rake("setup:secrets DEPLOY_ENV=${projectEnv}")
    }

    stage("Deploy ${projectEnv} ${projectDescription} ALB") {
      // Deploy Application Load Balancer
      rvm.rake("deploy:alb DEPLOY_ENV=${projectEnv}")
    }

    stage("Deploy ${projectEnv} ${projectDescription} Container") {
      // Deploy Container to ECS
      rvm.rake("deploy:container DEPLOY_ENV=${projectEnv}")
    }
  }
}
