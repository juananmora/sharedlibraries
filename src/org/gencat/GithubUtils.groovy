package org.gencat

class GitHubUtils implements Serializable {

  def script

  GitHubUtils(script) {
    this.script = script
  }

  def addCommentToPullRequest(String repoUrl, String pullRequestNumber, String markdownFile) {
    if (!pullRequestNumber?.trim()) {
      script.echo "[WARNING]: attach-md-to-github-pull-request skipped due to missing parameters (Pull Request)"
      return
    }
    script.withCredentials([script.string(credentialsId: 'github-token', variable: 'GITHUB_TOKEN')]) {
      def urlParts = repoUrl.split('/')
      def organization = urlParts[-2]
      def repoName = urlParts[-1].replace('.git', '')
      def pullRequestUrl = "https://api.github.com/repos/${organization}/${repoName}/issues/${pullRequestNumber}/comments"

      def mdFileContent = script.readFile(markdownFile)
      def payload = [ body: "Adjuntando el reporte de Pruebas API:\\n\\n" + mdFileContent ]

      def response = script.httpRequest(
        acceptType: 'APPLICATION_JSON',
        contentType: 'APPLICATION_JSON',
        httpMode: 'POST',
        url: pullRequestUrl,
        requestBody: groovy.json.JsonOutput.toJson(payload),
        customHeaders: [[name: 'Authorization', value: "Bearer ${script.GITHUB_TOKEN}"]],
        validResponseCodes: '201'
      )
      script.echo "Archivo .md adjuntado a la pull request de GitHub exitosamente."
    }
  }
}
