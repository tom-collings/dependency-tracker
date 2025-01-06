package com.example.dependency_tracker.collectors;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import jodd.io.StreamGobbler;

public class JavaCollector {

    private final static String MAVEN_FILE_PATTERN = "pom.xml";
    private final static String GRADLE_FILE_PATTERN = "build.gradle";
    private File rootDirectory;

    private static final String LIST_START_STRING = "The following files have been resolved";
    private static final String TREE_START_STRING = "productionRuntimeClasspath";

    public JavaCollector(File rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public JSONArray fetchDependencies(JSONArray dependencies) throws IOException, SQLException, JSONException {

        findDependenciesGradle(new File(this.rootDirectory.getPath()), dependencies);
        findDependenciesMaven(new File(this.rootDirectory.getPath()), dependencies);

        return dependencies;
    }

    public void findDependenciesGradle(File dir, JSONArray dependencies) throws IOException, JSONException {

        //System.out.println("checking for gradle dependency tree information " + dir.getAbsolutePath());

        List<File> files = FileUtils.listFiles(
                dir,
                new RegexFileFilter(GRADLE_FILE_PATTERN),
                DirectoryFileFilter.DIRECTORY
        ).stream().toList();

        for (File f : files) {
            System.out.println("found pom at " + f.getAbsolutePath());
            buildDependencyTreeGradle(f, dependencies);
        }

    }

    public void findDependenciesMaven(File dir, JSONArray dependencies) throws IOException, JSONException {

       //System.out.println("checking for maven dependency tree information " + dir.getAbsolutePath());

        List<File> files = FileUtils.listFiles(
                dir,
                new RegexFileFilter(MAVEN_FILE_PATTERN),
                DirectoryFileFilter.DIRECTORY
        ).stream().toList();

        for (File f : files) {
            System.out.println("found pom at " + f.getAbsolutePath());
            buildDependencyTreeMaven(f, dependencies);
        }

    }

    private void buildDependencyTreeMaven(File pomFile, JSONArray dependencies) {
        String[] commands;
        String mavenHome = System.getenv("M2_HOME");
        if (!(mavenHome == null) && !mavenHome.isBlank() && !mavenHome.isEmpty()) {
            String settingsLoc = mavenHome + "/settings.xml";
            commands = new String[]{"mvn", "dependency:list", "-s", settingsLoc};
        }
        else {
            commands = new String[]{"mvn", "dependency:list"};
        }
        try {
            runCmdMaven(commands, new File(pomFile.getParent()), dependencies);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
         
        //System.out.println(dependencies.toString());
        
    }

    private void buildDependencyTreeGradle(File pomFile, JSONArray dependencies) {
        String[] commands;
        String mavenHome = System.getenv("M2_HOME");
        /* 
        if (!(mavenHome == null) && !mavenHome.isBlank() && !mavenHome.isEmpty()) {
            String settingsLoc = mavenHome + "/settings.xml";
            commands = new String[]{"mvn", "dependency:list", "-s", settingsLoc};
        }
        */
        //else {
            commands = new String[]{"gradle", "dependencies", "--configuration", "pRC"};
        //}
        try {
            runCmdGradle(commands, new File(pomFile.getParent()), dependencies);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
         
        //System.out.println(dependencies.toString());
        
    }

    public static void runCmdMaven(String[] cmds, File contextDir, JSONArray dependencies) throws IOException, InterruptedException, JSONException {

        StringBuilder command = new StringBuilder();
        for (String s: cmds) {
            command.append(s).append(" ");
        }

        System.out.println("trying to run the command " + command.toString());

        ProcessBuilder builder = new ProcessBuilder();
        builder.command(cmds);
        builder.directory(contextDir);
        Process process = builder.start();

        ByteArrayOutputStream processStdOut = new ByteArrayOutputStream();
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();

        StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), processStdOut, "");
        StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), errorOutput, "");
        outputGobbler.start();
        errorGobbler.start();
        process.waitFor();
        outputGobbler.waitFor();
        errorGobbler.waitFor();

        //System.out.println("output is: " + processStdOut.toString());
        
        populateDependenciesFromList(processStdOut, dependencies);

