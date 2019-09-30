package ajk.gradle.elastic

import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import static ajk.gradle.elastic.ElasticPlugin.*
import static org.apache.http.client.fluent.Executor.newInstance
import static org.apache.http.client.fluent.Request.Post
import static org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS
import static org.apache.tools.ant.taskdefs.condition.Os.isFamily

class StopElasticAction {
    @Input
    @Optional
    private String networkHost

    @Input
    @Optional
    private Integer httpPort

    @Input
    @Optional
    File homeDir

    @Input
    @Optional
    String elasticVersion

    private AntBuilder ant
    private Project project

    StopElasticAction(Project project) {
        this.project = project
        this.ant = project.ant
    }

    void execute() {
        File homeDir = homeDir ?: new File("${project.buildDir}/elastic")
        String elasticVersion = elasticVersion ?: DEFAULT_ELASTIC_VERSION

        networkHost = networkHost ?: 'localhost'
        httpPort = httpPort ?: 9200
        println "${CYAN}* elastic:$NORMAL stopping ElasticSearch at ${networkHost}:${httpPort}"

        try {
            def pidFile = new File(homeDir, 'elastic.pid')
            if (elasticVersion.startsWith("2")) {
                if (!pidFile.exists()) {
                    println "${RED}* elastic:$NORMAL ${pidFile} not found"
                    println "${RED}* elastic:$NORMAL could not stop ElasticSearch, please check manually!"
                    return
                }
                def elasticPid = pidFile.text
                println "${CYAN}* elastic:$NORMAL going to kill pid $elasticPid"
                if (isFamily(FAMILY_WINDOWS)) {
                    "cmd /c \"taskkill /f /pid $elasticPid\"".execute()
                } else {
                    "kill $elasticPid".execute()
                }
            } else {
                newInstance().execute(Post("http://${networkHost}:${httpPort}/_shutdown"))
            }

            println "${CYAN}* elastic:$NORMAL waiting for ElasticSearch to shutdown"
            ant.waitfor(maxwait: 2, maxwaitunit: "minute", timeoutproperty: "elasticTimeout") {
                not {
                    ant.http(url: "http://${networkHost}:$httpPort")
                }
            }

            if (ant.properties['elasticTimeout'] != null) {
                println "${RED}* elastic:$NORMAL could not stop ElasticSearch"
                throw new RuntimeException("failed to stop ElasticSearch")
            } else {
                if (isFamily(FAMILY_WINDOWS)) {
                    pidFile.delete()
                }
                println "${CYAN}* elastic:$NORMAL ElasticSearch is now down"
            }
        } catch (ConnectException e) {
            println "${CYAN}* elastic:$YELLOW warning - unable to stop elastic on  ${networkHost}:${httpPort}, ${e.message}$NORMAL"
        }
    }
}
