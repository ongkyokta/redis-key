pipeline {
    agent any

    stages {
        stage('Create Redis Job using Groovy DSL') {
            steps {
                script {
                    // Call the Groovy DSL script that creates the job with parameters
                    jobDsl targets: [
                        'src/job.groovy'  // Update the path to your Groovy DSL script
                    ].join('\n'),
                    removedJobAction: 'DELETE',          // Remove deleted jobs
                    removedViewAction: 'DELETE',         // Remove deleted views
                    lookupStrategy: 'SEED_JOB'          // Search for job definitions in the seed job
                }
            }
        }
    }
}