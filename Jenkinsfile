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

        // 👇 These choices are generated dynamically by Active Choices
        activeChoiceParam('PROJECT', "Select the project folder", '''
            return new File("${WORKSPACE}/${ENVIRONMENT}").list().findAll { it.isDirectory() }
        ''')

        activeChoiceReactiveParam('KEYDB_FOLDER', "Select the KeyDB folder", '''
            if (!PROJECT) return ["Select a project first"]
            return new File("${WORKSPACE}/${ENVIRONMENT}/${PROJECT}").list().findAll { it.isDirectory() }
        ''')
        
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
