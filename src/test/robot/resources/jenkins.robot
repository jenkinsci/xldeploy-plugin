*** Settings ***
Library             Selenium2Library

*** Variables ***
${BROWSER}  phantomjs

*** Keywords ***

Open Jenkins browser
    open browser            ${JENKINS_URL}    ${BROWSER}
    page should contain     Welcome to Jenkins!

Close Jenkins browser
    close all browsers

Create job
    [Arguments]     ${job_name}
    go to                   ${JENKINS_URL}/view/All/newJob
    input text              name    ${job_name}
    select radio button     mode    hudson.model.FreeStyleProject
    click button            ok-button

Build job
    [Arguments]     ${job_name}
    go to                           ${JENKINS_URL}/job/${job_name}/build?delay=0sec
    wait until keyword succeeds     2 min   3 sec   Job should not be running   ${job_name}

Job should not be running
    [Arguments]     ${job_name}
    ${job_status}=          get last build status  ${job_name}
    should not be equal     ${job_status}   RUNNING

Job should be running
    [Arguments]     ${job_name}
    ${job_status}=          get last build status  ${job_name}
    should not equal        ${job_status}   RUNNING

Get last build status
    [Arguments]     ${job_name}
    go to               ${JENKINS_URL}/job/${job_name}/lastBuild/api/xml?depth=1&xpath=*/building/text%28%29
    ${is_running}=      get text    tag=pre
    run keyword if      '${is_running}' == 'true'  return from keyword  RUNNING
    go to               ${JENKINS_URL}/job/${job_name}/lastBuild/api/xml?depth=1&xpath=*/result/text%28%29
    ${result}=          get text    tag=pre
    [return]    ${result}

Last build status should equal
    [Arguments]     ${job_name}     ${expected_status}
    ${job_status}=      get last build status  ${job_name}
    should be equal     ${job_status}   ${expected_status}

Last build status should not equal
    [Arguments]     ${job_name}     ${expected_status}
    ${job_status}=          get last build status  ${job_name}
    should not be equal     ${job_status}   ${expected_status}