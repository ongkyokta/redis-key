import groovy.json.JsonSlurper

// Define parameters for the Jenkins job
pipelineJob('ongky_test') {
    description('Jenkins Pipeline Job for ongky_test')

    parameters {
        // String parameter for the JIRA URL
        stringParam('JIRA_URL', '', 'Enter the JIRA URL')
        
        // Choice parameter for selecting the project
        choiceParam('PROJECT', ['payment', 'coin', 'platform'], 'Select the project folder')
        
        // Active Choices Reactive Reference Parameter for Redis Folder
        activeChoiceReactiveReferenceParam('REDIS_FOLDER') {
            description('Select Redis Folder based on selected Project')
            filterable()
            choiceType('FORMATTED_HTML')
            groovyScript {
                script("""
import groovy.json.JsonSlurper

// Dynamically use the selected project from the PROJECT parameter
def githubRepoUrl = 'https://api.github.com/repos/ongkyokta/redis-key/contents/'
def tribeName = "stg/\${PROJECT}" // Dynamically use the selected project

def url = githubRepoUrl + tribeName

// Execute the curl command to fetch the directory contents from GitHub API
def command = ['curl', '-L', '-s', url]
def process = command.execute()

// Parse the response from the GitHub API
def jsonResponse = new JsonSlurper().parseText(process.text)

def html = new StringBuilder()
html.append("<select name='value'>")

// Loop through the response and collect the names of subdirectories
jsonResponse.each { item ->
    if (item.type == 'dir') {
        html.append("<option value='\${item.name}'>\${item.name}</option>" + "\\n")
    }
}

html.append("</select>")
return html.toString()
""")
                fallbackScript("""
return ["<option value='error'>Failed to fetch folders</option>"]
""")
            }
        }
    }

    // Add any additional configurations like triggers, log rotation, etc.
}
