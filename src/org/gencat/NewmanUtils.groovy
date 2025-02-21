package org.gencat

class NewmanUtils implements Serializable {

  def script

  NewmanUtils(script) {
    this.script = script
  }

  def runNewman(String appName) {
    def command = """
      newman run ${appName}-backend-postman.json -e ${appName}-environment.json -r 'cli,json,influxdb,junitxray,htmlextra' \\
      --reporter-influxdb-server 'influxdb.monitoring' \\
      --reporter-influxdb-port 8086 \\
      --reporter-influxdb-org '${script.env.MAT_INFLUXDB_COMPANY}' \\
      --reporter-influxdb-version 2 \\
      --reporter-influxdb-username '$INFLUXDB_USER' \\
      --reporter-influxdb-password '$INFLUXDB_PASS' \\
      --reporter-influxdb-name '${script.env.MAT_POSTMAN_INFLUXDB_BUCKET}' \\
      --reporter-influxdb-measurement api_results \\
      --reporter-influxdb-identifier '${script.env.ENV_TO_TEST}' \\
      --reporter-htmlextra-export 'newman/report.html' \\
      --reporter-json-export 'newman/output.json' \\
      --reporter-junitxray-export 'postman_echo_junitxray.xml' -n 1
    """
    
    def status = script.sh(returnStatus: true, script: command)
    if (status != 0) {
      script.echo "Newman tests failed, but the pipeline will continue."
    }
  }

  def convertJsonToMarkdown(String jsonFile, String markdownFile) {
    def jsContent = '''\
const fs = require('fs');
// Función para convertir el JSON a Markdown
function jsonToMarkdown(jsonData) {
  let markdown = "# Reporte de Newman\\n\\n";
  for (const exec of jsonData.run.executions) {
    markdown += `## ${exec.item.name}\\n`;
    const assertions = exec.assertions || [];
    const estado = assertions.length > 0 && assertions.every(a => !a.error) ? 'Pasado' : 'Fallido';
    markdown += `- **Estado**: ${estado}\\n`;
    for (const assertion of assertions) {
      const resultado = assertion.error ? '❌' : '✅';
      markdown += `  - ${assertion.assertion}: ${resultado}\\n`;
    }
    markdown += '\\n';
  }
  return markdown;
}
try {
  const data = JSON.parse(fs.readFileSync('${jsonFile}', 'utf-8'));
  const markdownResult = jsonToMarkdown(data);
  fs.writeFileSync('${markdownFile}', markdownResult, 'utf-8');
  console.log('Archivo ${markdownFile} generado correctamente.');
} catch (error) {
  console.error('Error al procesar el archivo:', error);
  process.exit(1);
}
'''
    script.writeFile file: "convert.js", text: jsContent
    def status = script.sh(returnStatus: true, script: "node convert.js")
    if (status != 0) {
      script.error "Error converting JSON to Markdown"
    }
  }
}
