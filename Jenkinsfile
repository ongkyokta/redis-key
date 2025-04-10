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
                command: [ "cat" ]
                tty: true
              - name: redis-cli
                image: redis:latest
                command: [ "sleep" ]
                args: [ "infinity" ]
                tty: true
            """
        }
    }

    environment {
        REPO_URL = 'https://github.com/ongkyokta/redis-key.git'
        WORKSPACE_PATH = "${env.WORKSPACE}/stg"
        DEFAULT_REDIS_PORT = "6390"
    }

    stages {
        stage('Create Redis Job using Groovy DSL') {
            steps {
                script {
                    // Call the Groovy DSL script that creates the job with parameters
                    jobDsl targets: [
                        'src/job.groovy'  // Path to the Groovy DSL script to create the job
                    ].join('\n'),
                    removedJobAction: 'DELETE',          // Remove deleted jobs
                    removedViewAction: 'DELETE',         // Remove deleted views
                    lookupStrategy: 'SEED_JOB'          // Search for job definitions in the seed job
                }
            }
        }

        stage('Prepare Workspace') {
            steps {
                container('git-cli') {
                    script {
                        echo "🔄 Cloning repository: ${REPO_URL}"
                        deleteDir()
                        sh "git clone ${REPO_URL} ."
                    }
                }
            }
        }

        stage('Extract JIRA Key') {
            steps {
                script {
                    def jiraKey = params.JIRA_URL.tokenize('/').last()
                    env.JIRA_KEY = jiraKey
                    echo "Extracted JIRA Key: ${jiraKey}"
                }
            }
        }

        stage('Locate Redis Config and Ticket Files') {
            steps {
                container('git-cli') {
                    script {
                        def projectPath = "${WORKSPACE_PATH}/${params.PROJECT}/${params.REDIS_FOLDER}"
                        echo "📁 Using project path: ${projectPath}"

                        def configFile = "${projectPath}/config.json"
                        if (!fileExists(configFile)) {
                            error "❌ config.json file not found in ${projectPath}."
                        }
                        echo "✅ Found config.json"

                        def redisConfig = readJSON(file: configFile)
                        if (!redisConfig.containsKey("redis_instances") || redisConfig.redis_instances.isEmpty()) {
                            error "❌ Invalid or missing 'redis_instances' in config.json"
                        }

                        def redisInstances = redisConfig.redis_instances.collect { 
                            it.contains(":") ? it.trim() : it.trim() + ":" + env.DEFAULT_REDIS_PORT 
                        }
                        echo "Detected Redis instances: ${redisInstances.join(', ')}"

                        def ticketFile = "${projectPath}/${env.JIRA_KEY}.json"
                        if (!fileExists(ticketFile)) {
                            error "❌ Ticket file ${env.JIRA_KEY}.json not found."
                        }
                        echo "✅ Found ticket file: ${ticketFile}"

                        def ticketData = readJSON(file: ticketFile)
                        if (!ticketData.containsKey("keys") || ticketData.keys.isEmpty()) {
                            error "❌ Invalid or missing 'keys' in ${env.JIRA_KEY}.json"
                        }

                        def keysToDelete = ticketData.keys.collect { it.trim() }
                        echo "Keys to delete: ${keysToDelete.join(', ')}"

                        env.REDIS_INSTANCES = redisInstances.join(',')
                        env.KEYS_TO_DELETE = keysToDelete.join(',')
                    }
                }
            }
        }

        stage('Delete Redis Keys') {
            steps {
                container('redis-cli') {
                    script {
                        def redisInstances = env.REDIS_INSTANCES.split(',')
                        def keysToDelete = env.KEYS_TO_DELETE.split(',')

                        redisInstances.each { redisInstance ->
                            def (host, port) = redisInstance.split(":")
                            echo "🔎 Connecting to Redis: ${host}:${port}"

                            keysToDelete.each { key ->
                                echo "🔍 Checking if key exists: ${key} on Redis: ${host}:${port}"

                                // Get the list of actual keys matching the pattern
                                def redisCommand = "redis-cli -h ${host} -p ${port} --scan --pattern '${key}'"
                                echo "✅ Running Redis check command: ${redisCommand}"

                                def keyList = sh(script: redisCommand, returnStdout: true).trim()

                                if (!keyList) {
                                    echo "❌ No matching keys found: ${key} in Redis ${host}:${port}"
                                    return
                                }

                                echo "✅ Found keys in Redis ${host}:${port}:"
                                echo keyList

                                echo "🔑 Deleting keys matching: ${key} from Redis instance: ${host}:${port}"

                                def testConnection = sh(
                                    script: "redis-cli -h ${host} -p ${port} PING || echo 'AUTH_REQUIRED'",
                                    returnStdout: true
                                ).trim()

                                if (testConnection == "PONG") {
                                    echo "✅ No authentication needed for Redis: ${host}"
                                    sh """
                                    printf '%s\n' '${keyList}' | while read -r key; do
                                        redis-cli -h ${host} -p ${port} DEL "\$key"
                                    done
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
                                        def testAuth1 = sh(
                                            script: "redis-cli -h ${host} -p ${port} -a '${redisPassword1}' PING || echo 'AUTH_FAILED'",
                                            returnStdout: true
                                        ).trim()
                                        if (testAuth1 == "PONG") {
                                            echo "✅ Authentication successful with redis-pass-1"
                                            authSuccess = true
                                            sh """
                                            echo '${keyList}' | xargs -r -n 1 redis-cli -h ${host} -p ${port} -a '${redisPassword1}' DEL
                                            """
                                        }
                                    }

                                    if (!authSuccess && redisPassword2?.trim()) {
                                        def testAuth2 = sh(
                                            script: "redis-cli -h ${host} -p ${port} -a '${redisPassword2}' PING || echo 'AUTH_FAILED'",
                                            returnStdout: true
                                        ).trim()
                                        if (testAuth2 == "PONG") {
                                            echo "✅ Authentication successful with redis-pass-2"
                                            authSuccess = true
                                            sh """
                                            echo '${keyList}' | xargs -r -n 1 redis-cli -h ${host} -p ${port} -a '${redisPassword2}' DEL
                                            """
                                        }
                                    }

                                    if (!authSuccess) {
                                        echo "❌ Authentication failed for Redis: ${host}. Skipping..."
                                    }
                                }

                                echo "✅ Deleted keys matching: ${key} from Redis: ${host}:${port}"
                            }
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
