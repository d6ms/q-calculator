package calculator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ProjectPreprocessTask implements Runnable {

    private final File projectDir;
    private final Path outPath;

    public ProjectPreprocessTask(File projectDir, Path outPath) {
        this.projectDir = projectDir;
        this.outPath = outPath;
    }

    @Override
    public void run() {
        JavaParser parser = createParser(projectDir.toPath());
        try {

            String projectName = projectDir.getName();

            double qValue = new QCalculator(projectDir, outPath, parser).calculate();

            File outFile = outPath.resolve(projectName + ".txt").toFile();
            try (FileWriter fw = new FileWriter(outFile)) {
                fw.write(Double.toString(qValue));
            }

            System.out.println("complete preprocessing " + projectDir);
        } catch (Exception e) {
            System.err.println("failed to process project: " + projectDir);
            e.printStackTrace(System.err);
        }
    }


    private JavaParser createParser(Path projectDir) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new JavaParserTypeSolver(projectDir));
        typeSolver.add(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        JavaParser parser = new JavaParser();
        parser.getParserConfiguration().setSymbolResolver(symbolSolver);
        return parser;
    }
}
