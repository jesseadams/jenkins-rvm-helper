package pipeline.helpers;

def setup(version, project) {
  withEnv(['PROJECT_NAME=' + project, 'RUBY_VERSION=' + version]){
    sh returnStdout: false, script: '''#!/bin/bash --login
      set +x
      source /usr/share/rvm/scripts/rvm && \
        rvm use --install --create ${RUBY_VERSION}@${PROJECT_NAME} && \
        export | egrep -i "(ruby|rvm)" > rvm.env
      set -x
      gem install bundler
      bundle install
    '''
  }
}

def rake(task) {
  sh returnStdout: false, script: """#!/bin/bash --login
    set +x
    . rvm.env
    set -x
    bundle exec rake ${task}
  """
}

return this
