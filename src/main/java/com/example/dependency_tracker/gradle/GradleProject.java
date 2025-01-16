package com.example.dependency_tracker.gradle;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.model.Plugin;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import com.example.dependency_tracker.model.Dependency;
import com.example.dependency_tracker.model.Project;

import jodd.io.StreamGobbler;

public class GradleProject {

    private static final String TREE_START_STRING = "productionRuntimeClasspath";

    private String name;
    private String type;
    private String source;
    private List<Project> projects = new ArrayList<>();

    public GradleProject(String name) {
        this.name = name;
        this.type = "Java";
        this.source = "Gradle";
    }

    public Project parse(File file) throws IOException, XmlPullParserException, JSONException {
        System.out.println("Parsing "+file.getPath());
        String fileContent = new String(Files.readAllBytes(file.toPath()));

        Project project = new Project("Java");
        project.setFileContent(fileContent);

        String folderName = new File(file.getParent()).getName();
        project.setName(folderName);
        findPlugins(project);
        findJavaVersions(project);
        project.setDependencies(getDependencies(file));
        findProjectMetadata(project);

        return project;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject mavenProject = new JSONObject();
        mavenProject.put("name", name);
        mavenProject.put("type", type);
        mavenProject.put("source", source);

        JSONArray projectArray = new JSONArray();

        for (Project project : projects) {
            JSONObject projectJson = new JSONObject();

            projectJson.put("name", project.getName());
            projectJson.put("type", project.getType());
            projectJson.put("artifactId", project.getArtifactId());
            projectJson.put("groupId", project.getGroupId());
            projectJson.put("version", project.getVersion());
            projectJson.put("javaVersion", project.getJavaVersion());
            projectJson.put("javaSource", project.getJavaSource());
            projectJson.put("javaRelease", project.getJavaRelease());
            projectJson.put("javaTarget", project.getJavaTarget());
            projectJson.put("packaging", project.getPackaging());

            JSONArray dependencyArray = new JSONArray();
            for (Dependency d : project.getDependencies()) {
                JSONObject depJson = new JSONObject();
                depJson.put("artifactId", d.getArtifactId());
                depJson.put("groupId", d.getGroupId());
                depJson.put("version", d.getVersion());
                dependencyArray.put(depJson);
            }
            projectJson.put("dependencies", dependencyArray);

            JSONArray pluginArray = new JSONArray();

            List<Plugin> plugins = project.getPlugins();
            plugins.addAll(project.getManagementPlugins());

            for (Plugin p : project.getPlugins()) {
                JSONObject plugJson = new JSONObject();
                plugJson.put("artifactId", p.getArtifactId());
                plugJson.put("groupId", p.getGroupId());
                plugJson.put("version", p.getVersion());
                pluginArray.put(plugJson);
            }
            projectJson.put("plugins", pluginArray);

            JSONArray moduleArray = new JSONArray();
            for (String m : project.getModules()) {
                moduleArray.put(m);
            }
            projectJson.put("modules", moduleArray);

            if (project.getProperties() != null) {
                JSONArray propertyArray = new JSONArray();
                for (String key : project.getProperties().stringPropertyNames()) {
                    String value = project.getProperties().getProperty(key);
                    JSONObject propJson = new JSONObject();
                    propJson.put("name", key);
                    propJson.put("value", value);
                    propertyArray.put(propJson);
                }

                projectJson.put("properties", propertyArray);
            }

            projectArray.put(projectJson);
        }

        mavenProject.put("projects", projectArray);

        return mavenProject;
    }

    private List<Dependency> getDependencies (File pomFile) {
        String[] commands = new String[]{"gradle", "dependencies", "--configuration", "pRC"};
        List<Dependency> dependencies = new ArrayList<Dependency>();
        try {
            runCmdGradle(commands, new File(pomFile.getParent()), dependencies);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return dependencies;
    }

    public static void runCmdGradle(String[] cmds, File contextDir, List<Dependency> dependencies) throws IOException, InterruptedException, JSONException {
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

    public static void populateDependenciesFromTree(ByteArrayOutputStream processStdOut, List<Dependency> dependencies) throws IOException, JSONException{
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
                        Dependency dep = new Dependency();
                        dep.setArtifactId(artifact);
                        dep.setGroupId(group);
                        dep.setVersion(version);

                        dependencies.add(dep);
                        depsMap.put(key, version);
                    }
                    
                }
            }

