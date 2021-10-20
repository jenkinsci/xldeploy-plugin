package com.xebialabs.deployit.ci;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class RepositoryUtilsTest {
    static final String ID = "id";
    static final String USERNAME = "admin";
    static final String PASSWORD = "admin";
    static final String DESCRIPTION = "description";

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void shouldRetrieveOverridingCredentialFromProject() throws Exception {
        Folder f = r.jenkins.createProject(Folder.class, "folder1");
        UsernamePasswordCredentialsImpl credentials =
                new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, ID, DESCRIPTION, USERNAME,
                        PASSWORD);
        getFolderStore(f).addCredentials(Domain.global(), credentials);

        // Matches what's sent from ui
        Credential overridingCredentialProvided = new Credential(null, "", null, credentials.getId(), null, true);
        DeployitNotifier.DeployitDescriptor descriptor = new DeployitNotifier.DeployitDescriptor();

        DeployitNotifier notifierSpy = spy(new DeployitNotifier("AdminGlobal1", "app1", null, null, null, null, false, null, overridingCredentialProvided));
        doReturn(descriptor).when(notifierSpy).getDescriptor();
        doReturn(overridingCredentialProvided).when(notifierSpy).getOverridingCredential();

        FreeStyleProject freeStyleProjectSpy = spy(new FreeStyleProject(f, "folder1/proj1"));
        freeStyleProjectSpy.addPublisher(notifierSpy);

        DescribableList<Publisher, Descriptor<Publisher>> publisherListMock = mock(DescribableList.class);
        doReturn(notifierSpy).when(publisherListMock).get(any(DeployitNotifier.DeployitDescriptor.class));
        doReturn(publisherListMock).when(freeStyleProjectSpy).getPublishersList();

        Credential overridingCredential = RepositoryUtils.retrieveOverridingCredentialFromProject(freeStyleProjectSpy);
        assertEquals(USERNAME, overridingCredential.getUsername());
        assertEquals(PASSWORD, overridingCredential.getPassword().getPlainText());
    }

    @Test
    public void shouldNotRetrieveOverridingCredentialFromProjectWhenNotDefined() throws Exception {
        Folder f = r.jenkins.createProject(Folder.class, "folder1");
        FreeStyleProject freeStyleProjectSpy = spy(new FreeStyleProject(f, "folder1/proj1"));

        assertNull(RepositoryUtils.retrieveOverridingCredentialFromProject(freeStyleProjectSpy));
    }

    @Test
    public void shouldRetrieveLoadTypesFlag() throws Exception {
        Folder f = r.jenkins.createProject(Folder.class, "folder-relead");
        FreeStyleProject freeStyleProjectSpy = spy(new FreeStyleProject(f, "folder-reload/proj1"));

        DeployitNotifier.DeployitDescriptor descriptor = new DeployitNotifier.DeployitDescriptor();
        DeployitNotifier notifierSpy = spy(new DeployitNotifier("AdminGlobal2", "app1", null, null, null, null, false, null, true));
        doReturn(descriptor).when(notifierSpy).getDescriptor();
        freeStyleProjectSpy.addPublisher(notifierSpy);

        DescribableList<Publisher, Descriptor<Publisher>> publisherListMock = mock(DescribableList.class);
        doReturn(notifierSpy).when(publisherListMock).get(any(DeployitNotifier.DeployitDescriptor.class));
        doReturn(publisherListMock).when(freeStyleProjectSpy).getPublishersList();

        assertNotNull(RepositoryUtils.retrieveDeployitNotifierFromProject(freeStyleProjectSpy));
        assertEquals(RepositoryUtils.retrieveDeployitNotifierFromProject(freeStyleProjectSpy).loadTypesOnStartup, true);
    }

    CredentialsStore getFolderStore(Folder f) {
        Iterable<CredentialsStore> stores = CredentialsProvider.lookupStores(f);
        CredentialsStore folderStore = null;
        for (CredentialsStore s : stores) {
            if (s.getProvider() instanceof FolderCredentialsProvider && s.getContext() == f) {
                folderStore = s;
                break;
            }
        }
        return folderStore;
    }
}