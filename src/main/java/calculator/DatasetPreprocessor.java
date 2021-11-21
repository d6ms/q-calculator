package calculator;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatasetPreprocessor {

    private Path targetDir;
    private Path outputDir;
    private int numWorkers;

    private Logger logger = Logger.getLogger(DatasetPreprocessor.class.getName());

    public DatasetPreprocessor(Path targetDir, Path outputDir, int numWorkers, Path logDir) {
        this.targetDir = targetDir;
        this.outputDir = outputDir;
        this.numWorkers = numWorkers;

        logger.setLevel(Level.INFO);
        try {
            if (!logDir.toFile().exists()) {
                logDir.toFile().mkdirs();
            }
            Handler handler = new FileHandler(logDir.resolve("preprocess.log").toString());
            handler.setFormatter(new LogFormatter());
            logger.addHandler(handler);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void preprocess() {
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Map.Entry<String, List<File>> en : listProjects().entrySet()) {
                String dataType = en.getKey();
                for (File project : en.getValue()) {
                    Path outPath = outputDir.resolve(dataType);
                    if (!outPath.toFile().exists()) {
                        outPath.toFile().mkdirs();
                    }
                    Future<?> future = executor.submit(() -> preprocess(project, outPath));
                    futures.add(future);
                }
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                }
            }
        } finally {
            executor.shutdown();
        }
        logger.info("complete preprocessing all projects");
    }

    // returns {dataType, projectRoot[]}
    private Map<String, List<File>> listProjects() {
        Map<String, List<File>> result = new HashMap<>();
        for (File dataTypeLevel : targetDir.toFile().listFiles()) {
            if (!dataTypeLevel.isDirectory()) {
                continue;
            }
            for (File projectLevel : dataTypeLevel.listFiles()) {
                if (!projectLevel.isDirectory()) {
                    continue;
                }
                String dataType = dataTypeLevel.toPath().getFileName().toString();
                result.computeIfAbsent(dataType, k -> new ArrayList<>());
                result.get(dataType).add(projectLevel);
            }
        }
        return result;
    }

    private void preprocess(File projectDir, Path outPath) {
        try {
            String jar = new File(getClass().getProtectionDomain().getCodeSource().getLocation()
                    .toURI()).getPath();
            Process p = new ProcessBuilder(
                    "java",
                    "-Xms4g", // TODO parameterize
                    "-Xmx16g",
                    "-cp",
                    jar,
                    "calculator.App",
                    "--project", projectDir.toPath().toAbsolutePath().toString(),
                    "--output_dir", outPath.toAbsolutePath().toString()
            ).start();
            int exitCode = p.waitFor();
            logInputStream(p.getInputStream(), line -> logger.info("[" + projectDir.getName() + "] " + line));
            logInputStream(p.getErrorStream(), line -> logger.severe("[" + projectDir.getName() + "] " + line));
        } catch (URISyntaxException | IOException | InterruptedException e) {
                logger.log(Level.SEVERE, "error processing " + projectDir, e);
        }
    }

    private void logInputStream(InputStream inputStream, Consumer<String> loggingMethod) throws IOException {
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        BufferedReader bufferReader = new BufferedReader(inputStreamReader);
        while (true) {
            String line = bufferReader.readLine();
            if (line != null) {
                loggingMethod.accept(line);
            } else {
                break;
            }
        }
    }
}
