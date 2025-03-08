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

    environment {
        REPO_URL = 'https://github.com/ongkyokta/redis-key.git'
    }

    stages {
        stage('Initialize Parameters') {
            steps {
                script {
                    // Clone Repository to Read Folder Structure
                    container('git-cli') {
                        deleteDir()
                        sh "git clone ${REPO_URL} ."
                    }

                    // 🔥 Get List of Projects from Folder Names
                    def projectList = sh(script: "ls -d stg/*/ prd/*/ | awk -F'/' '{print \$2}' | sort -u", returnStdout: true).trim().split("\n")
                    def projectChoices = projectList.join(",")

                    // 🔥 Set Default Project (First One Found)
                    def defaultProject = projectList[0] ?: "Select a Project"

                    // 🔥 Define Active Choices Properties
                    properties([
                        parameters([
                            string(name: 'JIRA_URL', description: 'Enter the JIRA URL'),
                            choice(name: 'ENVIRONMENT', choices: ['stg', 'prd'], description: 'Select the environment'),

                            // ✅ Dynamically Populate PROJECT Choices
                            choice(name: 'PROJECT', choices: projectList, description: 'Select the project folder'),

                            // ✅ Dynamically Populate KEYDB_FOLDER Based on Selected PROJECT
                            dynamicChoiceParam('KEYDB_FOLDER', "Select the KeyDB folder", '''
                                if (!PROJECT) return ["Select a project first"]
                                return new File(WORKSPACE + "/" + ENVIRONMENT + "/" + PROJECT).list().findAll { it.isDirectory() }
                            '''),

                            string(name: 'KEY_NAME', description: 'Enter the Redis key pattern to delete')
                        ])
                    ])
                }
            }
        }

        stage('Clone Repository') {
            steps {
                container('git-cli') {
                    deleteDir()
                    echo "🔄 Cloning repository: ${REPO_URL}"
                    sh "git clone ${REPO_URL} ."

                    echo "📂 Listing cloned files:"
                    sh "ls -R ${params.ENVIRONMENT}/"
                }
            }
        }

        stage('Locate & Process Redis Config Files') {
            steps {
                script {
                    def folderPath = "${params.ENVIRONMENT}/${params.PROJECT}/${params.KEYDB_FOLDER}"

                    // 🔥 Find all JSON files inside the KeyDB folder
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

                            // 🔥 Run inside Redis container
                            def redisCliPath = "redis-cli"
                            def testConnection = ""
                            container('redis-cli') {
                                testConnection = sh(script: "${redisCliPath} -h ${host} -p ${port} PING || echo 'AUTH_REQUIRED'", returnStdout: true).trim()
                            }

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
                                    def testAuth1 = ""
                                    container('redis-cli') {
                                        testAuth1 = sh(script: "${redisCliPath} -h ${host} -p ${port} -a '${redisPassword1}' PING || echo 'AUTH_FAILED'", returnStdout: true).trim()
                                    }
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
                                    def testAuth2 = ""
                                    container('redis-cli') {
                                        testAuth2 = sh(script: "${redisCliPath} -h ${host} -p ${port} -a '${redisPassword2}' PING || echo 'AUTH_FAILED'", returnStdout: true).trim()
                                    }
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
