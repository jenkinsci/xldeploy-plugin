package com.xebialabs.deployit.ci.workflow;

import java.io.*;
import java.util.zip.ZipEntry;

public class FileResourceReader implements ResourceReader {

    private final Resource resource;
    private final String workspace;

    public FileResourceReader(Resource resource, String workspace) {
        this.resource = resource;
        this.workspace = workspace;
    }

    @Override
    public ResourceInfo readResource() throws IOException {
        String path = this.workspace + File.separator + resource.path;
        File resourceFile = new File(path);
        ZipEntry zipEntry = new ZipEntry(resourceFile.getName());
        InputStream inputStream = new FileInputStream(resourceFile);
        return new ResourceInfo(zipEntry, inputStream);
    }

}
