import pipeline.helpers.RVMHelper

def call(String projectDsl, String projectContainerName) {
  if(projectDsl == 'angular') {
    stage('Build Angular') {
      sh "ng build --output-path containers/${projectContainerName}/dist/ --prod"
    }
  } else if (projectDsl == 'java') {
    stage('Build WAR') {
      sh "mvn -B -Dmaven.test.failure.ignore=true -DskipTests=true clean package"
      sh "cp target/*.war containers/${projectContainerName}"
      sh "ls -al containers/${projectContainerName}"
    }
  }
}