        // If it fails for any reasons
        if (process.exitValue() != 0) {
            throw new RuntimeException("Command => ["+command.toString()+"] failed! \n\nStdOut =>\n" + processStdOut.toString() + "\nStdErr =>\n"+errorOutput.toString());
        }

    }

    public static void runCmdGradle(String[] cmds, File contextDir, JSONArray dependencies) throws IOException, InterruptedException, JSONException {
        StringBuilder command = new StringBuilder();
        for (String s: cmds) {
            command.append(s).append(" ");
        }

        System.out.println("trying to run the command " + command.toString());

        ProcessBuilder builder = new ProcessBuilder();
        builder.command(cmds);
        builder.directory(contextDir);
        Process process = builder.start();

        ByteArrayOutputStream processStdOut = new ByteArrayOutputStream();
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();

        StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), processStdOut, "");
        StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), errorOutput, "");
        outputGobbler.start();
        errorGobbler.start();
        process.waitFor();
        outputGobbler.waitFor();
        errorGobbler.waitFor();

        System.out.println("output is: " + processStdOut.toString());
        
        populateDependenciesFromTree(processStdOut, dependencies);

        // If it fails for any reasons
        if (process.exitValue() != 0) {
            throw new RuntimeException("Command => ["+command.toString()+"] failed! \n\nStdOut =>\n" + processStdOut.toString() + "\nStdErr =>\n"+errorOutput.toString());
        }
    }

    public static void populateDependenciesFromTree(ByteArrayOutputStream processStdOut, JSONArray dependencies) throws IOException, JSONException{
        System.out.println("populating the dependencies from a tree");

        ByteArrayInputStream inStream = new ByteArrayInputStream(processStdOut.toByteArray());
        InputStreamReader reader = new InputStreamReader(inStream);
        BufferedReader buffReader = new BufferedReader(reader);

        String line;
        String[] values;
        boolean shouldBeSplitting = false;

        Map<String, String> depsMap = new HashMap<String, String>();

        while ((line = buffReader.readLine()) != null) {
            if (shouldBeSplitting) {
                if (line.startsWith("(c))") || line.isBlank() || line.isEmpty()) {
                    shouldBeSplitting = false;
                }
                else {
                    String rightSide = line.substring(line.indexOf("---")+3).trim();
                    
                    values = rightSide.split(":");
                    String group = values[0].trim();
                    String artifact;
                    String version;
                    if (values[1].contains("->")) {
                        artifact = values[1].substring(0,values[1].indexOf("->")).trim();
                        String annotatedversion = values[1].substring(values[1].indexOf("->")+2).trim();
                        if (annotatedversion.contains(" ")) {
                            version = annotatedversion.substring(0, annotatedversion.indexOf(" "));
                        }
                        else {
                            version = annotatedversion;
                        }
                    }
                    else {
                        artifact = values[1].trim();
                        String annotatedversion = values[2];
                        if (annotatedversion.contains(" ")) {
                            version = annotatedversion.substring(0, annotatedversion.indexOf(" "));
                        }
                        else {
                            version = annotatedversion;
                        }
                    }
                    //System.out.println("gav = " + group + ":" + artifact + ":" + version);
                    String key = group+artifact;
                    if (!depsMap.containsKey(key)) {
                        JSONObject depJson = new JSONObject();
                        depJson.put("artifactId", artifact);
                        depJson.put("groupId", group);
                        depJson.put("version", version);
                        dependencies.put(depJson);
                        depsMap.put(key, version);
                    }
                    
                }
            }

            if (line.contains(TREE_START_STRING)) {
                shouldBeSplitting = true;
            }

        }
    }

    public static void populateDependenciesFromList (ByteArrayOutputStream processStdOut, JSONArray dependencies) throws IOException, JSONException{
        ByteArrayInputStream inStream = new ByteArrayInputStream(processStdOut.toByteArray());
        InputStreamReader reader = new InputStreamReader(inStream);
        BufferedReader buffReader = new BufferedReader(reader);

        String line;
        String trimmedLine = "";
        String[] values;
        boolean shouldBeSplitting = false;

        while ((line = buffReader.readLine()) != null) {
            trimmedLine = line.replace("[INFO]", "");
            if (shouldBeSplitting) {
                if (trimmedLine.contains("--------------") || trimmedLine.isBlank() || trimmedLine.isEmpty() ) {
                    shouldBeSplitting = false;
                }
                else {
                    values = trimmedLine.split(":");
                    String group = values[0].trim();
                    String artifact = values[1].trim();
                    String version = values[3].trim();
                    //System.out.println("gav = " + group + ":" + artifact + ":" + version);
                    JSONObject depJson = new JSONObject();
                    depJson.put("artifactId", artifact);
                    depJson.put("groupId", group);
                    depJson.put("version", version);
                    dependencies.put(depJson);
                }
            }

            if (trimmedLine.contains(LIST_START_STRING)) {
                shouldBeSplitting = true;
            }

        }


    }

}
