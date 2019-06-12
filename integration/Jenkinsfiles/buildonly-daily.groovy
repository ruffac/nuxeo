
properties([
    [$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', daysToKeepStr: '60', numToKeepStr: '60', artifactNumToKeepStr: '1']],
    disableConcurrentBuilds(),
    [$class: 'GithubProjectProperty', displayName: '', projectUrlStr: 'https://github.com/nuxeo/'],
    [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
    parameters([booleanParam(defaultValue: true, description: '', name: 'CLEAN_REPO')]),
    pipelineTriggers([cron('0 0 * * *')])
])



timestamps {
    
    timeout(time: 240, unit: 'MINUTES') {
        
        node ('SLAVE') {
      
            stage ('Checkout') {
                checkout([$class: 'GitSCM', branches: [[name: 'master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '', url: 'https://github.com/nuxeo/nuxeo/']]])
            }
            stage ('Build') {
            
                withEnv(["MAVEN_OPTS=-Xmx2048m"]) {
            
                    // Shell Clean Workspace step
                    sh """
                        export PATH="/opt/build/tools/maven3/bin:$PATH"
                        [ "$CLEAN_REPO" = "true" ] && rm -rf ${WORKSPACE}/.repository/org/nuxeo 2>/dev/null
                        ./clone.py master 
                       """        
                    // Maven build step
                    withMaven(jdk: 'java-11-openjdk', maven: 'maven-3', mavenOpts: '-Xms2g -Xmx4g', mavenLocalRepo: "$WORKSPACE/.repository") {
                        sh "mvn -f pom.xml -V clean install -DskipTests=true -DskipITs=true -Paddons,distrib "
                    }       
                    // Maven build step
                    withMaven(jdk: 'java-11-openjdk', maven: 'maven-3', mavenOpts: '-Xmx2g') {
                        sh "mvn -f pom.xml -V versions:display-dependency-updates -nsu -N "
                    }       
                    // Maven build step
                    withMaven(jdk: 'java-11-openjdk', maven: 'maven-3', mavenOpts: '-Xms2g -Xmx3g', mavenLocalRepo: "$WORKSPACE/.repository") {
                        sh "mvn -f pom.xml -V license:aggregate-add-third-party -Paddons,distrib,release -nsu "
                    }       
                    // Maven build step
                    withMaven(jdk: 'java-11-openjdk', maven: 'maven-3', mavenOpts: '-Xms2g -Xmx4g') {
                        sh """
                           mvn -V  \
                           dependency:tree \
                           -Paddons,distrib,release \
                           -DoutputFile="${WORKSPACE}/target/generated-sources/dependency-tree.log" \
                           -DappendOutput=true \
                           -nsu 
                               """
                    }       
                    // Maven build step
                    withMaven(jdk: 'java-11-openjdk', maven: 'maven-3', mavenOpts: '-Xms2g -Xmx4g') {
                        sh """
                           mvn -V \
                           org.owasp:dependency-check-maven:aggregate \
                           -Paddons,distrib,release \
                           -nsu 
                           """
                    }
                    
                    // Shell check license step
                
                    def shellReturnStatus = sh returnStatus: true, script: """
                        ./scripts/generate-licenses.py
                        if [ -s target/generated-sources/license/THIRD-PARTY-BLACK.md ]; then
                            echo "check black listed third parties for unknown or wrong licenses" >&2
                            exit 1
                        fi
                            exit 0 
                    """ 
                    if (shellReturnStatus == 1) { 
                       currentBuild.result = 'UNSTABLE' 
                    }
                    archiveArtifacts allowEmptyArchive: false, artifacts: 'target/generated-sources/license/THIRD-PARTY*,target/generated-sources/dependency-tree.log,target/dependency-check-report.html,target/outdated-dependencies.txt', caseSensitive: true, defaultExcludes: true, fingerprint: false, onlyIfSuccessful: false
                }
            }
        }
    }
}
