*** Settings ***

Documentation       Maven plugin test suite setup to download, install and configure the mvn build tool and boot-up the overcast image with XLD

Library             OvercastLibrary
Library             NetLibrary
Library             XldLibrary
Library             OperatingSystem
Library             ./JenkinsLibrary.py  ${WORK_DIR}    ${CACHE_DIR}

Resource            ../conf/vars.robot
Resource            jenkins.robot

*** Keywords ***

Setup Jenkins server
    install jenkins         default         ${JENKINS_VERSION}
    install plugin          default         deployit-plugin     ${PLUGIN_VERSION}
    start jenkins           default         ${JENKINS_IP}       ${JENKINS_PORT}
    set global variable     ${JENKINS_URL}  http://${JENKINS_IP}:${JENKINS_PORT}
    open jenkins browser

Teardown Jenkins server
    close jenkins browser
    stop jenkins        default
    uninstall jenkins   default

Setup XLD server
    ${CURRENT_XLD_URL}=     get variable value  ${XLD_URL}  None
    return from keyword if  '${CURRENT_XLD_URL}' != 'None' # don't boot up overcast if we are using an already running XLD instance
    boot server             version=${XLD_VERSION}  work_dir=${WORK_DIR}    cache_dir=${CACHE_DIR}  port=${XLD_PORT}
    #copy file               ${CURDIR}/../files/synthetic.xml    ${WORK_DIR}/xl-deploy-${XLD_VERSION}-server/ext/synthetic.xml
    set global variable     ${XLD_HOST}         ${XLD_IP}
    set global variable     ${XLD_URL}          http://${XLD_IP}:${XLD_PORT}/deployit
    check server port       ${XLD_IP}           ${XLD_PORT}   30

Teardown XLD server
    ${CURRENT_XLD_HOST}=     get variable value  ${XLD_HOST}  None
    return from keyword if  '${CURRENT_XLD_HOST}' == 'None'
    destroy server           version=${XLD_VERSION}  work_dir=${WORK_DIR}   port=${XLD_PORT}