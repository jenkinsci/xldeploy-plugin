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

import hudson.remoting.Callable;

import java.io.File;
import java.util.Collection;

import com.xebialabs.deployit.booter.remote.BooterConfig;
import com.xebialabs.deployit.ci.Versioned;
import com.xebialabs.deployit.packager.DarPackager;
import com.xebialabs.deployit.packager.ManifestWriter;
import com.xebialabs.deployit.packager.writers.ManifestXmlWriter;
import com.xebialabs.deployit.plugin.api.reflect.Descriptor;
import com.xebialabs.deployit.plugin.api.reflect.DescriptorRegistry;
import com.xebialabs.deployit.plugin.api.udm.DeploymentPackage;
import org.jenkinsci.remoting.RoleChecker;
import scala.Function0;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.Config;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.InputStream;

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
    private String registryVersion;
    private Function0<MessageDigest> messageDigest;
    private static final Logger logger = LoggerFactory.getLogger(RemotePackaging.class);

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

    public RemotePackaging withRegistryVersion(String registryVersion) {
        this.registryVersion = registryVersion;
        return this;
    }

    /**
     * Call to be executed via jenkins virtual channel
     */
    @Override
    public String call() throws RuntimeException {
        targetDir.mkdirs();
        ManifestWriter mw = new ManifestXmlWriter();
        DarPackager pkger = new DarPackager(mw);
        DescriptorRegistry descriptorRegistry = DescriptorRegistry.getDescriptorRegistry(booterConfig);
        if (null == descriptorRegistry) {
           SlaveRemoteDescriptorRegistry.boot(descriptors, booterConfig, registryVersion);
        } else {
            if (descriptorRegistry instanceof Versioned) {
                Versioned versionedDescriptorRegistry = (Versioned) descriptorRegistry;
                if (!versionedDescriptorRegistry.getVersion().equals(this.registryVersion)) {
                    SlaveRemoteDescriptorRegistry.boot(descriptors, booterConfig, registryVersion);
                }
            }
            else {
                // do nothing for normal remote descriptor registries - those should be reloaded from the UI
            }
        }
        return pkger.buildPackage(deploymentPackage, targetDir.getAbsolutePath(), true).getPath();
    }
    /**
     * Old Call logic for packager - Once Deploy Fixes D-21539 , will update the call with below logic.
     */
    public String oldcall() throws RuntimeException {
        targetDir.mkdirs();
        ManifestWriter mw = new ManifestXmlWriter();
        InputStream ioStream = this.getClass()
                .getClassLoader()
                .getResourceAsStream("reference.conf");
        Reader reader = new InputStreamReader(ioStream);
        Config config = ConfigFactory.parseReader(reader);
        DarPackager pkger = new DarPackager(mw,new Function0<MessageDigest>() {
            @Override
            public MessageDigest apply(){
                MessageDigest messageDigest = null;
                try {
                    messageDigest = MessageDigest.getInstance("SHA1");
                } catch (NoSuchAlgorithmException e){
                    logger.info(e.getMessage());
                }
                return messageDigest;
            }
        },config);
        DescriptorRegistry descriptorRegistry = DescriptorRegistry.getDescriptorRegistry(booterConfig);
        if (null == descriptorRegistry) {
            SlaveRemoteDescriptorRegistry.boot(descriptors, booterConfig, registryVersion);
        } else {
            if (descriptorRegistry instanceof Versioned) {
                Versioned versionedDescriptorRegistry = (Versioned) descriptorRegistry;
                if (!versionedDescriptorRegistry.getVersion().equals(this.registryVersion)) {
                    SlaveRemoteDescriptorRegistry.boot(descriptors, booterConfig, registryVersion);
                }
            }
            else {
                // do nothing for normal remote descriptor registries - those should be reloaded from the UI
            }
        }
        return pkger.buildPackage(deploymentPackage, targetDir.getAbsolutePath(), true).getPath();
    }

    /**
     * This method has an empty implementation, which is added after upgrading the Jenkins core version to 1.642.3
     * @param checker
     * @throws SecurityException
     */
    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {

    }
}