#Build#
The Jenkins plugin build is powered by <a href="https://github.com/jenkinsci/gradle-jpi-plugin">gradle-jpi-plugin</a>. Jenkins-style wiki is located <a href="https://wiki.jenkins-ci.org/display/JENKINS/Gradle+JPI+Plugin">here</a>.

There are following targets defined:

Builds **.hpi** file

    ./gradlew jpi

Run development server:

    ./gradlew server

###Debugging###

Debuggins is configured with GRADLE_OPTIONS env variable.

    GRADLE_OPTS="${GRADLE_OPTS} -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5006" ./gradlew clean server