            if (line.contains(TREE_START_STRING)) {
                shouldBeSplitting = true;
            }

        }
    }

    public static String findProjectName(File file) throws IOException {
        String fileContent = new String(Files.readAllBytes(file.toPath()));
        String name = null;

        String regexName = "rootProject.name.*=.*'(.*?)'";
        Pattern patternName = Pattern.compile(regexName, Pattern.MULTILINE);

        Matcher matcherName = patternName.matcher(fileContent);
        while (matcherName.find()) {
            String textInBetween = matcherName.group(1);
            name = textInBetween;
        }

        return name;
    }

    public void findProjectMetadata(Project project) {

        String group = null;
        String version = null;

        String regexVersion = "^version\\s[=]*[\\s]*'(.*?)'";
        Pattern patternVersion = Pattern.compile(regexVersion, Pattern.MULTILINE);

        Matcher matcherVersion = patternVersion.matcher(project.getFileContent());
        while (matcherVersion.find()) {
            String textInBetween = matcherVersion.group(1);
            version = textInBetween;
        }

        String regexGroup = "^group\\s[=]*[\\s]*'(.*?)'";
        Pattern patternGroup = Pattern.compile(regexGroup, Pattern.MULTILINE);

        Matcher matcherGroup = patternGroup.matcher(project.getFileContent());
        while (matcherGroup.find()) {
            String textInBetween = matcherGroup.group(1);
            group = textInBetween;
        }

        project.setArtifactId(group);
        project.setGroupId(group);
        project.setVersion(version);

    }

    public void findPlugins(Project project) {

        // for (Project p: projects) {
        // PLUGINS
        String pluginsBlock = "";
        String regexPlugins = "plugins\\s\\{([\\S\\s]*?)}";
        Pattern patternPlugins = Pattern.compile(regexPlugins, Pattern.MULTILINE);
        Matcher matcherPlugins = patternPlugins.matcher(project.getFileContent());
        while (matcherPlugins.find()) {
            pluginsBlock = matcherPlugins.group(1);
            String[] plugins = pluginsBlock.split("\n");

            for (String pluginBlockEntry : plugins) {
                String id = "";
                String idRegexPlugins = "id.[('](.*?)[')]";
                Pattern idPatternPlugins = Pattern.compile(idRegexPlugins, Pattern.MULTILINE);
                Matcher matcherIdPlugins = idPatternPlugins.matcher(pluginBlockEntry);
                while (matcherIdPlugins.find()) {
                    id = matcherIdPlugins.group(1);
                }
                String version = "";
                String versionRegexPlugins = "version.[('](.*?)[')]";
                Pattern versionPatternPlugins = Pattern.compile(versionRegexPlugins, Pattern.MULTILINE);
                Matcher matcherVersionPlugins = versionPatternPlugins.matcher(pluginBlockEntry);
                while (matcherVersionPlugins.find()) {
                    version = matcherVersionPlugins.group(1);
                }
                if (!id.equals("") && !version.equals("")) {
                    Plugin plug = new Plugin();
                    plug.setVersion(version);
                    plug.setArtifactId(id);
                    plug.setGroupId(id);
                    project.getPlugins().add(plug);
                }
            }
            // }
        }
    }

    public void findJavaVersions(Project project) throws JSONException {

        // for (Project p: projects) {
        String jdkSource = null;
        String jdkTarget = null;
        String jdkRelease = null;

        String regexJdkSource = "sourceCompatibility(.*)";
        Pattern patternJdkSource = Pattern.compile(regexJdkSource);
        Matcher matcherJdkSource = patternJdkSource.matcher(project.getFileContent());
        if (matcherJdkSource.find()) {
            jdkSource = matcherJdkSource.group(1);
            if (jdkSource != null) {
                jdkSource = jdkSource.replace("=", "").replace(" ", "");
            }
        }
        project.setJavaSource(jdkSource);

        String regexJdkTarget = "targetCompatibility(.*)";
        Pattern patternJdkTarget = Pattern.compile(regexJdkTarget);
        Matcher matcherJdkTarget = patternJdkTarget.matcher(project.getFileContent());
        if (matcherJdkTarget.find()) {
            jdkTarget = matcherJdkTarget.group(1);
            if (jdkTarget != null) {
                jdkTarget = jdkTarget.replace("=", "").replace(" ", "");
            }
        }
        project.setJavaTarget(jdkTarget);

        String regexJdkRelease = "options.release(.*)";
        Pattern patternJdkRelease = Pattern.compile(regexJdkRelease);
        Matcher matcherJdkRelease = patternJdkRelease.matcher(project.getFileContent());
        if (matcherJdkRelease.find()) {
            jdkRelease = matcherJdkRelease.group(1);
            if (jdkRelease != null) {
                jdkRelease = jdkRelease.replace("=", "").replace(" ", "");
            }
        }
        project.setJavaRelease(jdkRelease);

    }

    public List<Project> getProjects() {
        return projects;
    }

    public void setProjects(List<Project> projects) {
        this.projects = projects;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
