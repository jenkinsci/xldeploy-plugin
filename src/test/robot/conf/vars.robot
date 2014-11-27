*** Variables ***
# directories
${TMP_DIR}           ${EXECDIR}/tmp
${CACHE_DIR}         ${TMP_DIR}/robot_cache
${WORK_DIR}          ${TMP_DIR}/robot_work

# xld server
${XLD_VERSION}      4.5.2
${XLD_HOST_ID}      xlDeploy
${XLD_USERNAME}     admin
${XLD_PASSWORD}     admin
${XLD_IP}           localhost
${XLD_PORT} 	    4516

# jenkins server
${JENKINS_VERSION}  1.579
${JENKINS_IP}       localhost
${JENKINS_PORT}     8081

# jenkins plugin
${PLUGIN_VERSION}   4.5.0