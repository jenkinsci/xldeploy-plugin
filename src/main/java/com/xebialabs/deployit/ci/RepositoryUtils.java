package com.xebialabs.deployit.ci;

import java.util.Collection;
import java.util.List;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.collect.Ordering;
import com.xebialabs.deployit.ci.Credential.SecondaryServerInfo;
import com.xebialabs.deployit.ci.DeployitNotifier.DeployitDescriptor;
import com.xebialabs.deployit.ci.server.DeployitDescriptorRegistry;
import com.xebialabs.deployit.ci.server.DeployitServer;

import org.apache.commons.lang.StringUtils;

import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.util.Secret;


public class RepositoryUtils {

    public static DeployitServer getDeployitServer(String credentialName, Credential overridingCredential, AbstractProject<?,?> context) {
        Credential credential = findCredential(credentialName);
        if (null != credential && null != overridingCredential) {
            credential = retrieveOverridingCredential(credential, overridingCredential.getCredentialsId(),
                    credential.getName(), overridingCredential.getUsername(), overridingCredential.getPassword(),
                    overridingCredential.isUseGlobalCredential());
        }
        DeployitDescriptor descriptor = getDeployitDescriptor();
        return descriptor.getDeployitServer(credential, context);

    }

    public static DeployitServer getDeployitServerFromCredentialsId(String serverCredentialName, String credentialId, AbstractProject<?,?> context) {
        Credential credential = findCredential(serverCredentialName);
        if (null != credential && null != credentialId) {
            StandardUsernamePasswordCredentials cred = Credential.lookupSystemCredentials(credentialId, context.getParent());
            if ( null == cred )
            {
                throw new IllegalArgumentException(Messages.DeployitNotifier_credentialIdNotFoundError(credentialId));
            }
            credential = retrieveOverridingCredential(credential, credentialId, credential.getName(),
                    cred.getUsername(), cred.getPassword(), true);
        }
        DeployitDescriptor descriptor = getDeployitDescriptor();
        return descriptor.getDeployitServer(credential, context);
    }


    private static Credential retrieveOverridingCredential(Credential credential, String credentialId, String name,
                                                           String username, Secret password, boolean useGlobalCredential) {
        DeployitDescriptor descriptor = getDeployitDescriptor();
        String secondaryProxyUrl = credential.resolveProxyUrl(descriptor.getDeployitClientProxyUrl());
        String secondaryServerUrl = credential.resolveServerUrl(descriptor.getDeployitServerUrl());
        SecondaryServerInfo serverInfo = new SecondaryServerInfo(secondaryServerUrl, secondaryProxyUrl);
        credential = new Credential(name, username, password, credentialId, serverInfo, useGlobalCredential);
        return credential;
    }

    private static List<Credential> getGlobalCredentials() {
        return getDeployitDescriptor().getCredentials();
    }

    public static Credential findCredential(String credentialName) {
        for (Credential credential : getGlobalCredentials()) {
            if (credentialName.equals(credential.getName())) {
                return credential;
            }
        }
        throw new IllegalArgumentException(Messages.DeployitNotifier_credentialsNotFoundError(credentialName));
    }

    public static Credential retrieveOverridingCredentialFromProject(AbstractProject<?,?> project) {
        Credential overridingCredential = null;
        DeployitNotifier notifier = retrieveDeployitNotifierFromProject(project);
        if (null != notifier) {
            overridingCredential = notifier.getOverridingCredential();
            if (null != overridingCredential && StringUtils.isEmpty(overridingCredential.getUsername())
                    && null != overridingCredential.getCredentialsId()) {
                DeployitDescriptor descriptor = (DeployitDescriptor) notifier.getDescriptor();
                String secondaryProxyUrl = overridingCredential.resolveProxyUrl(descriptor.getDeployitClientProxyUrl());
                String secondaryServerUrl = overridingCredential.resolveServerUrl(descriptor.getDeployitServerUrl());
                SecondaryServerInfo serverInfo = new SecondaryServerInfo(secondaryServerUrl, secondaryProxyUrl);

                StandardUsernamePasswordCredentials cred = Credential.lookupSystemCredentials(overridingCredential.getCredentialsId(), project.getParent());
                if (null != cred) {
                    overridingCredential = new Credential(overridingCredential.getName(), cred.getUsername(), cred.getPassword(),
                            overridingCredential.getCredentialsId(), serverInfo, false);
                }
            }
        }
        return overridingCredential;
    }

    public static DeployitNotifier retrieveDeployitNotifierFromProject(AbstractProject<?,?> project) {
        DeployitNotifier notifier = null;
        DeployitDescriptor descriptor = getDeployitDescriptor();
        if (null != project) {
            notifier = (DeployitNotifier) project.getPublishersList().get(descriptor);
        }
        return notifier;
    }


    public static List<String> environments(final DeployitServer deployitServer) {
        List<String> envs = deployitServer.search(DeployitDescriptorRegistry.UDM_ENVIRONMENT);
        return Ordering.natural().sortedCopy(envs);
    }

    public static Collection<String> getAllResourceTypes(final DeployitServer deployitServer) {
        return deployitServer.getDescriptorRegistry().getDeployableResourceTypes();
    }

    public static Collection<String> getAllEmbeddedResourceTypes(final DeployitServer deployitServer) {
        return deployitServer.getDescriptorRegistry().getEmbeddedDeployableTypes();
    }

    public static Collection<String> getAllArtifactTypes(final DeployitServer deployitServer) {
        return deployitServer.getDescriptorRegistry().getDeployableArtifactTypes();
    }

    public static Collection<String> getPropertiesOf(final DeployitServer deployitServer, String type) {
        return deployitServer.getDescriptorRegistry().getEditablePropertiesForDeployableType(type);
    }

    private static DeployitDescriptor getDeployitDescriptor(){
        return (DeployitDescriptor) Hudson.getInstance().getDescriptor(DeployitNotifier.class);
    }

}
