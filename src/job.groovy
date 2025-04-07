pipelineJob('ongky_test') {
    description('Jenkins Pipeline Job for ongky_test')

    parameters {
        // String parameter for the JIRA URL
        stringParam('JIRA_URL', '', 'Enter the JIRA URL')

        // Choice parameter for selecting the project
        choiceParam('PROJECT', ['payment', 'coin', 'platform', 'bank'], 'Select the project folder')

        // Active Choices Reactive Reference Parameter for Redis Folder
        activeChoiceReactiveReferenceParam('REDIS_FOLDER') {
            description('Select Redis Folder based on selected Project')
            choiceType('FORMATTED_HTML')  // Specify the choice type
            groovyScript {
                script("""
import groovy.json.JsonSlurper

// Get the selected project from Jenkins parameters (e.g., payment, coin, platform)
def githubRepoUrl = 'https://api.github.com/repos/ongkyokta/redis-key/contents/'
def tribeName = "stg/" + PROJECT // Dynamically use the selected project

def url = githubRepoUrl + tribeName

// Execute the curl command to fetch the directory contents from GitHub API
def command = ['curl', '-L', '-s', url] // Curl command with flags
def process = command.execute()

// Parse the response from the GitHub API
def jsonResponse = new JsonSlurper().parseText(process.text)

// Initialize an array to hold the folder names (keydb folders)
def folderList = []
def html = '''<select name="value">'''

// Loop through the response and collect the names of subdirectories (keydb folders)
jsonResponse.each { item ->
    if (item.type == 'dir') { // Only consider directories (keydb folders)
        folderList.add(item.name) // Add the directory name to the list
        html = html + "<option value=\"" + item.name + "\">" + item.name + "</option>" + "\\n"
    }
}

html = html + '''</select>'''
return html
""")
                fallbackScript("""
                    return ["<option value='error'>Failed to fetch folders</option>"]
                """)
            }
            referencedParameter('PROJECT')
        }
    }

    // Add any additional configurations like triggers, log rotation, etc.
}
