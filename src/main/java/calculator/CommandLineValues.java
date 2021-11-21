package calculator;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This class handles the programs arguments.
 */
public class CommandLineValues {
    @Option(name = "--dataset", required = false, forbids = {"--project"})
    public Path dataset;

    @Option(name = "--project", required = false, forbids = {"--dataset"})
    public Path project;

    @Option(name = "--output_dir", required = false)
    public Path outputDir = new File("./output").toPath();

    @Option(name = "--log_dir", required = false)
    public Path logDir = Paths.get("./logs");

    @Option(name = "--num_workers", required = false)
    public int numWorkers = 1;


    public CommandLineValues(String... args) throws CmdLineException {
        CmdLineParser parser = new CmdLineParser(this);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
            throw e;
        }
    }

    public CommandLineValues() {

    }
}