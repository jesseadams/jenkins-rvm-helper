## Example Usage:

```
@Library('pipeline-helpers')
import pipeline.helpers.RVMHelper

node {
  stage('Checkout SCM') {
    checkout scm

    rvm = new RVMHelper()
    rvm.setup('2.5.1', 'gemset-name')
  }
}
```
