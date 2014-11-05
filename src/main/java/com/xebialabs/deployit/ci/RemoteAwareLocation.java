package com.xebialabs.deployit.ci;

import com.google.common.io.Files;
import hudson.FilePath;

import java.io.File;
import java.io.IOException;

public abstract class RemoteAwareLocation extends ImportLocation {
    protected File localTempDir;
    protected FilePath localTempDar;

    /**
     * For local workspace just returns the path;
     * For remote workspace - copies dar file into local temporary location first,
     * then returns temporary path. FilePath.cleanup() method should be used to delete all temporary files.
     */
    protected String getRemoteAwareLocation(FilePath workspace, String location) {
        if (!workspace.isRemote()) {
            return location;
        }

        FilePath remoteDar = new FilePath(workspace.getChannel(), location);
        localTempDir = Files.createTempDir();
        localTempDar = new FilePath(new File(localTempDir, remoteDar.getName()));
        try {
            remoteDar.copyTo(localTempDar);
        } catch (Exception e) {
            String msg = String.format("Unable to copy remote dar '%s' to local temp directory '%s'.", remoteDar, localTempDar);
            throw new DeployitPluginException(msg, e);
        }

        return localTempDar.getRemote();
    }

    @Override
    public void cleanup() {
        try {
            if (localTempDar != null && localTempDar.exists())
                localTempDar.delete();
            if (localTempDir != null && localTempDir.exists())
                localTempDir.delete();
        } catch (IOException e) {
            //ignore
        } catch (InterruptedException e) {
            //ignore
        }
    }
}
