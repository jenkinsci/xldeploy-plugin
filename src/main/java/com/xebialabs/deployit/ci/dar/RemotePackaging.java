/**
 * Copyright (c) 2014, XebiaLabs B.V., All rights reserved.
 *
 *
 * The XL Deploy plugin for Jenkins is licensed under the terms of the GPLv2
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
import java.util.Collection;

import com.xebialabs.deployit.booter.remote.BooterConfig;
import com.xebialabs.deployit.packager.DarPackager;
import com.xebialabs.deployit.packager.ManifestWriter;
import com.xebialabs.deployit.packager.writers.ManifestXmlWriter;
import com.xebialabs.deployit.plugin.api.reflect.Descriptor;
import com.xebialabs.deployit.plugin.api.reflect.DescriptorRegistry;
import com.xebialabs.deployit.plugin.api.udm.DeploymentPackage;

import hudson.remoting.Callable;

/**
 * Wrapper for the packaging operation.
 * It must be executed on the target system where the project artifact was built.
 * @see <a href="https://wiki.jenkins-ci.org/display/JENKINS/Distributed+builds">Jenkins distributed builds</a>
 */
public class RemotePackaging implements Callable<String, RuntimeException> {

    private File targetDir;
    private DeploymentPackage deploymentPackage;
    private BooterConfig booterConfig;
    private Collection<Descriptor> descriptors;


    public RemotePackaging forDeploymentPackage(DeploymentPackage deploymentPackage) {
        this.deploymentPackage = deploymentPackage;
        return this;
    }

    public RemotePackaging withTargetDir(File targetDir) {
        this.targetDir = targetDir;
        return this;
    }

    public RemotePackaging usingConfig(BooterConfig booterConfig) {
        this.booterConfig = booterConfig;
        return this;
    }

    public RemotePackaging usingDescriptors(Collection<Descriptor> descriptors) {
        this.descriptors = descriptors;
        return this;
    }


    /**
     * Call to be executed via jenkins virtual channel
     */
    public String call() throws RuntimeException {
        targetDir.mkdirs();
        ManifestWriter mw = new ManifestXmlWriter();
        DarPackager pkger = new DarPackager(mw);
        if (DescriptorRegistry.getDescriptorRegistry(booterConfig) == null) {
           SlaveRemoteDescriptorRegistry.boot(descriptors, booterConfig);
        }
        return pkger.buildPackage(deploymentPackage, targetDir.getAbsolutePath(), true).getPath();
    }


}
