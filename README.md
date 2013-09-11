#Build#
The Jenkins plugin build is powered by the <a href="https://github.com/jenkinsci/gradle-jpi-plugin">gradle-jpi-plugin</a> (see its <a href="https://wiki.jenkins-ci.org/display/JENKINS/Gradle+JPI+Plugin">documentation</a>).

There are following targets defined:

Builds **.hpi** file

    gradle jpi

Run development server:

    gradle server

###Debugging###

Debuggins is configured with GRADLE_OPTIONS env variable.

    GRADLE_OPTS="${GRADLE_OPTS} -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5006" ./gradlew clean server
