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
                    // 🔄 Clone Repository to Read Folder Structure
                    container('git-cli') {
                        sh "find ${WORKSPACE} -mindepth 1 -delete || true"
                        sh "git config --global --add safe.directory '${WORKSPACE}'"
                        sh "git clone --no-checkout ${REPO_URL} ."
                        sh "git reset --hard"
                    }

                    // ✅ Get List of Projects
                    def projectList = sh(script: """
                        ls -d stg/*/ 2>/dev/null | awk -F'/' '{print \$2}' | sort -u || echo "NO_PROJECTS"
                    """, returnStdout: true).trim().split("\n").findAll { it != "NO_PROJECTS" }

                    // ✅ Define Active Choices Parameters
                    properties([
                        parameters([
                            string(name: 'JIRA_URL', description: 'Enter the JIRA URL'),
                            choice(name: 'ENVIRONMENT', choices: ['stg', 'prd'], description: 'Select the environment'),

                            [$class: 'ChoiceParameterDefinition', 
                                name: 'PROJECT', 
                                choices: projectList, 
                                description: 'Select the project folder'],

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
                    sh "git config --global --add safe.directory '${WORKSPACE}'"
                    sh "git clone ${REPO_URL} ."

                    echo "📂 Listing cloned files:"
                    sh "ls -R ${params.ENVIRONMENT}/"
                }
            }
        }
    }

    post {
        success {
            echo '✅ Jenkins pipeline ran successfully!'
        }
        failure {
            echo '❌ Pipeline failed. Please check logs.'
        }
    }
}
