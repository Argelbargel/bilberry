package ajk.gradle.elastic

import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import static ajk.gradle.elastic.ElasticPlugin.*
import static org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS
import static org.apache.tools.ant.taskdefs.condition.Os.isFamily

class StartElasticAction {

    @Input
    @Optional
    String elasticVersion

    @Input
    @Optional
    int httpPort

    @Input
    @Optional
    int transportPort

    @Input
    @Optional
    File homeDir

    @Input
    @Optional
    File toolsDir

    @Input
    @Optional
    File dataDir

    @Input
    @Optional
    File logsDir

    @Input
    @Optional
    List<String> withPlugins = ["head plugin"]

    @Input
    @Optional
    boolean deleteDataDir = true

    @Input
    @Optional
    String packageUrl = null

    @Input
    @Optional
    String minMemory = "128m"

    @Input
    @Optional
    String maxMemory = "512m"

    private Project project

    private AntBuilder ant

    StartElasticAction(Project project) {
        this.project = project
        this.ant = project.ant
    }

    void execute() {
        File toolsDir = toolsDir ?: new File("${project.rootDir}/.gradle/tools")
        File homeDir = homeDir ?: new File("${project.buildDir}/elastic")
        ElasticActions elastic = new ElasticActions(project, homeDir, toolsDir, elasticVersion ?: DEFAULT_ELASTIC_VERSION)

        def pidFile = new File(elastic.homeDir, 'elastic.pid')
        if (pidFile.exists()) {
            println "${YELLOW}* elastic:$NORMAL ElasticSearch seems to be running at pid ${pidFile.text}"
            println "${YELLOW}* elastic:$NORMAL please check $pidFile"
            return
        }

        if (!elastic.installed) {
            String url = (packageUrl == null || packageUrl == "") ? elastic.getElasticPackageUrl() : packageUrl
            elastic.install(url, withPlugins)
        }

        httpPort = httpPort ?: 9200
        transportPort = transportPort ?: 9300
        dataDir = dataDir ?: new File("$project.buildDir/elastic")
        logsDir = logsDir ?: new File("$dataDir/logs")
        println "${CYAN}* elastic:$NORMAL starting ElasticSearch at $elastic.homeDir using http port $httpPort and tcp transport port $transportPort"
        println "${CYAN}* elastic:$NORMAL ElasticSearch data directory: $dataDir"
        println "${CYAN}* elastic:$NORMAL ElasticSearch logs directory: $logsDir"

        if (deleteDataDir) {
            ant.delete(failonerror: true, dir: dataDir)
        }

        ant.delete(failonerror: true, dir: logsDir)

        dataDir.mkdirs()
        logsDir.mkdirs()

        File esScript = new File("${elastic.homeDir}/bin/elasticsearch${isFamily(FAMILY_WINDOWS) ? '.bat' : ''}")
        def environment = [
                "JAVA_HOME=${System.properties['java.home']}",
                "ES_HOME=$elastic.homeDir",
                "ES_MAX_MEM=${maxMemory}",
                "ES_MIN_MEM=${minMemory}"
        ]

        def command = [
                esScript.absolutePath,
                "-Des.http.port=$httpPort",
                "-Des.transport.tcp.port=$transportPort",
                "-Des.path.data=$dataDir",
                "-Des.path.logs=$logsDir",
                "-Des.discovery.zen.ping.multicast.enabled=false",
                    "-p${pidFile}"
            ]

        if (isFamily(FAMILY_WINDOWS)) {
            environment += [
                    "TEMP=${System.env['TEMP']}"
            ]
        }

        command.execute(environment, elastic.homeDir)

        println "${CYAN}* elastic:$NORMAL waiting for ElasticSearch to start"
        ant.waitfor(maxwait: 2, maxwaitunit: "minute", timeoutproperty: "elasticTimeout") {
            and {
                socket(server: "localhost", port: transportPort)
                ant.http(url: "http://localhost:$httpPort")
            }
        }

        if (ant.properties['elasticTimeout'] != null) {
            println "${RED}* elastic:$NORMAL could not start ElasticSearch"
            throw new RuntimeException("failed to start ElasticSearch")
        } else {
            println "${CYAN}* elastic:$NORMAL ElasticSearch is now up"
        }
    }
}
