pipeline {
    agent {
        kubernetes {
            yaml """
            apiVersion: v1
            kind: Pod
            spec:
              containers:
              - name: git-cli
                image: alpine/git
                command:
                - cat
                tty: true
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
        REPO_URL = 'https://github.com/ongkyokta/redis-key.git'  
    }

    stages {
        stage('Clone Repository') {
            steps {
                container('git-cli') { // Use Git container
                    deleteDir()
                    echo "🔄 Cloning repository: ${REPO_URL}"
                    sh "git clone ${REPO_URL} ."

                    // Verify that files are cloned
                    echo "📂 Listing cloned files:"
                    sh "ls -la"
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
