package com.xebialabs.deployit.ci;

import hudson.EnvVars;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ArtifactViewTest {

    @Test
    public void testIsUriLocationMethod() {
        ArtifactView av = new ArtifactView("file.File", "file-1", null, null, null);
        av.location = "maven:group:artifact:1.0";
        assertThat(av.isUriLocation(new EnvVars()), is(true));
        av.location = "http://www.xebialabs.com";
        assertThat(av.isUriLocation(new EnvVars()), is(true));
        av.location = "build/test/test.dar";
        assertThat(av.isUriLocation(new EnvVars()), is(false));
        av.location = "C:/test/test.dar";
        assertThat(av.isUriLocation(new EnvVars()), is(false));
        av.location = "x:\\test\\test.dar";
        assertThat(av.isUriLocation(new EnvVars()), is(false));
    }

}
