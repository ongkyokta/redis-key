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
                image: redis:alpine
                command:
                - sh
                - -c
                - |
                  apk add --no-cache redis redis-cli;
                  which redis-cli;
                  redis-cli --version;
                  cat
                tty: true
            """
        }
    }

    parameters {
        string(name: 'JIRA_URL', description: 'Enter the JIRA URL')
        choice(name: 'ENVIRONMENT', choices: ['stg', 'prd'], description: 'Select the environment')
        choice(name: 'PROJECT', choices: ['platform', 'payment', 'coin'], description: 'Select the project folder')
        choice(name: 'KEYDB_FOLDER', choices: ['keydb-payment', 'keydb-shared-payment'], description: 'Select the KeyDB folder')
        string(name: 'KEY_NAME', description: 'Enter the Redis key pattern to delete')
    }

    environment {
        REPO_URL = 'https://github.com/ongkyokta/redis-key.git'
    }

    stages {
        stage('Clone Repository') {
            steps {
                container('git-cli') {
                    deleteDir()
                    echo "🔄 Cloning repository: ${REPO_URL}"
                    sh "git clone ${REPO_URL} ."

                    echo "📂 Listing cloned files:"
                    sh "ls -la ${params.ENVIRONMENT}/${params.PROJECT}/${params.KEYDB_FOLDER}"
                }
            }
        }

        stage('Locate & Process Redis Config Files') {
            steps {
                script {
                    def folderPath = "${params.ENVIRONMENT}/${params.PROJECT}/${params.KEYDB_FOLDER}"
                    
                    // 🔥 Find all JSON files inside KEYDB_FOLDER
                    def jsonFiles = sh(script: "ls ${folderPath}/*.json || echo 'NO_FILES'", returnStdout: true).trim().split('\n')

                    if (jsonFiles[0] == "NO_FILES") {
                        error "❌ No JSON files found in ${folderPath}. Please check the repository structure."
                    }

                    echo "✅ Found JSON files: ${jsonFiles.join(', ')}"

                    for (jsonFile in jsonFiles) {
                        echo "📜 Processing JSON file: ${jsonFile}"

                        def redisInstances = sh(script: "cat ${jsonFile}", returnStdout: true).trim().split('\n')

                        for (redis in redisInstances) {
                            def (host, port) = redis.split(":")
                            echo "🔎 Connecting to Redis: ${host}:${port}"

                            // 🔥 Dynamically find redis-cli path
                            def redisCliPath = sh(script: "which redis-cli || echo 'CLI_NOT_FOUND'", returnStdout: true).trim()
                            if (redisCliPath == "CLI_NOT_FOUND") {
                                error "❌ redis-cli not found in container. Failing pipeline."
                            }

                            def testConnection = sh(script: "${redisCliPath} -h ${host} -p ${port} PING || echo 'AUTH_REQUIRED'", returnStdout: true).trim()

                            if (testConnection == "PONG") {
                                echo "✅ No authentication needed for Redis: ${host}"
                                container('redis-cli') {
                                    sh """
                                    ${redisCliPath} -h ${host} -p ${port} --scan --pattern '${params.KEY_NAME}' | xargs -r -n 1 ${redisCliPath} -h ${host} -p ${port} DEL
                                    """
                                }
                            } else {
                                echo "🔒 Authentication required for Redis: ${host}"

                                def authSuccess = false
                                def redisPassword1 = ""
                                def redisPassword2 = ""

                                // 🔥 Retrieve credentials before using them
                                withCredentials([
                                    string(credentialsId: 'redis-pass-1', variable: 'REDIS_PASSWORD_1'),
                                    string(credentialsId: 'redis-pass-2', variable: 'REDIS_PASSWORD_2')
                                ]) {
                                    redisPassword1 = env.REDIS_PASSWORD_1
                                    redisPassword2 = env.REDIS_PASSWORD_2
                                }

                                if (redisPassword1?.trim()) {
                                    def testAuth1 = sh(script: "${redisCliPath} -h ${host} -p ${port} -a '${redisPassword1}' PING || echo 'AUTH_FAILED'", returnStdout: true).trim()
                                    if (testAuth1 == "PONG") {
                                        echo "✅ Authentication successful with redis-pass-1"
                                        authSuccess = true
                                        container('redis-cli') {
                                            sh """
                                            ${redisCliPath} -h ${host} -p ${port} -a '${redisPassword1}' --scan --pattern '${params.KEY_NAME}' | xargs -r -n 1 ${redisCliPath} -h ${host} -p ${port} -a '${redisPassword1}' DEL
                                            """
                                        }
                                    }
                                }

                                if (!authSuccess && redisPassword2?.trim()) {
                                    def testAuth2 = sh(script: "${redisCliPath} -h ${host} -p ${port} -a '${redisPassword2}' PING || echo 'AUTH_FAILED'", returnStdout: true).trim()
                                    if (testAuth2 == "PONG") {
                                        echo "✅ Authentication successful with redis-pass-2"
                                        authSuccess = true
                                        container('redis-cli') {
                                            sh """
                                            ${redisCliPath} -h ${host} -p ${port} -a '${redisPassword2}' --scan --pattern '${params.KEY_NAME}' | xargs -r -n 1 ${redisCliPath} -h ${host} -p ${port} -a '${redisPassword2}' DEL
                                            """
                                        }
                                    }
                                }

                                if (!authSuccess) {
                                    echo "❌ Authentication failed for Redis: ${host}. Skipping..."
                                }
                            }

                            echo "✅ Processed Redis: ${host}:${port} from ${jsonFile}"
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo '✅ Redis key deletion completed successfully for all JSON files!'
        }
        failure {
            echo '❌ Redis key deletion failed. Please check logs.'
        }
    }
}
