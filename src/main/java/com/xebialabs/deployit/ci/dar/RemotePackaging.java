/**
 * Copyright (c) 2013, XebiaLabs B.V., All rights reserved.
 *
 *
 * The Deployit plugin for Jenkins is licensed under the terms of the GPLv2
 * <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most XebiaLabs Libraries.
 * There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
 * this software, see the FLOSS License Exception
 * <https://github.com/jenkinsci/deployit-plugin/blob/master/LICENSE>.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation; version 2
 * of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
 * Floor, Boston, MA 02110-1301  USA
 */

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
