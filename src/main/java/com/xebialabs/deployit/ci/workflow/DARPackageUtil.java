package com.xebialabs.deployit.ci.workflow;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DARPackageUtil {
    private final List<Resource> artifacts;
    private final Resource manifest;
    private final String packageName;
    private final String packageVersion;
    private final String workspace;
    private final ResourceReaderFactory resourceReaderFactory = new ResourceReaderFactory();

    public DARPackageUtil(List<Resource> artifacts, Resource manifest, String packageName, String packageVersion, String workspace) {

        this.artifacts = artifacts;
        this.manifest = manifest;
        this.packageName = packageName;
        this.packageVersion = packageVersion;
        this.workspace = workspace;
    }

    public String createPackage() throws IOException {
        String packagePath = outputFilePath();
        try (FileOutputStream fileOutputStream = new FileOutputStream(packagePath);
             ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {
            addManifest(this.manifest, zipOutputStream);
            zipOutputStream.putNextEntry(new ZipEntry(this.packageName + File.separator));
            for (Resource artifact : this.artifacts) {
                addArtifact(artifact, zipOutputStream);
                zipOutputStream.closeEntry();
            }
        }
        return packagePath;
    }

    private void addManifest(Resource resource, ZipOutputStream zipOutputStream) throws IOException {
        addResourceToPackage(resource, zipOutputStream, true);
    }

    private void addArtifact(Resource resource, ZipOutputStream zipOutputStream) throws IOException {
        addResourceToPackage(resource, zipOutputStream, false);
    }

    private void addResourceToPackage(Resource resource, ZipOutputStream zipOutputStream, boolean manifest) throws IOException {
        ResourceInfo resourceInfo = resourceReaderFactory.getReader(resource, this.workspace).readResource();
        InputStream inputStream = resourceInfo.getInputStream();
        ZipEntry zipEntry = resourceInfo.getZipEntry();
        ZipEntry modifiedEntry = (manifest) ? zipEntry : new ZipEntry(this.packageName + File.separator + zipEntry.getName());
        zipOutputStream.putNextEntry(modifiedEntry);
        byte[] buf = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buf)) > 0) {
            zipOutputStream.write(buf, 0, bytesRead);
        }
    }

    private String outputFilePath() {
        return new StringBuilder(this.workspace).append(File.separator).append(this.packageName).append("-").append(this.packageVersion).append(".dar").toString();
    }

}