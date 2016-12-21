package com.xebialabs.deployit.ci.workflow;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipEntry;

import static org.apache.commons.lang.StringUtils.isNotBlank;

public class URLResourceReader implements ResourceReader {

    private static final int TIMEOUT_VALUE = 20000;
    private final Resource resource;

    public URLResourceReader(Resource resource) {
        this.resource = resource;
    }

    @Override
    public ResourceInfo readResource() throws IOException {
        URL url = new URL(this.resource.path);
        URLConnection urlConnection = url.openConnection();
        urlConnection.setConnectTimeout(TIMEOUT_VALUE);
        urlConnection.setReadTimeout(TIMEOUT_VALUE);
        setAuthData(urlConnection);
        InputStream inputStream = urlConnection.getInputStream();
        ZipEntry zipEntry = new ZipEntry(FilenameUtils.getName(url.getPath()));
        return new ResourceInfo(zipEntry, inputStream);
    }

    private void setAuthData(URLConnection urlConnection) {
        if (isNotBlank(this.resource.username) && isNotBlank(this.resource.password)) {
            urlConnection.setRequestProperty("Authorization", "Basic " + Base64.encodeBase64String((this.resource.username + ":" + this.resource.password).getBytes()));
        }
    }

}
