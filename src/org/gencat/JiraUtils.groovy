package org.gencat

class JiraUtils implements Serializable {

  def script

  JiraUtils(script) {
    this.script = script
  }

  def runJiraCmd(String cmdName, Closure callback) {
    if (!script.env.JIRA_PROJECT_KEY?.trim() || !script.env.JIRA_ISSUE_KEY?.trim()) {
      script.echo "[WARNING]: ${cmdName} skipped due to missing Jira parameters."
      return
    }
    callback()
  }

  def addComment(String msg) {
    runJiraCmd("jira_add_comment", {
      script.withEnv(['JIRA_SITE=JIRA-CTTI']) {
        script.jiraAddComment(idOrKey: script.env.JIRA_ISSUE_KEY, input: [body: msg])
      }
    })
  }

  def uploadResults(String importFile) {
    runJiraCmd("jira_upload_results", {
      script.step([
        $class: 'XrayImportBuilder',
        serverInstance: '226faf03-9189-4ee8-964e-1a691d60f62d',
        projectKey: script.env.JIRA_PROJECT_KEY,
        testPlanKey: script.env.JIRA_ISSUE_KEY,
        testEnvironments: script.env.ENV_TO_TEST,
        endpointName: '/junit',
        importFilePath: importFile
      ])
    })
  }

  def uploadReport(String zipFile, String dir) {
    try {
      runJiraCmd("jira_upload_report", {
        script.zip(zipFile: zipFile, archive: false, dir: dir)
        script.withEnv(['JIRA_SITE=JIRA-CTTI']) {
          script.jiraUploadAttachment(idOrKey: script.env.JIRA_ISSUE_KEY, file: zipFile)
        }
      })
    } catch (Exception e) {
      addComment("[ERROR] (#${script.env.BUILD_NUMBER}): ${e.message}")
    }
  }

  def validateIssue() {
    if (!script.env.JIRA_PROJECT_KEY?.trim() || !script.env.JIRA_ISSUE_KEY?.trim()) {
      script.error "[ERROR]: Missing required Jira parameters."
    }
    script.withCredentials([script.usernamePassword(credentialsId: 'jira-step', usernameVariable: 'JIRA_USER', passwordVariable: 'JIRA_API_TOKEN')]) {
      try {
        def response = script.httpRequest(
          url: "${script.env.MAT_JIRA_URL}/rest/api/3/issue/${script.env.JIRA_ISSUE_KEY}",
          httpMode: 'GET',
          acceptType: 'APPLICATION_JSON',
          authentication: 'jira-step'
        )
        if (response.status != 200) {
          script.error "[ERROR] (#${script.env.BUILD_NUMBER}): Failed to retrieve Jira issue: HTTP ${response.status}"
        }
        def issue = script.readJSON text: response.content
        if (issue.fields.project.key != script.env.JIRA_PROJECT_KEY) {
          script.error "[ERROR] (#${script.env.BUILD_NUMBER}): The issue does not belong to project ${script.env.JIRA_PROJECT_KEY}"
        }
        if (issue.fields.issuetype.name != 'Test Plan') {
          script.error "[ERROR] (#${script.env.BUILD_NUMBER}): The issue type is not a Test Plan"
        }
        script.echo "[SUCCESS] (#${script.env.BUILD_NUMBER}): Jira issue validation passed."
      } catch (hudson.AbortException e) {
        if (e.getMessage().contains("404")) {
          script.error "[ERROR] (#${script.env.BUILD_NUMBER}): Issue ${script.env.JIRA_ISSUE_KEY} not found."
        } else if (e.getMessage().contains("403")) {
          script.error "[ERROR] (#${script.env.BUILD_NUMBER}): Forbidden. Check API token permissions."
        } else {
          script.error "[ERROR] (#${script.env.BUILD_NUMBER}): Failed to validate Jira issue: ${e.getMessage()}"
        }
      }
    }
  }
}
