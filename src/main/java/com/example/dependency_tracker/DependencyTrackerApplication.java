package com.example.dependency_tracker;

import java.io.File;
import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.example.dependency_tracker.collectors.JavaCollector;
import com.example.dependency_tracker.util.CsvWriter;
import com.example.dependency_tracker.util.FileExplorer;

@SpringBootApplication
public class DependencyTrackerApplication implements CommandLineRunner{

	public static void main(String[] args) {
		SpringApplication.run(DependencyTrackerApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {

		if (args != null && args.length == 2) {
            String appDir = args[0];
            String outputDir = args[1];

            System.out.println("App Dir =>" + appDir);
            System.out.println("Output Dir =>" + outputDir);

            // Fetch dependencies
            JSONArray dependencies = new JSONArray();

            JavaCollector javaHandler = new JavaCollector(new File(appDir));
            javaHandler.fetchDependencies(dependencies);

            FileExplorer.createFile(new File(outputDir + "/" + "dependencies.json"), dependencies.toString());
            //FileExplorer.createFile(new File(outputDir + "/" + "dependencies.csv"), CsvWriter.generateDependencyReportFromJson(dependencies));
        }
        else {
            System.err.println("Provide input and output paths!");
        }
	}

}
