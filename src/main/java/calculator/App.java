package calculator;

import org.kohsuke.args4j.CmdLineException;

public class App {

    public static void main(String[] args) {
        CommandLineValues s_CommandLineValues;
        try {
            s_CommandLineValues = new CommandLineValues(args);
        } catch (CmdLineException e) {
            e.printStackTrace();
            return;
        }

        if (s_CommandLineValues.project != null) {
            ProjectPreprocessTask task = new ProjectPreprocessTask(s_CommandLineValues.project.toFile(), s_CommandLineValues.outputDir);
            task.run();
        } else if (s_CommandLineValues.dataset != null) {
            DatasetPreprocessor preprocessor = new DatasetPreprocessor(
                    s_CommandLineValues.dataset,
                    s_CommandLineValues.outputDir,
                    s_CommandLineValues.numWorkers,
                    s_CommandLineValues.logDir
            );
            preprocessor.preprocess();
        }
    }

}
