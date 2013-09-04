package com.xebialabs.deployit.ci;

import java.io.File;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.xebialabs.deployit.ci.util.JenkinsDeploymentListener;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;

import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class GeneratedLocationTest {
    private static final String FILE_SEPARATOR = File.separator;

    @Mock
    private JenkinsDeploymentListener listener;

    @Mock
    private VirtualChannel channel;

    private FilePath remoteFilePath, localFilePath;

    private GeneratedLocation generatedLocation = new GeneratedLocation();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        remoteFilePath = new FilePath(channel, "/tmp/test-remote");
        localFilePath = new FilePath(new File("/tmp/test-local"));
    }

    @Test
    public void shouldReturnPathWithoutChangesIfLocal() {
        generatedLocation.setGeneratedLocation(new File("/tmp/test-local/asd.dar"));
        assertThat(generatedLocation.getDarFileLocation(localFilePath, listener), is(format("%stmp%stest-local%sasd.dar", FILE_SEPARATOR, FILE_SEPARATOR, FILE_SEPARATOR)));
    }

    @Test
    public void shouldReturnLocalTempFileWhenWorkspaceIsRemote() {
        generatedLocation.setGeneratedLocation(new File("/tmp/test-remote/asd.dar"));
        String localDarLocation = generatedLocation.getDarFileLocation(remoteFilePath, listener);
        assertThat(localDarLocation, not("/tmp/test-remote/asd.dar"));
    }

    @Test
    public void shouldCleanUpLocalTempFile() throws Exception {
        generatedLocation.setGeneratedLocation(new File("/tmp/test-remote/asd.dar"));
        String localDarLocation = generatedLocation.getDarFileLocation(remoteFilePath, listener);
        File localDarFile = new File(localDarLocation);
        assertThat(localDarFile.exists(), is(true));

        generatedLocation.cleanup();
        assertThat(localDarFile.exists(), is(false));
    }
}
