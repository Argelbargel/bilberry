package ajk.gradle.elastic

import de.undercouch.gradle.tasks.download.DownloadAction
import org.gradle.api.Project

import static ajk.gradle.elastic.ElasticPlugin.CYAN
import static ajk.gradle.elastic.ElasticPlugin.NORMAL
import static org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS
import static org.apache.tools.ant.taskdefs.condition.Os.isFamily

class ElasticActions {
    String version
    File toolsDir
    Project project
    AntBuilder ant
    File homeDir

    ElasticActions(Project project, File homeDir, File toolsDir, String version) {
        this.project = project
        this.homeDir = homeDir
        this.toolsDir = toolsDir
        this.version = version
        this.ant = project.ant
    }

    boolean isInstalled() {
        if (!new File("$homeDir/bin/elasticsearch").exists()) {
            return false
        }

        def currentVersion = getCurrentVersion()
        if (!(currentVersion?.contains(version))) {
            // cleanup when the installed version doesn't match the expected version
            println "deleting $homeDir ..."
            ant.delete(dir: homeDir, quiet: true)
            return false
        }

        return true
    }

    String getCurrentVersion() {
        def versionInfo = new StringBuffer()

        print "${CYAN}* elastic:$NORMAL checking existing version..."
        def versionFile = new File("$homeDir/version.txt")
        if (versionFile?.isFile() && versionFile?.canRead()) {
            versionInfo = versionFile.readLines()
        }
        println "${versionInfo ?: 'unknown'}"

        return versionInfo
    }

    void install(String packageUrl, List<String> withPlugins) {
        File elasticFile = new File("$toolsDir/elastic-${version}.zip")
        if (!elasticFile.exists()) {
            println "${CYAN}* elastic:$NORMAL downloading elastic version $version from $packageUrl"
            DownloadAction elasticDownload = new DownloadAction(project)
            elasticDownload.dest(elasticFile)
            elasticDownload.src(packageUrl)
            elasticDownload.onlyIfNewer(true)
            elasticDownload.execute()
        }

        println "${CYAN}* elastic:$NORMAL installing elastic from $elasticFile"
        ant.delete(dir: homeDir, quiet: true)
        homeDir.mkdirs()

        if (isFamily(FAMILY_WINDOWS)) {
            ant.unzip(src: elasticFile, dest: "$homeDir") {
                cutdirsmapper(dirs: 1)
            }
        } else {
            ant.untar(src: elasticFile, dest: "$homeDir", compression: "gzip") {
                cutdirsmapper(dirs: 1)
            }
            ant.chmod(file: new File("$homeDir/bin/elasticsearch"), perm: "+x")
            ant.chmod(file: new File("$homeDir/bin/plugin"), perm: "+x")
        }

        new File("$homeDir/version.txt").write(version)

        if (withPlugins.contains("head plugin")) {
            println "* elastic: installing the head plugin"
            String plugin = "$homeDir/bin/plugin"
            if (isFamily(FAMILY_WINDOWS)) {
                plugin += ".bat"
            }

            [
                    new File(plugin),
                    "--install",
                    "mobz/elasticsearch-head"
            ].execute([
                    "JAVA_HOME=${System.properties['java.home']}",
                    "JAVA_OPTS=${System.getenv("JAVA_OPTS")}",
                    "ES_HOME=$homeDir"

            ], homeDir)
        }
    }

    String getElasticPackageUrl() {
        String linuxUrl, winUrl
        if (version.startsWith("2")) {
            def baseUrl = "https://download.elasticsearch.org/elasticsearch/release/org/elasticsearch/distribution"
            linuxUrl = "${baseUrl}/tar/elasticsearch/${version}/elasticsearch-${version}.tar.gz"
            winUrl = "${baseUrl}/zip/elasticsearch/${version}/elasticsearch-${version}.zip"
        } else {
            def baseUrl = "https://download.elastic.co/elasticsearch/elasticsearch"
            linuxUrl = "${baseUrl}/elasticsearch-${version}.tar.gz"
            winUrl = "${baseUrl}/elasticsearch-${version}.zip"
        }

        return isFamily(FAMILY_WINDOWS) ? winUrl : linuxUrl
    }
}
