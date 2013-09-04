package com.xebialabs.deployit.ci.dar;

import java.io.File;

import hudson.remoting.Callable;

/**
 * Wrapper for file-related operations.
 * Those must be executed on the target system (where files are located) which may be different
 * from the system where code is executed.
 * @see <a href="https://wiki.jenkins-ci.org/display/JENKINS/Distributed+builds">Jenkins distributed builds</a>
 */
public class RemoteLookup implements Callable<File, RuntimeException> {

    private String artifactPath;

    private String workspacePath;

    public RemoteLookup(final String artifactPath, final String workspacePath) {
        this.artifactPath = artifactPath;
        this.workspacePath = workspacePath;
    }

    @Override
    public File call() throws RuntimeException {
        if (new File(artifactPath).isAbsolute()) {
            return new File(artifactPath);
        }
        return new File(workspacePath, artifactPath);
    }
}
