package com.xebialabs.deployit.ci.workflow;

import hudson.EnvVars;
import hudson.remoting.Callable;
import org.jenkinsci.remoting.RoleChecker;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DARPackageUtil implements Callable<String, IOException> {

    private static final String SEPARATOR = "/";

    private final String artifactsPath;
    private final String manifestPath;
    private final String darPath;
    private final String workspace;
    private final EnvVars envVars;

    public DARPackageUtil(String artifactsPath, String manifestPath, String darPath, EnvVars envVars) {
        this.artifactsPath = artifactsPath;
        this.manifestPath = manifestPath;
        this.darPath = envVars.expand(darPath);
        this.workspace = envVars.get("WORKSPACE");
        this.envVars = envVars;
    }

    public String call() throws IOException {
        String manifestContent = replaceEnvVarInManifest();
        List<String> filePathsToBeAdded = filterFiles(manifestContent);
        String packagePath = outputFilePath();
        File darFile = new File(packagePath);
        darFile.getParentFile().mkdirs();

        try (FileOutputStream fileOutputStream = new FileOutputStream(darFile);
             ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {
            // Add manifest file
            addEntryToZip("", this.workspace + File.separator + manifestPath, zipOutputStream, false, true);

            for (String filePath : filePathsToBeAdded) {
                String parentPath = filePath.contains("/") ? filePath.substring(0, filePath.lastIndexOf("/")) : "";
                File file = new File(workspace + File.separator + filePath);
                if (file.isDirectory()) {
                    // Add artifacts directory
                    addFolderToZip(parentPath, this.workspace + File.separator + artifactsPath + File.separator + filePath, zipOutputStream);
                } else {
                    addEntryToZip(parentPath, this.workspace + File.separator + artifactsPath + File.separator + filePath, zipOutputStream, false, false);
                }
            }

        }
        return packagePath;
    }

    List<String> filterFiles(String manifestContent) {
        final List<String> files = new ArrayList<>();
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            DefaultHandler handler = new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                    super.startElement(uri, localName, qName, attributes);
                    for (int index = 0; index < attributes.getLength(); index++) {
                        if (attributes.getQName(index).equals("file")) {
                            files.add(attributes.getValue(attributes.getQName(index)));
                        }
                    }
                }
            };
            saxParser.parse(new InputSource(new StringReader(manifestContent)), handler);
        } catch (SAXException | IOException | ParserConfigurationException e) {
           throw new IllegalArgumentException("Exception Occured while parsing deployit-manifest", e);
        }
        return files;
    }

    private String replaceEnvVarInManifest() throws IOException {
        String manifestContent;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Files.copy(Paths.get(workspace, manifestPath), outputStream);
            manifestContent = new String(outputStream.toByteArray());
        }
        manifestContent = envVars.expand(manifestContent);
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(workspace, manifestPath), StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.append(manifestContent);
        }
        return manifestContent;
    }

    private void addFolderToZip(String path, String srcFolder, ZipOutputStream zip) throws IOException {
        File folder = new File(srcFolder);
        if (folder.list().length == 0) {
            addEntryToZip(path, srcFolder, zip, true, false);
        } else {
            for (String fileName : folder.list()) {
                if (path.equals("")) {
                    addEntryToZip(folder.getName(), srcFolder + File.separator + fileName, zip, false, false);
                } else {
                    addEntryToZip(path + SEPARATOR + folder.getName(), srcFolder + File.separator + fileName, zip, false, false);
                }
            }
        }
    }

    private void addEntryToZip(String path, String srcFile, ZipOutputStream zip, boolean isFolder, boolean isManifest) throws IOException {
        File entry = new File(srcFile);
        if (isFolder == true) {
            zip.putNextEntry(new ZipEntry(path + SEPARATOR + entry.getName() + SEPARATOR));
        } else {
            if (entry.isDirectory()) {
                addFolderToZip(path, srcFile, zip);
            } else {
                byte[] buf = new byte[8192];
                int len;
                ZipEntry zipEntry = isManifest ? new ZipEntry(entry.getName()) : new ZipEntry(path + SEPARATOR + entry.getName());
                zip.putNextEntry(zipEntry);
                try (FileInputStream in = new FileInputStream(srcFile)) {
                    while ((len = in.read(buf)) > 0) {
                        zip.write(buf, 0, len);
                    }
                }
            }
        }
    }

    private String outputFilePath() {
        return new StringBuilder(this.workspace).append(File.separator).append(this.darPath).toString();
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {

    }
}