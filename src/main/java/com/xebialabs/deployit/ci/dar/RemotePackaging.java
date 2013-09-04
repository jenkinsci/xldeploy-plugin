package com.xebialabs.deployit.ci.dar;

import java.io.File;
import java.util.List;

import com.xebialabs.deployit.engine.packager.DarPackager;
import com.xebialabs.deployit.engine.packager.content.DarMember;
import com.xebialabs.deployit.engine.packager.content.ExternalDarContents;
import com.xebialabs.deployit.engine.packager.content.PackagingListener;

import hudson.remoting.Callable;

/**
 * Wrapper for the packaging operation.
 * It must be executed on the target system where the project artifact was built.
 * @see <a href="https://wiki.jenkins-ci.org/display/JENKINS/Distributed+builds">Jenkins distributed builds</a>
 */
public class RemotePackaging implements Callable<File, RuntimeException> {

    private PackagingListener deploymentListener;

    List<DarMember> darMembers;

    private String applicationName;

    private String applicationVersion;

    private File targetDir;


    public RemotePackaging withListener(PackagingListener deploymentListener) {
        this.deploymentListener = deploymentListener;
        return this;
    }

    public RemotePackaging withDarMembers(List<DarMember> darMembers) {
        this.darMembers = darMembers;
        return this;
    }

    public RemotePackaging forApplication(String applicationName, String applicationVersion) {
        this.applicationName = applicationName;
        this.applicationVersion = applicationVersion;
        return this;
    }

    public RemotePackaging withTargetDir(File targetDir) {
        this.targetDir = targetDir;
        return this;
    }

    /**
     * Call to be executed via jenkins virtual channel
     */
    public File call() throws RuntimeException {
        return new DarPackager(
                targetDir,
                new ExternalDarContents(deploymentListener, darMembers, applicationName, applicationVersion)
        ).perform();
    }

}
