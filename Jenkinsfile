@Library('Jenkins-Shared-Lib') _

pipeline {
    environment {
        PROJECT_NAME = 'Trove Server'
        IMAGE_NAME = 'trove-server'
        REGISTRY = 'registry.runicrealms.com'
        REGISTRY_PROJECT = 'build'
    }

    stages {
        stage('Send Discord Notification (Build Start)') {
            agent { label 'any' }
            steps {
                discordNotifyStart(env.PROJECT_NAME, env.GIT_URL, env.GIT_BRANCH, env.GIT_COMMIT.take(7))
            }
        }
        stage('Determine Environment') {
            agent { label 'any' }
            steps {
                script {
                    def branchName = env.GIT_BRANCH.replaceAll(/^origin\//, '').replaceAll(/^refs\/heads\//, '')
                    echo "Using normalized branch name: ${branchName}"

                    if (branchName == 'dev') {
                        env.RUN_MAIN_DEPLOY = 'false'
                    } else if (branchName == 'main') {
                        env.RUN_MAIN_DEPLOY = 'true'
                    } else {
                        error "Unsupported branch: ${branchName}"
                    }
                }
            }
        }
        stage('Build and Push Server Docker Image') {
            agent {
                kubernetes {
                    yaml jenkinsAgent("registry.runicrealms.com/jenkins/agent-go-protoc:latest")
                }
            }
            steps {
                container('jenkins-agent') {
                    script {
                    sh """
                    cd server
                    export PATH="\$PATH:\$(go env GOPATH)/bin"
                    ./gen-proto.sh
                    go mod download
                    go build -buildvcs=false -o trove-server ./cmd
                    """
                    dockerBuildPush("trove-server.Dockerfile", IMAGE_NAME, env.GIT_COMMIT.take(7), env.REGISTRY, env.REGISTRY_PROJECT)
                    }
                }
            }
        }
        stage('Build and Publish Client Artifact') {
            agent {
                kubernetes {
                    yaml jenkinsAgent("registry.runicrealms.com/jenkins/agent-java-21:latest")
                }
            }
            steps {
                container('jenkins-agent') {
                    dir('client') {
                        script {
                            sh "./gradlew clean build --no-daemon"
                            nexusPublish()
                        }
                    }
                }
            }
        }
//         stage('Update Deployment') {
//             steps {
//                 container('jenkins-agent') {
//                     updateManifest('dev', 'Realm-Deployment', 'values.yaml', env.IMAGE_NAME, env.GIT_COMMIT.take(7), 'TODO')
//                 }
//             }
//         }
//         stage('Create PR to Promote Realm-Deployment Dev to Main (Prod Only)') {
//             when {
//                 expression { return env.RUN_MAIN_DEPLOY == 'true' }
//             }
//             steps {
//                 container('jenkins-agent') {
//                     createPR('Trove', 'Realm-Deployment', 'dev', 'main')
//                 }
//             }
//         }
    }

    post {
        success {
            discordNotifySuccess(env.PROJECT_NAME, env.GIT_URL, env.GIT_BRANCH, env.GIT_COMMIT.take(7))
        }
        failure {
            discordNotifyFail(env.PROJECT_NAME, env.GIT_URL, env.GIT_BRANCH, env.GIT_COMMIT.take(7))
        }
    }
}
