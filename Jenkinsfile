pipeline {
    agent {
        kubernetes {
            yaml """
            apiVersion: v1
            kind: Pod
            spec:
              containers:
              - name: redis-cli
                image: redis:latest
                command:
                - cat
                tty: true
            """
        }
    }

    parameters {
        string(name: 'JIRA_URL', description: 'Enter the JIRA URL')
        choice(name: 'ENVIRONMENT', choices: ['stg', 'prd'], description: 'Select the environment')
        choice(name: 'PROJECT', choices: ['platform', 'another-project'], description: 'Select the project folder')
        choice(name: 'KEYDB_FOLDER', choices: ['keydb-payment', 'another-keydb'], description: 'Select the KeyDB folder')
        string(name: 'KEY_NAME', description: 'Enter the Redis key pattern to delete')
    }

    environment {
        GIT_CREDENTIALS_ID = 'github-ajaib'  // Replace with your Jenkins credential ID
        REPO_URL = 'https://github.com/ongkyokta/redis-key.git'  // Replace with your actual repo URL
    }

    stages {
        stage('Extract JIRA Key') {
            steps {
                script {
                    def jiraKey = params.JIRA_URL.tokenize('/').last()
                    env.JIRA_KEY = jiraKey
                }
            }
        }

        stage('Clone Repository') {
            steps {
                container('redis-cli') {
                    deleteDir()  // Clean workspace
                    withCredentials([usernamePassword(credentialsId: env.GIT_CREDENTIALS_ID, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
                        sh 'git clone https://${GIT_USERNAME}:${GIT_PASSWORD}@${REPO_URL} .'
                    }
                }
            }
        }

        stage('Locate Redis Config File') {
            steps {
                script {
                    def jsonFilePath = "${params.ENVIRONMENT}/${params.PROJECT}/${params.KEYDB_FOLDER}/keydb-platform.json"
                    
                    if (!fileExists(jsonFilePath)) {
                        error "❌ Redis config file not found: ${jsonFilePath}"
                    }
                    
                    env.REDIS_CONFIG_FILE = jsonFilePath
                    echo "✅ Redis config file found: ${jsonFilePath}"
                }
            }
        }

        stage('Delete Redis Keys') {
            steps {
                container('redis-cli') {
                    script {
                        def redisInstances = sh(script: "cat ${env.REDIS_CONFIG_FILE}", returnStdout: true).trim().split('\n')

                        for (redis in redisInstances) {
                            def (host, port) = redis.split(":")
                            echo "🔎 Connecting to Redis: ${host}:${port}"
                            
                            def deleteCommand = """
                            redis-cli -h ${host} -p ${port} --scan --pattern '${params.KEY_NAME}' | xargs -r -n 1 redis-cli -h ${host} -p ${port} DEL
                            """
                            
                            sh deleteCommand
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo '✅ Redis key deletion completed successfully!'
        }
        failure {
            echo '❌ Redis key deletion failed.'
        }
    }
}
