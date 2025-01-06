package com.example.dependency_tracker.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class FileExplorer {

 public static File findSpecificFile(File dir, String folderTag, String fileName) {
        File found = null;
        List<File> files = listAllFiles(dir.getPath());
        for (File file : files) {
            if (file.isDirectory() && file.getName().contains(folderTag)) {
                System.out.println(file);
                List<File> subFiles = listFiles(file.getPath());
                for (File csaFile : subFiles) {
                    if (csaFile.getName().equals(fileName)) {
                        found = csaFile;
                    }
                }
            }
        }
        return found;
    }

    // Lists all files in a directory
    public static List<File> listFiles(String dir) {
        return Stream.of(new File(dir).listFiles())
                .filter(file -> !file.isDirectory())
                .collect(Collectors.toList());
    }

    // Lists all files in a directory
    public static List<File> listAllFiles(String dir) {
        return Stream.of(new File(dir).listFiles())
                .collect(Collectors.toList());
    }

    // Lists all files in a directory
    public static List<File> listDirs(String dir) {
        return Stream.of(new File(dir).listFiles())
                .filter(file -> file.isDirectory())
                .collect(Collectors.toList());
    }

    public static void createFile(File file, String content) throws IOException {
        FileWriter writer = new FileWriter(file);
        writer.write(content);
        writer.close();
        System.out.println("File "+file.getName()+" created!");
    }

    public static String createJson(Map<String, String> map) throws IOException, JSONException {
        JSONArray versionArray = new JSONArray();
        for (Map.Entry<String,String> entry : map.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().equals("[]")) {
                JSONObject version = new JSONObject();
                version.put("name", entry.getValue());
                version.put("assembly", entry.getKey());
                version.put("path", entry.getValue());
                versionArray.put(version);
            }
        }

        return versionArray.toString();
    }

    public static BufferedReader getResourceAsStream(String name) throws IOException {
        InputStream in = FileExplorer.class.getResourceAsStream("/"+name);
        return new BufferedReader(new InputStreamReader(in));
    }

    public static String readFile (File file) {
        StringBuilder sb = new StringBuilder();
        try {
            Scanner myReader = new Scanner(file);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                sb.append(data);
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
        return sb.toString();
    }

    public static List<File> findFilesInDir (File baseDir, String name) {
        List<File> files = FileUtils.listFiles(
                baseDir,
                new RegexFileFilter(name),
                DirectoryFileFilter.DIRECTORY
        ).stream().toList();

        return files;
    }

}
