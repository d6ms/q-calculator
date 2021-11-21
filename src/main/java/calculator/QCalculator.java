package calculator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class QCalculator {
    private final File projectDir;
    private final Path outPath;
    private final JavaParser parser;

    public QCalculator(File projectDir, Path outPath, JavaParser parser) {
        this.projectDir = projectDir;
        this.outPath = outPath;
        this.parser = parser;
    }

    public double calculate() throws IOException {
        List<Path> targetPaths = listPaths();
        Set<String> allPackages = new HashSet<>();
        for (Path path : targetPaths) {
            path = projectDir.toPath().relativize(path);
            String packageName = path.getParent().toString().replace("/", ".");
            allPackages.add(packageName);
        }
        allPackages = Collections.unmodifiableSet(allPackages);

        // 全ての辺を抽出
        List<Edge> edges = new ArrayList<>();
        for (Path path : targetPaths) {
            CompilationUnit cu = parse(path);
            if (cu == null) {
                continue;
            }
            var visitor = new EdgeCollectorVisitor(allPackages);
            visitor.visit(cu, null);
            edges.addAll(visitor.getEdges());
        }

        // Q値の計算
        int edgeCount = edges.size();
        List<Double> aSums = new ArrayList<>();
        for (String i : allPackages) {
            Map<String, Integer> count = edges.stream()
                    .filter(e -> e.contains(i))
                    .map(e -> e.getCounterPart(i))
                    .collect(Collectors.toMap(n -> n, n -> 1, Integer::sum));
            int aSum = 0;
            for (String j : allPackages) {
                if (count.containsKey(j)) {
                    aSum += count.get(j);
                }
            }
            aSums.add((double) aSum);
        }
        double abTotal = aSums.stream()
                .map(a_i -> (a_i * a_i) / (edgeCount * edgeCount))
                .reduce(Double::sum).get();

        Map<String, Integer> internalCount = edges.stream()
                .filter(Edge::isInternal)
                .map(e -> e.node1)
                .collect(Collectors.toMap(n -> n, n -> 1, Integer::sum));
        int eSum = 0;
        for (String i : allPackages) {
            eSum += internalCount.getOrDefault(i, 0);
        }
        double e = ((double) eSum) / edgeCount;
        return (e - abTotal) / (1 - abTotal);
    }


    private CompilationUnit parse(Path path) {
        // ソースコードを読み込む
        String code;
        try {
            code = new String(Files.readAllBytes(path));
        } catch (IOException e) {
            // ファイルを読めない場合は存在しないものとして扱う
            return null;
        }

        // パースして AST を構築する
        CompilationUnit cu;
        try {
            Optional<CompilationUnit> result = parser.parse(code).getResult();
            if (result.isEmpty()) {
                return null;
            }
            cu = result.get();
        } catch (ParseProblemException e) {
            // パースできない場合は存在しないものとして扱う
            return null;
        }

        return cu;
    }

    private List<Path> listPaths() throws IOException {
        return Files.walk(projectDir.toPath())
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().contains("Test"))
                .collect(Collectors.toList());
    }

    private static class Edge {
        public final String node1;
        public final String node2;

        public Edge(String node1, String node2) {
            this.node1 = node1;
            this.node2 = node2;
        }

        public boolean contains(String node) {
            return node1.equals(node) || node2.equals(node);
        }

        public String getCounterPart(String node) {
            if (node.equals(node1)) {
                return node2;
            } else if (node.equals(node2)) {
                return node1;
            } else {
                return null;
            }
        }

        public boolean isInternal() {
            return node1.equals(node2);
        }
    }

    private static class EdgeCollectorVisitor extends VoidVisitorAdapter<Object> {
        private final List<String> packageCalls = new ArrayList<>();
        private final Set<String> allPackages;
        private String sourcePackage;

        public EdgeCollectorVisitor(Set<String> allPackages) {
            this.allPackages = allPackages;
        }

        @Override
        public void visit(PackageDeclaration n, Object arg) {
            this.sourcePackage = n.getNameAsString();
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Object arg) {
            super.visit(n, arg);
            List<ClassOrInterfaceType> parentTypes = new ArrayList<>();
            parentTypes.addAll(n.getExtendedTypes());
            parentTypes.addAll(n.getImplementedTypes());
            for (ClassOrInterfaceType parentType : parentTypes) {
                ResolvedReferenceType resolvedType;
                try {
                    resolvedType = parentType.resolve();
                } catch (Exception | StackOverflowError e) {
                    continue;
                }
                resolvedType.getTypeDeclaration().ifPresent(t -> {
                  String packageName = t.getPackageName();
                  if (allPackages.contains(packageName)) {
                      packageCalls.add(packageName);
                  }
                });
            }
        }

        @Override
        public void visit(MethodCallExpr n, Object arg) {
            super.visit(n, arg);
            addPackageCallNode(n);
        }

        @Override
        public void visit(FieldAccessExpr n, Object arg) {
            super.visit(n, arg);
            addPackageCallNode(n);
        }

        private void addPackageCallNode(Node n) {
            if (n instanceof MethodCallExpr) {
                try {
                    ResolvedMethodDeclaration m = ((MethodCallExpr) n).resolve();
                    if (m instanceof JavaParserMethodDeclaration) {
                        MethodDeclaration methodDecl = m.toAst().get();
                        String packageName = m.getPackageName();
                        if (allPackages.contains(packageName)) {
                            packageCalls.add(packageName);
                        }
                    }
                } catch (Exception | StackOverflowError e) {
//                    System.out.println(e.getMessage());
                }
            } else if (n instanceof FieldAccessExpr) {
                try {
                    ResolvedValueDeclaration d = ((FieldAccessExpr) n).resolve();
                    if (d instanceof JavaParserFieldDeclaration) {
                        VariableDeclarator varDecl = ((JavaParserFieldDeclaration) d).getVariableDeclarator();
                        String packageName = ((JavaParserFieldDeclaration) d).declaringType().getPackageName();
                        if (allPackages.contains(packageName)) {
                            packageCalls.add(packageName);
                        }
                    }
                } catch (Exception | StackOverflowError e) {
//                    System.out.println(e.getMessage());
                }
            }
        }

        public List<Edge> getEdges() {
            if (sourcePackage == null) {
                return Collections.emptyList();
            }
            return packageCalls.stream()
                    .map(dst -> new Edge(sourcePackage, dst))
                    .collect(Collectors.toList());
        }
    }
}
