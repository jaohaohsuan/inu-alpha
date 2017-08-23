def pod_label = "exp-inu-alpha${env.BUILD_NUMBER}"
podTemplate(
        label: pod_label,
        containers: [
                containerTemplate(name: 'jnlp', image: env.JNLP_SLAVE_IMAGE, args: '${computer.jnlpmac} ${computer.name}', alwaysPullImage: true),
                containerTemplate(name: 'sbt', image: "${env.PRIVATE_REGISTRY}/library/sbt:2.11-fabric8", ttyEnabled: true, command: 'cat', alwaysPullImage: true),
                containerTemplate(name: 'dind', image: 'docker:stable-dind', privileged: true, ttyEnabled: true, command: 'dockerd', args: '--host=unix:///var/run/docker.sock --host=tcp://0.0.0.0:2375 --storage-driver=vfs')
        ],
        volumes: [
                emptyDirVolume(mountPath: '/var/run', memory: false),
                hostPathVolume(mountPath: "/etc/docker/certs.d/${env.PRIVATE_REGISTRY}/ca.crt", hostPath: "/etc/docker/certs.d/${env.PRIVATE_REGISTRY}/ca.crt"),
                hostPathVolume(mountPath: '/home/jenkins/.kube/config', hostPath: '/etc/kubernetes/admin.conf'),
                persistentVolumeClaim(claimName: env.JENKINS_IVY2, mountPath: '/home/jenkins/.ivy2', readOnly: false),
        ]) {

    node(pod_label) {
        ansiColor('xterm') {
            try {
                stage('git clone') {
                    checkout scm
                }

                container('sbt') {
                    stage('build') {
                        sh 'sbt cluster/compile'
                        sh 'sbt cluster/cpJarsForDocker'

                        sh 'sbt frontend/compile'
                        sh 'sbt frontend/cpJarsForDocker'
                    }
                }
            } catch (e) {
                echo "${e}"
                currentBuild.result = FAILURE
            }
            finally {

            }
        }
    }
}