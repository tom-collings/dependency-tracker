package com.example.dependency_tracker.collectors;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import com.example.dependency_tracker.gradle.GradleProject;
import com.example.dependency_tracker.maven.MavenProject;
import com.example.dependency_tracker.model.Project;

public class JavaCollector {

    private final static String MAVEN_FILE_PATTERN = "pom.xml";
    private final static String GRADLE_FILE_PATTERN = "build.gradle";
    private final static String GRADLE_KOTLIN_FILE_PATTERN = "build.gradle.kts";
    private File rootDirectory;

    public JavaCollector(File rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public JSONArray fetchDependencies(JSONArray dependencies) throws IOException, SQLException, JSONException, XmlPullParserException {

        findDependenciesGradle(new File(this.rootDirectory.getPath()), dependencies);
        findDependenciesMaven(new File(this.rootDirectory.getPath()), dependencies);

        return dependencies;
    }

    public void findDependenciesGradle(File dir, JSONArray dependencies) throws IOException, JSONException, XmlPullParserException {

        //System.out.println("checking for gradle dependency tree information " + dir.getAbsolutePath());

        GradleProject parser = new GradleProject(dir.getName());

        List<File> files = FileUtils.listFiles(
                dir,
                new RegexFileFilter(GRADLE_FILE_PATTERN),
                DirectoryFileFilter.DIRECTORY
        ).stream().toList();

        for (File f : files) {
            System.out.println("found build.gradle at " + f.getAbsolutePath());
            Project p = parser.parse(f);
            parser.getProjects().add(p);
        }

        files = FileUtils.listFiles(
                dir,
                new RegexFileFilter(GRADLE_KOTLIN_FILE_PATTERN),
                DirectoryFileFilter.DIRECTORY
        ).stream().toList();

        for (File f : files) {
            System.out.println("found build.gradle.kts at " + f.getAbsolutePath());
            Project p = parser.parse(f);
            parser.getProjects().add(p);
        }

        dependencies.put(parser.toJson());

    }

    public void findDependenciesMaven(File dir, JSONArray dependencies) throws IOException, JSONException, XmlPullParserException {

       //System.out.println("checking for maven dependency tree information " + dir.getAbsolutePath());

       MavenProject parser = new MavenProject(dir.getName());

        List<File> files = FileUtils.listFiles(
                dir,
                new RegexFileFilter(MAVEN_FILE_PATTERN),
                DirectoryFileFilter.DIRECTORY
        ).stream().toList();

        for (File f : files) {
            System.out.println("found pom at " + f.getAbsolutePath());
            Project project = parser.parse(f);
            parser.getProjects().add(project);
        }

        parser.reconcile();
        parser.findJavaVersions();

        dependencies.put(parser.toJson());

    }

}
