#
import requests
import os
import shutil
import subprocess
import logging
import time

logger = logging.getLogger(__name__)


class JenkinsLibrary(object):
    ROBOT_LIBRARY_SCOPE = 'TEST SUITE'
    ROBOT_LIBRARY_VERSION = '1.0'

    JENKINS_URL = "http://mirrors.jenkins-ci.org/war/%(version)s/jenkins.war"
    PLUGINS_URL = "http://updates.jenkins-ci.org/download/plugins/%(plugin)s/%(version)s/%(plugin)s.hpi"

    def __init__(self, work_dir, cache_dir):
        self.cache_dir = cache_dir
        self.work_dir = work_dir
        self._instances = {}

    # public

    def install_jenkins(self, alias, version, src=None):
        if not src:
            src = self.JENKINS_URL % {"version": version}
        if self._is_remote(src):
            cached_path = os.path.join(self.cache_dir, "jenkins-%s.war" % version)
            self._download_file(src, cached_path)
        else:
            cached_path = src

        war_path = self._get_war_path(alias)
        self._mkdirs(os.path.dirname(war_path))
        shutil.copy2(cached_path, war_path)

    def uninstall_jenkins(self, alias):
        shutil.rmtree(self._get_alias_dir(alias))

    def start_jenkins(self, alias, http_address="localhost", http_port="8080"):
        os.environ["JENKINS_HOME"] = self._get_jenkins_home(alias)
        run_cmd = ["java", "-jar", self._get_war_path(alias),
                   "--httpPort=%s" % http_port,
                   "--httpListenAddress=%s" % http_address,
                   "-Dhudson.model.Api.INSECURE=true"]
        subprocess.Popen(run_cmd, stdin=subprocess.PIPE,
                         stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        url = self._get_jenkins_url(http_address, http_port)
        self._instances[alias] = url
        self._wait_until_up(url)

    def stop_jenkins(self, alias):
        if not alias in self._instances:
            return
        url = self._instances[alias]
        requests.post("%s/exit" % url)
        self._wait_until_down(url)
        del self._instances[alias]

    def install_plugin(self, alias, plugin, version, src=None):
        if not src:
            src = self.PLUGINS_URL % {"plugin": plugin, "version": version}
        if self._is_remote(src):
            cached_path = os.path.join(self.cache_dir, "%s-%s.hpi" % (plugin, version))
            self._download_file(src, cached_path)
        else:
            cached_path = src
        plugins_dir = self._get_plugins_dir(alias)
        self._mkdirs(plugins_dir)
        shutil.copy2(cached_path, os.path.join(plugins_dir, "%s.hpi" % plugin))

    # private

    def _wait_until_up(self, url, timeout=60):
        i = 0
        while i < timeout:
            try:
                logger.info("Waiting for Jenkins to start...")
                response = requests.get(url)
                if response.ok:
                    return True
            except requests.exceptions.ConnectionError, e:
                logger.debug("Connection to %s failed: %s" % (url, e))
            i = i + 1
            time.sleep(1)
        raise RuntimeError("Timeout checking for 200 OK on %s" % url)

    def _wait_until_down(self, url, timeout=60):
        i = 0
        while i < timeout:
            try:
                logger.info("Waiting for Jenkins to shutdown...")
                requests.get(url)
            except requests.exceptions.ConnectionError, e:
                time.sleep(5)
                return True
            i = i + 1
            time.sleep(1)
        raise RuntimeError("Timeout checking to shutdown %s" % url)

    def _get_jenkins_url(self, http_address, http_port):
        return "http://%s:%s" % (http_address, http_port)

    def _get_war_path(self, alias):
        return os.path.join(self._get_alias_dir(alias), "jenkins.war")

    def _get_plugins_dir(self, alias):
        return os.path.join(self._get_jenkins_home(alias), "plugins")

    def _get_jenkins_home(self, alias):
        return os.path.join(self._get_alias_dir(alias), ".home")

    def _get_alias_dir(self, alias):
        return os.path.join(self.work_dir, alias)

    def _is_remote(self, src):
        return src.lower().startswith("http")

    def _download_file(self, src, dest, **kwargs):
        if os.path.isfile(dest) and kwargs.get('force', 'False') == 'False':
            logger.info('Destination file %s already present and force flag is false, skipping download.', dest)
            return

        logger.info('Downloading file from %s...', src)
        self._mkdirs(os.path.dirname(dest))

        with open(dest, 'wb') as handle:
            response = requests.get(src, stream=True)
            assert response.ok
            for block in response.iter_content(1024):
                if not block:
                    break
                handle.write(block)
        logger.info('File %s successfully downloaded.', src)

    def _mkdirs(self, dir):
        if not os.path.exists(dir):
            os.makedirs(dir)