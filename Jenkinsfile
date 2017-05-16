podTemplate(
        label: 'storedq',
        containers: [
                containerTemplate(name: 'jnlp', image: 'henryrao/jnlp-slave', args: '${computer.jnlpmac} ${computer.name}', alwaysPullImage: true)
        ],
        volumes: [
                hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock'),
                persistentVolumeClaim(claimName: 'jenkins-ivy2', mountPath: '/home/jenkins/.ivy2', readOnly: false),
                persistentVolumeClaim(claimName: 'helm-repository', mountPath: '/var/helm/repo', readOnly: false)
        ]) {

    node('storedq') {
        ansiColor('xterm') {
            try {
                def image
                stage('checkout') {
                    checkout scm
                }

                docker.image('henryrao/sbt:2.11.8').inside("") {

                    stage('build') {
                        sh 'du -sh ~/.ivy2'
                        sh 'sbt frontend/cpJarsForDocker'
                        sh 'sbt cluster/cpJarsForDocker'
                    }

                    stage('test') {
                        sh 'sbt cluster/test'
                    }

                }

                def doaminimg
                def apiimg

                stage('build image') {
                    parallel domain: {
                        dir('cluster/target/docker') {
                            def tag = sh(returnStdout: true, script: 'cat tag').trim()
                            def mainClass = sh(returnStdout: true, script: 'cat mainClass').trim()
                            doaminimg = doaminImage = docker.build("henryrao/storedq-domain:${tag}", "--pull --build-arg JAVA_MAIN_CLASS=${mainClass} .")
                        }
                    }, 'http-api': {
                        dir('frontend/target/docker') {
                            def tag = sh(returnStdout: true, script: 'cat tag').trim()
                            def mainClass = sh(returnStdout: true, script: 'cat mainClass').trim()
                            apiimg = docker.build("henryrao/storedq-api:${tag}", "--pull --build-arg JAVA_MAIN_CLASS=${mainClass} .")
                        }
                    },failFast: false
                }

                withDockerRegistry(url: 'https://index.docker.io/v1/', credentialsId: 'docker-login') {
                    stage('push image') {
                        parallel domain: {
                            doaminimg.push()
                        }, 'http-api': {
                            apiimg.push()
                        },failFast: false
                    }
                }

//                stage('pack') {
//                    docker.image('henryrao/helm:2.3.1').inside('') { c ->
//                        sh '''
//                        # packaging
//                        helm package --destination /var/helm/repo storedq
//                        helm repo index --url https://grandsys.github.io/helm-repository/ --merge /var/helm/repo/index.yaml /var/helm/repo
//                        '''
//                    }
//
//                    build job: 'helm-repository/master', parameters: [string(name: 'commiter', value: "${env.JOB_NAME}\ncommit: ${sh(script: 'git log --format=%B -n 1', returnStdout: true).trim()}")]
//                }


            } catch (e) {
                echo "${e}"
                currentBuild.result = FAILURE
            }
            finally {

            }
        }
    }
}