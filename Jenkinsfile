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
        choice(name: 'PROJECT', choices: ['platform', 'payment', 'coin'], description: 'Select the project folder')
        string(name: 'KEY_NAME', description: 'Enter the Redis key pattern to delete')
        string(name: 'KEYDB_FOLDER', defaultValue: '', description: 'Auto-detected KeyDB folder based on selected Project')
    }

    stages {
        stage('Clone Repository') {
            steps {
                container('git-cli') {
                    echo "🔄 Cloning repository: https://github.com/ongkyokta/redis-key.git"
                    deleteDir()
                    sh "git clone https://github.com/ongkyokta/redis-key.git ."
                }
            }
        }

        stage('Detect KeyDB Folders') {
            steps {
                container('git-cli') {
                    script {
                        def projectPath = "stg/${params.PROJECT}/"
                        def keydbFolders = sh(script: "ls -d ${projectPath}*/", returnStdout: true).trim().split("\n")
                        
                        if (keydbFolders.isEmpty()) {
                            error "❌ No KeyDB folders found in ${projectPath}."
                        }

                        echo "✅ Detected KeyDB Folders: ${keydbFolders.join(', ')}"
                        
                        // Dynamically update the KEYDB_FOLDER parameter
                        currentBuild.rawBuild.addAction(new ParametersAction(
                            new ChoiceParameterDefinition('KEYDB_FOLDER', keydbFolders, 'Select the KeyDB folder')
                        ))
                    }
                }
            }
        }

        stage('Delete Redis Keys') {
            steps {
                script {
                    echo "Selected KeyDB folder: ${params.KEYDB_FOLDER}"
                    def folderPath = "stg/${params.PROJECT}/${params.KEYDB_FOLDER}"

                    // Find JSON files
                    def jsonFiles = sh(script: "ls ${folderPath}/*.json 2>/dev/null || echo 'NO_FILES'", returnStdout: true).trim().split("\n")

                    if (jsonFiles[0] == "NO_FILES") {
                        error "❌ No JSON files found in ${folderPath}. Please check the repository structure."
                    }

                    echo "✅ Found JSON files: ${jsonFiles.join(', ')}"

                    // Process each JSON file and delete keys
                    for (jsonFile in jsonFiles) {
                        echo "📜 Processing JSON file: ${jsonFile}"

                        def redisInstances = sh(script: "cat ${jsonFile}", returnStdout: true).trim().split("\n")

                        for (redis in redisInstances) {
                            def (host, port) = redis.split(":")
                            echo "🔎 Connecting to Redis: ${host}:${port}"

                            def redisCliPath = "redis-cli"
                            def testConnection = sh(script: "${redisCliPath} -h ${host} -p ${port} PING || echo 'AUTH_REQUIRED'", returnStdout: true).trim()

                            if (testConnection == "PONG") {
                                echo "✅ No authentication needed for Redis: ${host}"
                                sh """
                                ${redisCliPath} -h ${host} -p ${port} --scan --pattern '${params.KEY_NAME}' | xargs -r -n 1 ${redisCliPath} -h ${host} -p ${port} DEL
                                """
                            } else {
                                echo "🔒 Authentication required for Redis: ${host}"

                                def authSuccess = false
                                def redisPassword1 = ""
                                def redisPassword2 = ""

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
                                        sh """
                                        ${redisCliPath} -h ${host} -p ${port} -a '${redisPassword1}' --scan --pattern '${params.KEY_NAME}' | xargs -r -n 1 ${redisCliPath} -h ${host} -p ${port} -a '${redisPassword1}' DEL
                                        """
                                    }
                                }

                                if (!authSuccess && redisPassword2?.trim()) {
                                    def testAuth2 = sh(script: "${redisCliPath} -h ${host} -p ${port} -a '${redisPassword2}' PING || echo 'AUTH_FAILED'", returnStdout: true).trim()
                                    if (testAuth2 == "PONG") {
                                        echo "✅ Authentication successful with redis-pass-2"
                                        authSuccess = true
                                        sh """
                                        ${redisCliPath} -h ${host} -p ${port} -a '${redisPassword2}' --scan --pattern '${params.KEY_NAME}' | xargs -r -n 1 ${redisCliPath} -h ${host} -p ${port} -a '${redisPassword2}' DEL
                                        """
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
