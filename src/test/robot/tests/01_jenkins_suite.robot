*** Settings ***
Documentation       Test suite to test Jenkins needing better documentation
Resource            ../conf/vars.robot
Resource            ../resources/jenkins.robot
Resource            ../resources/setup.robot

Force Tags          jenkins

Suite Setup         setup jenkins server
Suite Teardown      teardown jenkins server

*** Test Cases ***

Should be able to automate jenkins
    create job  job-1
    build job   job-1
    last build status should equal  job-1   SUCCESS
