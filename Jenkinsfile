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

                            // 🔥 Determine if Redis requires authentication
                            def credentialId = ""
                            if (host == "10.199.2.31") { credentialId = "redis_pass_stg_1" }
                            else if (host == "10.199.2.32") { credentialId = "redis_pass_stg_2" }
                            else if (host == "10.199.2.33") { credentialId = "redis_pass_stg_3" }

                            if (credentialId) {
                                echo "🔒 Using authentication for Redis: ${host}"
                                withCredentials([string(credentialsId: credentialId, variable: 'REDIS_PASSWORD')]) {
                                    def deleteCommand = """
                                    redis-cli -h ${host} -p ${port} -a '${REDIS_PASSWORD}' --scan --pattern '${params.KEY_NAME}' | xargs -r -n 1 redis-cli -h ${host} -p ${port} -a '${REDIS_PASSWORD}' DEL
                                    """
                                    container('redis-cli') {
                                        sh deleteCommand
                                    }
                                }
                            } else {
                                echo "🔓 No authentication needed for Redis: ${host}"
                                def deleteCommand = """
                                redis-cli -h ${host} -p ${port} --scan --pattern '${params.KEY_NAME}' | xargs -r -n 1 redis-cli -h ${host} -p ${port} DEL
                                """
                                container('redis-cli') {
                                    sh deleteCommand
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
