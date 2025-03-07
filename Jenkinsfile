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
        REPO_URL = 'https://github.com/ongkyokta/redis-key.git'  // Public GitHub repo (no credentials needed)
    }

    stages {
        stage('Extract JIRA Key') {
            steps {
                script {
                    def jiraKey = params.JIRA_URL.tokenize('/').last()
                    env.JIRA_KEY = jiraKey
                    echo "✅ Extracted JIRA Key: ${env.JIRA_KEY}"
                }
            }
        }

        stage('Clone Repository') {
            steps {
                container('redis-cli') {
                    deleteDir()  // Clean workspace
                    echo "🔄 Cloning repository: ${REPO_URL}"
                    
                    // Clone without credentials (public repo)
                    sh "git clone ${REPO_URL} ."

                    // Verify the repository was cloned
                    echo "📂 Listing cloned files:"
                    sh "ls -la"
                }
            }
        }

        stage('Locate Redis Config File') {
            steps {
                script {
                    def jsonFilePath = "${params.ENVIRONMENT}/${params.PROJECT}/${params.KEYDB_FOLDER}/keydb-platform.json"
                    
                    echo "🔍 Checking if Redis config file exists: ${jsonFilePath}"

                    if (!fileExists(jsonFilePath)) {
                        error "❌ Redis config file not found: ${jsonFilePath}"
                    }

                    env.REDIS_CONFIG_FILE = jsonFilePath
                    echo "✅ Redis config file found: ${jsonFilePath}"

                    // Show contents for debugging
                    echo "📜 Contents of Redis config file:"
                    sh "cat ${jsonFilePath}"
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

                            // Check Redis connectivity before deletion
                            def testConnection = sh(script: "redis-cli -h ${host} -p ${port} PING || echo 'NO_CONNECTION'", returnStdout: true).trim()

                            if (testConnection == "PONG") {
                                echo "✅ Successfully connected to Redis: ${host}:${port}"

                                // Delete keys matching the pattern
                                def deleteCommand = """
                                redis-cli -h ${host} -p ${port} --scan --pattern '${params.KEY_NAME}' | xargs -r -n 1 redis-cli -h ${host} -p ${port} DEL
                                """
                                sh deleteCommand

                                echo "✅ Keys deleted successfully from ${host}:${port}"
                            } else {
                                echo "❌ Unable to connect to Redis: ${host}:${port}. Skipping deletion for this instance."
                            }
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
            echo '❌ Redis key deletion failed. Please check logs for details.'
        }
    }
}
