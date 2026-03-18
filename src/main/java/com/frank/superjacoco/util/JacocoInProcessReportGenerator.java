package com.frank.superjacoco.util;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.ICoverageNode;
import org.jacoco.core.analysis.ICoverageNode.CounterEntity;
import org.jacoco.core.analysis.ICoverageNode.ElementType;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ICounter.CounterValue;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.analysis.IPackageCoverage;
import org.jacoco.core.analysis.ISourceFileCoverage;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.MultiSourceFileLocator;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;
import org.jacoco.report.FileMultiReportOutput;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JacocoInProcessReportGenerator {

    public static final class ReportResult {
        private final double lineCoveragePercent;
        private final double branchCoveragePercent;
        private final ICounter lineCounter;
        private final ICounter branchCounter;
        private final Path htmlIndex;
        private final Path xmlFile;

        private ReportResult(double lineCoveragePercent, double branchCoveragePercent, ICounter lineCounter, ICounter branchCounter, Path htmlIndex, Path xmlFile) {
            this.lineCoveragePercent = lineCoveragePercent;
            this.branchCoveragePercent = branchCoveragePercent;
            this.lineCounter = lineCounter;
            this.branchCounter = branchCounter;
            this.htmlIndex = htmlIndex;
            this.xmlFile = xmlFile;
        }

        public double getLineCoveragePercent() {
            return lineCoveragePercent;
        }

        public double getBranchCoveragePercent() {
            return branchCoveragePercent;
        }

        public ICounter getLineCounter() {
            return lineCounter;
        }

        public ICounter getBranchCounter() {
            return branchCounter;
        }

        public Path getHtmlIndex() {
            return htmlIndex;
        }

        public Path getXmlFile() {
            return xmlFile;
        }
    }

    public static ReportResult generateHtmlAndXml(
            Path workDir,
            List<Path> execFiles,
            List<Path> classDirs,
            List<Path> sourceDirs,
            String moduleName,
            String reportName,
            String diffSpec,
            Path htmlOutputDir
    ) throws IOException {
        Path normalizedWorkDir = workDir.toAbsolutePath().normalize();
        Path normalizedOutputDir = htmlOutputDir.toAbsolutePath().normalize();
        if (!normalizedOutputDir.startsWith(normalizedWorkDir)) {
            throw new IllegalArgumentException("htmlOutputDir must be under workDir");
        }

        SafeFileOps.deleteRecursively(normalizedWorkDir, normalizedOutputDir);
        Files.createDirectories(normalizedOutputDir);

        ExecFileLoader execFileLoader = new ExecFileLoader();
        for (Path execFile : execFiles) {
            if (execFile != null && Files.exists(execFile)) {
                execFileLoader.load(execFile.toFile());
            }
        }

        CoverageBuilder coverageBuilder = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(execFileLoader.getExecutionDataStore(), coverageBuilder);
        for (Path classDir : classDirs) {
            if (classDir != null && Files.exists(classDir)) {
                analyzer.analyzeAll(classDir.toFile());
            }
        }

        IBundleCoverage bundle;
        if (diffSpec == null || diffSpec.trim().isEmpty()) {
            bundle = coverageBuilder.getBundle(reportName);
        } else {
            bundle = buildFilteredBundle(coverageBuilder, moduleName, reportName, diffSpec);
        }

        MultiSourceFileLocator locator = new MultiSourceFileLocator(4);
        for (Path sourceDir : sourceDirs) {
            if (sourceDir != null && Files.exists(sourceDir)) {
                locator.add(new DirectorySourceFileLocator(sourceDir.toFile(), "UTF-8", 4));
            }
        }

        Path xmlFile = normalizedOutputDir.resolve("jacoco.xml");

        HTMLFormatter htmlFormatter = new HTMLFormatter();
        IReportVisitor htmlVisitor = htmlFormatter.createVisitor(new FileMultiReportOutput(normalizedOutputDir.toFile()));
        htmlVisitor.visitInfo(execFileLoader.getSessionInfoStore().getInfos(), execFileLoader.getExecutionDataStore().getContents());
        htmlVisitor.visitBundle(bundle, locator);
        htmlVisitor.visitEnd();

        XMLFormatter xmlFormatter = new XMLFormatter();
        try (OutputStream out = new FileOutputStream(xmlFile.toFile())) {
            IReportVisitor xmlVisitor = xmlFormatter.createVisitor(out);
            xmlVisitor.visitInfo(execFileLoader.getSessionInfoStore().getInfos(), execFileLoader.getExecutionDataStore().getContents());
            xmlVisitor.visitBundle(bundle, locator);
            xmlVisitor.visitEnd();
        }

        ICounter line = bundle.getLineCounter();
        ICounter branch = bundle.getBranchCounter();
        double linePercent = percent(line);
        double branchPercent = percent(branch);

        Path htmlIndex = normalizedOutputDir.resolve("index.html");
        return new ReportResult(linePercent, branchPercent, line, branch, htmlIndex, xmlFile);
    }

    public static ReportResult generateXmlOnly(
            Path workDir,
            List<Path> execFiles,
            List<Path> classDirs,
            List<Path> sourceDirs,
            String moduleName,
            String reportName,
            String diffSpec,
            Path xmlFile
    ) throws IOException {
        Path normalizedWorkDir = workDir.toAbsolutePath().normalize();
        Path normalizedXmlFile = xmlFile.toAbsolutePath().normalize();
        if (!normalizedXmlFile.startsWith(normalizedWorkDir)) {
            throw new IllegalArgumentException("xmlFile must be under workDir");
        }
        Files.createDirectories(normalizedXmlFile.getParent());

        ExecFileLoader execFileLoader = new ExecFileLoader();
        for (Path execFile : execFiles) {
            if (execFile != null && Files.exists(execFile)) {
                execFileLoader.load(execFile.toFile());
            }
        }

        CoverageBuilder coverageBuilder = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(execFileLoader.getExecutionDataStore(), coverageBuilder);
        for (Path classDir : classDirs) {
            if (classDir != null && Files.exists(classDir)) {
                analyzer.analyzeAll(classDir.toFile());
            }
        }

        IBundleCoverage bundle;
        if (diffSpec == null || diffSpec.trim().isEmpty()) {
            bundle = coverageBuilder.getBundle(reportName);
        } else {
            bundle = buildFilteredBundle(coverageBuilder, moduleName, reportName, diffSpec);
        }

        MultiSourceFileLocator locator = new MultiSourceFileLocator(4);
        for (Path sourceDir : sourceDirs) {
            if (sourceDir != null && Files.exists(sourceDir)) {
                locator.add(new DirectorySourceFileLocator(sourceDir.toFile(), "UTF-8", 4));
            }
        }

        XMLFormatter xmlFormatter = new XMLFormatter();
        try (OutputStream out = new FileOutputStream(normalizedXmlFile.toFile())) {
            IReportVisitor xmlVisitor = xmlFormatter.createVisitor(out);
            xmlVisitor.visitInfo(execFileLoader.getSessionInfoStore().getInfos(), execFileLoader.getExecutionDataStore().getContents());
            xmlVisitor.visitBundle(bundle, locator);
            xmlVisitor.visitEnd();
        }

        ICounter line = bundle.getLineCounter();
        ICounter branch = bundle.getBranchCounter();
        double linePercent = percent(line);
        double branchPercent = percent(branch);

        return new ReportResult(linePercent, branchPercent, line, branch, null, normalizedXmlFile);
    }

    public static List<Path> findJacocoExecFiles(Path workDir) throws IOException {
        if (workDir == null || !Files.exists(workDir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.walk(workDir)) {
            return stream
                    .filter(p -> p.getFileName() != null)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("jacoco.exec"))
                    .collect(Collectors.toList());
        }
    }

    private static IBundleCoverage buildFilteredBundle(CoverageBuilder coverageBuilder, String moduleName, String reportName, String diffSpec) {
        Map<String, DiffRule> diffRules = DiffSpec.parse(diffSpec);
        Map<String, DiffRule> ruleBySourceFileKey = mapRulesToSourceFileKeys(coverageBuilder, diffRules, moduleName);

        Map<String, List<IClassCoverage>> classesBySourceFileKey = new HashMap<>();
        for (IClassCoverage cc : coverageBuilder.getClasses()) {
            String sourceFile = cc.getSourceFileName();
            if (sourceFile == null || sourceFile.trim().isEmpty()) {
                continue;
            }
            String pkg = cc.getPackageName();
            String key = pkg == null || pkg.isEmpty() ? sourceFile : (pkg + "/" + sourceFile);
            classesBySourceFileKey.computeIfAbsent(key, k -> new ArrayList<>()).add(cc);
        }

        Map<String, List<FilteredClassCoverage>> classesByPackage = new LinkedHashMap<>();
        Map<String, List<FilteredSourceFileCoverage>> sourcesByPackage = new LinkedHashMap<>();

        for (ISourceFileCoverage sfc : coverageBuilder.getSourceFiles()) {
            String pkg = sfc.getPackageName();
            String sourceKey = pkg == null || pkg.isEmpty() ? sfc.getName() : (pkg + "/" + sfc.getName());

            DiffRule rule = ruleBySourceFileKey.get(sourceKey);
            if (rule == null) {
                continue;
            }

            List<IClassCoverage> classCoverages = classesBySourceFileKey.getOrDefault(sourceKey, Collections.emptyList());
            List<FilteredClassCoverage> filteredClasses = new ArrayList<>();
            Set<Integer> includedLines = new HashSet<>();
            List<IMethodCoverage> selectedMethodsAll = new ArrayList<>();

            for (IClassCoverage cc : classCoverages) {
                List<IMethodCoverage> selectedMethods = selectMethods(cc, rule);
                if (selectedMethods.isEmpty()) {
                    continue;
                }
                selectedMethodsAll.addAll(selectedMethods);
                for (IMethodCoverage mc : selectedMethods) {
                    int first = mc.getFirstLine();
                    int last = mc.getLastLine();
                    if (first > 0 && last >= first) {
                        for (int l = first; l <= last; l++) {
                            includedLines.add(l);
                        }
                    }
                }
                filteredClasses.add(new FilteredClassCoverage(cc, selectedMethods));
            }

            if (filteredClasses.isEmpty()) {
                continue;
            }

            Map<CounterEntity, ICounter> sourceCounters = CoverageCounters.aggregateSourceFile(filteredClasses, selectedMethodsAll);
            FilteredSourceFileCoverage filteredSource = new FilteredSourceFileCoverage(sfc, includedLines, sourceCounters);

            String packageName = sfc.getPackageName() == null ? "" : sfc.getPackageName();
            classesByPackage.computeIfAbsent(packageName, k -> new ArrayList<>()).addAll(filteredClasses);
            sourcesByPackage.computeIfAbsent(packageName, k -> new ArrayList<>()).add(filteredSource);
        }

        List<IPackageCoverage> packages = new ArrayList<>();
        Set<String> packageNames = new HashSet<>();
        packageNames.addAll(classesByPackage.keySet());
        packageNames.addAll(sourcesByPackage.keySet());

        for (String pkg : packageNames) {
            List<FilteredClassCoverage> cls = classesByPackage.getOrDefault(pkg, Collections.emptyList());
            List<FilteredSourceFileCoverage> src = sourcesByPackage.getOrDefault(pkg, Collections.emptyList());
            if (cls.isEmpty() && src.isEmpty()) {
                continue;
            }
            packages.add(new FilteredPackageCoverage(pkg, cls, src));
        }

        return new FilteredBundleCoverage(reportName, packages);
    }

    private static List<IMethodCoverage> selectMethods(IClassCoverage classCoverage, DiffRule rule) {
        if (rule.includeAll) {
            return new ArrayList<>(classCoverage.getMethods());
        }

        Map<String, List<IMethodCoverage>> bySig = new HashMap<>();
        for (IMethodCoverage mc : classCoverage.getMethods()) {
            String sig = MethodSig.toDiffSig(mc);
            if (sig == null || sig.isEmpty()) {
                continue;
            }
            bySig.computeIfAbsent(sig, k -> new ArrayList<>()).add(mc);
        }

        List<IMethodCoverage> selected = new ArrayList<>();
        for (String desired : rule.methodSignatures) {
            List<IMethodCoverage> matches = bySig.get(desired);
            if (matches != null) {
                selected.addAll(matches);
            }
        }
        return selected;
    }

    private static Map<String, DiffRule> mapRulesToSourceFileKeys(CoverageBuilder builder, Map<String, DiffRule> rawRules, String moduleName) {
        Set<String> existingSourceKeys = new HashSet<>();
        for (ISourceFileCoverage sfc : builder.getSourceFiles()) {
            String pkg = sfc.getPackageName();
            String key = pkg == null || pkg.isEmpty() ? sfc.getName() : (pkg + "/" + sfc.getName());
            existingSourceKeys.add(key);
        }

        String modulePrefix = moduleName == null ? "" : moduleName.trim();
        String modulePrefixNorm = modulePrefix.replace('\\', '/');
        if (!modulePrefixNorm.isEmpty() && !modulePrefixNorm.endsWith("/")) {
            modulePrefixNorm = modulePrefixNorm + "/";
        }

        Map<String, DiffRule> mapped = new HashMap<>();
        for (Map.Entry<String, DiffRule> entry : rawRules.entrySet()) {
            String rawKey = entry.getKey();
            if (rawKey == null) {
                continue;
            }
            String k = rawKey.replace('\\', '/');
            if (!modulePrefixNorm.isEmpty()) {
                if (!k.startsWith(modulePrefixNorm)) {
                    continue;
                }
                k = k.substring(modulePrefixNorm.length());
            }

            String sourceRel = toMainJavaRelPath(k);
            if (sourceRel != null) {
                String sourceKey = sourceRel + ".java";
                mergeRule(mapped, sourceKey, entry.getValue());
                continue;
            }

            for (String existing : existingSourceKeys) {
                String existingNoExt = existing.endsWith(".java") ? existing.substring(0, existing.length() - 5) : existing;
                if (k.endsWith(existingNoExt)) {
                    mergeRule(mapped, existing, entry.getValue());
                }
            }
        }

        return mapped;
    }

    private static String toMainJavaRelPath(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isEmpty()) {
            return null;
        }
        String p = normalizedPath;
        if (p.startsWith("/")) {
            p = p.substring(1);
        }

        String needle = "src/main/java/";
        int idx = p.indexOf(needle);
        if (idx >= 0) {
            return p.substring(idx + needle.length());
        }
        if (p.startsWith(needle)) {
            return p.substring(needle.length());
        }
        return null;
    }

    private static void mergeRule(Map<String, DiffRule> mapped, String sourceKey, DiffRule value) {
        if (sourceKey == null || sourceKey.isEmpty() || value == null) {
            return;
        }
        DiffRule existing = mapped.get(sourceKey);
        if (existing == null) {
            mapped.put(sourceKey, value);
            return;
        }
        mapped.put(sourceKey, existing.merge(value));
    }

    private static double percent(ICounter counter) {
        if (counter == null) {
            return 100.0;
        }
        int total = counter.getMissedCount() + counter.getCoveredCount();
        if (total <= 0) {
            return 100.0;
        }
        return ((double) counter.getCoveredCount()) * 100.0 / ((double) total);
    }

    private static final class DiffRule {
        private final boolean includeAll;
        private final Set<String> methodSignatures;

        private DiffRule(boolean includeAll, Set<String> methodSignatures) {
            this.includeAll = includeAll;
            this.methodSignatures = methodSignatures == null ? Collections.emptySet() : methodSignatures;
        }

        private DiffRule merge(DiffRule other) {
            if (other == null) {
                return this;
            }
            if (this.includeAll || other.includeAll) {
                return new DiffRule(true, Collections.emptySet());
            }
            Set<String> merged = new HashSet<>(this.methodSignatures);
            merged.addAll(other.methodSignatures);
            return new DiffRule(false, merged);
        }
    }

    private static final class DiffSpec {
        private static Map<String, DiffRule> parse(String diffSpec) {
            if (diffSpec == null || diffSpec.trim().isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, DiffRule> result = new HashMap<>();
            String normalized = diffSpec.replace("\r\n", "\n").replace("\r", "\n");
            String[] records = normalized.split("%");
            for (String record : records) {
                if (record == null) {
                    continue;
                }
                String trimmed = record.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.contains("\n")) {
                    String[] lines = trimmed.split("\\n");
                    for (String line : lines) {
                        parseOneLine(result, line);
                    }
                    continue;
                }
                parseOneLine(result, trimmed);
            }
            if (result.isEmpty()) {
                throw new IllegalArgumentException("diffSpec不合法：未解析出任何增量规则");
            }
            return result;
        }

        private static void parseOneLine(Map<String, DiffRule> result, String line) {
            if (result == null || line == null) {
                return;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                return;
            }
                int colon = trimmed.indexOf(':');
                if (colon <= 0 || colon >= trimmed.length() - 1) {
                    return;
                }
                String fileKey = trimmed.substring(0, colon).trim();
                String methodsPart = trimmed.substring(colon + 1).trim();

                if (methodsPart.isEmpty()) {
                    return;
                }
                if (methodsPart.equals("%") || methodsPart.equalsIgnoreCase("true")) {
                    mergeRule(result, fileKey, new DiffRule(true, Collections.emptySet()));
                    return;
                }
                while (methodsPart.endsWith("#")) {
                    methodsPart = methodsPart.substring(0, methodsPart.length() - 1);
                }

                String[] methodItems = methodsPart.split("#");
                Set<String> methods = new HashSet<>();
                for (String m : methodItems) {
                    if (m == null) {
                        continue;
                    }
                    String ms = m.trim();
                    if (!ms.isEmpty() && !ms.equals("%")) {
                        methods.add(ms);
                    }
                }
                if (!methods.isEmpty()) {
                    mergeRule(result, fileKey, new DiffRule(false, methods));
                }
        }
    }

    private static final class MethodSig {
        private static String toDiffSig(IMethodCoverage mc) {
            if (mc == null) {
                return "";
            }

            String name = mc.getName();
            String desc = null;
            try {
                desc = mc.getDesc();
            } catch (Throwable ignored) {
            }
            if (name == null || name.isEmpty() || desc == null || desc.isEmpty()) {
                return name == null ? "" : name;
            }

            List<String> params = Descriptor.parseParamTypeSimpleNames(desc);
            StringBuilder b = new StringBuilder();
            b.append(name);
            for (String p : params) {
                if (p == null || p.isEmpty()) {
                    continue;
                }
                if (Primitive.isPrimitiveSimpleName(p)) {
                    continue;
                }
                b.append(',');
                b.append(p);
            }
            return b.toString();
        }
    }

    private static final class Descriptor {
        private static List<String> parseParamTypeSimpleNames(String methodDesc) {
            if (methodDesc == null) {
                return Collections.emptyList();
            }
            int start = methodDesc.indexOf('(');
            int end = methodDesc.indexOf(')');
            if (start < 0 || end < 0 || end <= start) {
                return Collections.emptyList();
            }
            String params = methodDesc.substring(start + 1, end);
            if (params.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> result = new ArrayList<>();
            int i = 0;
            while (i < params.length()) {
                char c = params.charAt(i);
                if (c == 'L') {
                    int semi = params.indexOf(';', i);
                    if (semi < 0) {
                        break;
                    }
                    String internal = params.substring(i + 1, semi);
                    String simple = simpleNameFromInternal(internal);
                    result.add(simple);
                    i = semi + 1;
                    continue;
                }
                if (c == '[') {
                    int arrayStart = i;
                    while (i < params.length() && params.charAt(i) == '[') {
                        i++;
                    }
                    if (i >= params.length()) {
                        break;
                    }
                    char elem = params.charAt(i);
                    if (elem == 'L') {
                        int semi = params.indexOf(';', i);
                        if (semi < 0) {
                            break;
                        }
                        String internal = params.substring(i + 1, semi);
                        String simple = simpleNameFromInternal(internal);
                        result.add(simple);
                        i = semi + 1;
                    } else {
                        String prim = Primitive.fromDescriptor(elem);
                        result.add(prim);
                        i++;
                    }
                    continue;
                }

                String prim = Primitive.fromDescriptor(c);
                result.add(prim);
                i++;
            }
            return result;
        }

        private static String simpleNameFromInternal(String internalName) {
            if (internalName == null || internalName.isEmpty()) {
                return "";
            }
            String normalized = internalName.replace('/', '.');
            int lastDot = normalized.lastIndexOf('.');
            if (lastDot >= 0 && lastDot < normalized.length() - 1) {
                return normalized.substring(lastDot + 1);
            }
            return normalized;
        }
    }

    private static final class Primitive {
        private static boolean isPrimitiveSimpleName(String name) {
            if (name == null) {
                return false;
            }
            String n = name.toLowerCase(Locale.ROOT);
            return n.equals("boolean") || n.equals("byte") || n.equals("char") || n.equals("short") || n.equals("int")
                    || n.equals("long") || n.equals("float") || n.equals("double") || n.equals("void");
        }

        private static String fromDescriptor(char desc) {
            switch (desc) {
                case 'Z':
                    return "boolean";
                case 'B':
                    return "byte";
                case 'C':
                    return "char";
                case 'S':
                    return "short";
                case 'I':
                    return "int";
                case 'J':
                    return "long";
                case 'F':
                    return "float";
                case 'D':
                    return "double";
                case 'V':
                    return "void";
                default:
                    return "";
            }
        }
    }

    private static final class SimpleCounter implements ICounter {
        private final int missed;
        private final int covered;

        private SimpleCounter(int missed, int covered) {
            this.missed = Math.max(0, missed);
            this.covered = Math.max(0, covered);
        }

        @Override
        public double getValue(CounterValue value) {
            if (value == null) {
                return 0.0;
            }
            switch (value) {
                case TOTALCOUNT:
                    return getTotalCount();
                case MISSEDCOUNT:
                    return getMissedCount();
                case COVEREDCOUNT:
                    return getCoveredCount();
                case MISSEDRATIO:
                    return getMissedRatio();
                case COVEREDRATIO:
                    return getCoveredRatio();
                default:
                    return 0.0;
            }
        }

        @Override
        public int getMissedCount() {
            return missed;
        }

        @Override
        public int getCoveredCount() {
            return covered;
        }

        @Override
        public int getTotalCount() {
            return missed + covered;
        }

        @Override
        public double getMissedRatio() {
            int total = getTotalCount();
            return total == 0 ? 0.0 : ((double) missed) / ((double) total);
        }

        @Override
        public double getCoveredRatio() {
            int total = getTotalCount();
            return total == 0 ? 0.0 : ((double) covered) / ((double) total);
        }

        @Override
        public int getStatus() {
            if (missed > 0 && covered > 0) {
                return ICounter.PARTLY_COVERED;
            }
            if (covered > 0) {
                return ICounter.FULLY_COVERED;
            }
            if (missed > 0) {
                return ICounter.NOT_COVERED;
            }
            return ICounter.EMPTY;
        }

        private static ICounter add(ICounter a, ICounter b) {
            if (a == null) {
                return b == null ? new SimpleCounter(0, 0) : new SimpleCounter(b.getMissedCount(), b.getCoveredCount());
            }
            if (b == null) {
                return new SimpleCounter(a.getMissedCount(), a.getCoveredCount());
            }
            return new SimpleCounter(a.getMissedCount() + b.getMissedCount(), a.getCoveredCount() + b.getCoveredCount());
        }

        private static ICounter add(ICounter a, int missedCount, int coveredCount) {
            if (a == null) {
                return new SimpleCounter(missedCount, coveredCount);
            }
            return new SimpleCounter(a.getMissedCount() + missedCount, a.getCoveredCount() + coveredCount);
        }
    }

    private static final class SimpleLine implements ILine {
        private static final ILine EMPTY = new SimpleLine(new SimpleCounter(0, 0), new SimpleCounter(0, 0), ICounter.EMPTY);

        private final ICounter instruction;
        private final ICounter branch;
        private final int status;

        private SimpleLine(ICounter instruction, ICounter branch, int status) {
            this.instruction = instruction == null ? new SimpleCounter(0, 0) : instruction;
            this.branch = branch == null ? new SimpleCounter(0, 0) : branch;
            this.status = status;
        }

        @Override
        public ICounter getInstructionCounter() {
            return instruction;
        }

        @Override
        public ICounter getBranchCounter() {
            return branch;
        }

        @Override
        public int getStatus() {
            return status;
        }
    }

    private static final class PlainCoverageNode implements ICoverageNode {
        private final ElementType elementType;
        private final String name;
        private final Map<CounterEntity, ICounter> counters;

        private PlainCoverageNode(ElementType elementType, String name, Map<CounterEntity, ICounter> counters) {
            this.elementType = elementType == null ? ElementType.BUNDLE : elementType;
            this.name = name == null ? "" : name;
            this.counters = counters == null ? Collections.emptyMap() : counters;
        }

        @Override
        public ElementType getElementType() {
            return elementType;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ICounter getInstructionCounter() {
            return getCounter(CounterEntity.INSTRUCTION);
        }

        @Override
        public ICounter getBranchCounter() {
            return getCounter(CounterEntity.BRANCH);
        }

        @Override
        public ICounter getLineCounter() {
            return getCounter(CounterEntity.LINE);
        }

        @Override
        public ICounter getComplexityCounter() {
            return getCounter(CounterEntity.COMPLEXITY);
        }

        @Override
        public ICounter getMethodCounter() {
            return getCounter(CounterEntity.METHOD);
        }

        @Override
        public ICounter getClassCounter() {
            return getCounter(CounterEntity.CLASS);
        }

        @Override
        public ICounter getCounter(CounterEntity entity) {
            return counters.getOrDefault(entity, new SimpleCounter(0, 0));
        }

        @Override
        public boolean containsCode() {
            return getInstructionCounter().getTotalCount() > 0 || getBranchCounter().getTotalCount() > 0;
        }

        @Override
        public ICoverageNode getPlainCopy() {
            return new PlainCoverageNode(elementType, name, counters);
        }
    }

    private static final class FilteredBundleCoverage implements IBundleCoverage {
        private final String name;
        private final List<IPackageCoverage> packages;
        private final Map<CounterEntity, ICounter> counters;

        private FilteredBundleCoverage(String name, List<IPackageCoverage> packages) {
            this.name = name == null ? "" : name;
            this.packages = packages == null ? Collections.emptyList() : packages;
            this.counters = CoverageCounters.aggregate(packages);
        }

        @Override
        public ElementType getElementType() {
            return ElementType.BUNDLE;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Collection<IPackageCoverage> getPackages() {
            return packages;
        }

        @Override
        public ICounter getInstructionCounter() {
            return getCounter(CounterEntity.INSTRUCTION);
        }

        @Override
        public ICounter getBranchCounter() {
            return getCounter(CounterEntity.BRANCH);
        }

        @Override
        public ICounter getLineCounter() {
            return getCounter(CounterEntity.LINE);
        }

        @Override
        public ICounter getComplexityCounter() {
            return getCounter(CounterEntity.COMPLEXITY);
        }

        @Override
        public ICounter getMethodCounter() {
            return getCounter(CounterEntity.METHOD);
        }

        @Override
        public ICounter getClassCounter() {
            return getCounter(CounterEntity.CLASS);
        }

        @Override
        public ICounter getCounter(CounterEntity entity) {
            return counters.getOrDefault(entity, new SimpleCounter(0, 0));
        }

        @Override
        public boolean containsCode() {
            return getInstructionCounter().getTotalCount() > 0 || getBranchCounter().getTotalCount() > 0;
        }

        @Override
        public ICoverageNode getPlainCopy() {
            return new PlainCoverageNode(getElementType(), getName(), counters);
        }
    }

    private static final class FilteredPackageCoverage implements IPackageCoverage {
        private final String name;
        private final List<IClassCoverage> classes;
        private final List<ISourceFileCoverage> sourceFiles;
        private final Map<CounterEntity, ICounter> counters;

        private FilteredPackageCoverage(String name, List<FilteredClassCoverage> classes, List<FilteredSourceFileCoverage> sourceFiles) {
            this.name = name == null ? "" : name;
            this.classes = classes == null ? Collections.emptyList() : new ArrayList<>(classes);
            this.sourceFiles = sourceFiles == null ? Collections.emptyList() : new ArrayList<>(sourceFiles);
            this.counters = CoverageCounters.aggregateNodes(this.classes, this.sourceFiles);
        }

        @Override
        public ElementType getElementType() {
            return ElementType.PACKAGE;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Collection<IClassCoverage> getClasses() {
            return classes;
        }

        @Override
        public Collection<ISourceFileCoverage> getSourceFiles() {
            return sourceFiles;
        }

        @Override
        public ICounter getInstructionCounter() {
            return getCounter(CounterEntity.INSTRUCTION);
        }

        @Override
        public ICounter getBranchCounter() {
            return getCounter(CounterEntity.BRANCH);
        }

        @Override
        public ICounter getLineCounter() {
            return getCounter(CounterEntity.LINE);
        }

        @Override
        public ICounter getComplexityCounter() {
            return getCounter(CounterEntity.COMPLEXITY);
        }

        @Override
        public ICounter getMethodCounter() {
            return getCounter(CounterEntity.METHOD);
        }

        @Override
        public ICounter getClassCounter() {
            return getCounter(CounterEntity.CLASS);
        }

        @Override
        public ICounter getCounter(CounterEntity entity) {
            return counters.getOrDefault(entity, new SimpleCounter(0, 0));
        }

        @Override
        public boolean containsCode() {
            return getInstructionCounter().getTotalCount() > 0 || getBranchCounter().getTotalCount() > 0;
        }

        @Override
        public ICoverageNode getPlainCopy() {
            return new PlainCoverageNode(getElementType(), getName(), counters);
        }
    }

    private static final class FilteredClassCoverage implements IClassCoverage {
        private final IClassCoverage delegate;
        private final List<IMethodCoverage> methods;
        private final Map<CounterEntity, ICounter> counters;

        private FilteredClassCoverage(IClassCoverage delegate, List<IMethodCoverage> methods) {
            this.delegate = Objects.requireNonNull(delegate);
            this.methods = methods == null ? Collections.emptyList() : new ArrayList<>(methods);
            this.counters = CoverageCounters.aggregateMethods(this.methods);
        }

        @Override
        public long getId() {
            return delegate.getId();
        }

        @Override
        public boolean isNoMatch() {
            return delegate.isNoMatch();
        }

        @Override
        public String getSignature() {
            return delegate.getSignature();
        }

        @Override
        public String getSuperName() {
            return delegate.getSuperName();
        }

        @Override
        public String[] getInterfaceNames() {
            return delegate.getInterfaceNames();
        }

        @Override
        public String getPackageName() {
            return delegate.getPackageName();
        }

        @Override
        public String getSourceFileName() {
            return delegate.getSourceFileName();
        }

        @Override
        public Collection<IMethodCoverage> getMethods() {
            return methods;
        }

        @Override
        public ElementType getElementType() {
            return ElementType.CLASS;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public ICounter getInstructionCounter() {
            return getCounter(CounterEntity.INSTRUCTION);
        }

        @Override
        public ICounter getBranchCounter() {
            return getCounter(CounterEntity.BRANCH);
        }

        @Override
        public ICounter getLineCounter() {
            return getCounter(CounterEntity.LINE);
        }

        @Override
        public ICounter getComplexityCounter() {
            return getCounter(CounterEntity.COMPLEXITY);
        }

        @Override
        public ICounter getMethodCounter() {
            return getCounter(CounterEntity.METHOD);
        }

        @Override
        public ICounter getClassCounter() {
            return getCounter(CounterEntity.CLASS);
        }

        @Override
        public ICounter getCounter(CounterEntity entity) {
            return counters.getOrDefault(entity, new SimpleCounter(0, 0));
        }

        @Override
        public boolean containsCode() {
            return getInstructionCounter().getTotalCount() > 0 || getBranchCounter().getTotalCount() > 0;
        }

        @Override
        public ICoverageNode getPlainCopy() {
            return new PlainCoverageNode(getElementType(), getName(), counters);
        }

        @Override
        public int getFirstLine() {
            return delegate.getFirstLine();
        }

        @Override
        public int getLastLine() {
            return delegate.getLastLine();
        }

        @Override
        public ILine getLine(int nr) {
            ILine line = delegate.getLine(nr);
            return line == null ? SimpleLine.EMPTY : line;
        }
    }

    private static final class FilteredSourceFileCoverage implements ISourceFileCoverage {
        private final ISourceFileCoverage delegate;
        private final Set<Integer> includedLines;
        private final Map<CounterEntity, ICounter> counters;
        private final int firstLine;
        private final int lastLine;

        private FilteredSourceFileCoverage(ISourceFileCoverage delegate, Set<Integer> includedLines, Map<CounterEntity, ICounter> counters) {
            this.delegate = Objects.requireNonNull(delegate);
            this.includedLines = includedLines == null ? Collections.emptySet() : includedLines;
            int first = -1;
            int last = -1;
            for (Integer line : this.includedLines) {
                if (line == null) {
                    continue;
                }
                int l = line;
                if (first < 0 || l < first) {
                    first = l;
                }
                if (last < 0 || l > last) {
                    last = l;
                }
            }
            this.counters = counters == null ? Collections.emptyMap() : counters;
            this.firstLine = first;
            this.lastLine = last;
        }

        @Override
        public ElementType getElementType() {
            return ElementType.SOURCEFILE;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getPackageName() {
            return delegate.getPackageName();
        }

        @Override
        public ICounter getInstructionCounter() {
            return getCounter(CounterEntity.INSTRUCTION);
        }

        @Override
        public ICounter getBranchCounter() {
            return getCounter(CounterEntity.BRANCH);
        }

        @Override
        public ICounter getLineCounter() {
            return getCounter(CounterEntity.LINE);
        }

        @Override
        public ICounter getComplexityCounter() {
            return getCounter(CounterEntity.COMPLEXITY);
        }

        @Override
        public ICounter getMethodCounter() {
            return getCounter(CounterEntity.METHOD);
        }

        @Override
        public ICounter getClassCounter() {
            return getCounter(CounterEntity.CLASS);
        }

        @Override
        public ICounter getCounter(CounterEntity entity) {
            return counters.getOrDefault(entity, new SimpleCounter(0, 0));
        }

        @Override
        public boolean containsCode() {
            return getInstructionCounter().getTotalCount() > 0 || getBranchCounter().getTotalCount() > 0;
        }

        @Override
        public ICoverageNode getPlainCopy() {
            return new PlainCoverageNode(getElementType(), getName(), counters);
        }

        @Override
        public int getFirstLine() {
            return firstLine;
        }

        @Override
        public int getLastLine() {
            return lastLine;
        }

        @Override
        public ILine getLine(int nr) {
            if (!includedLines.contains(nr)) {
                return SimpleLine.EMPTY;
            }
            ILine line = delegate.getLine(nr);
            return line == null ? SimpleLine.EMPTY : line;
        }
    }

    private static final class CoverageCounters {
        private static Map<CounterEntity, ICounter> aggregate(Collection<IPackageCoverage> packages) {
            Map<CounterEntity, ICounter> m = new HashMap<>();
            for (CounterEntity e : CounterEntity.values()) {
                m.put(e, new SimpleCounter(0, 0));
            }
            if (packages == null) {
                return m;
            }
            for (IPackageCoverage p : packages) {
                if (p == null) {
                    continue;
                }
                for (CounterEntity e : CounterEntity.values()) {
                    m.put(e, SimpleCounter.add(m.get(e), p.getCounter(e)));
                }
            }
            return m;
        }

        private static Map<CounterEntity, ICounter> aggregateNodes(Collection<IClassCoverage> classes, Collection<ISourceFileCoverage> sources) {
            Map<CounterEntity, ICounter> m = new HashMap<>();
            for (CounterEntity e : CounterEntity.values()) {
                m.put(e, new SimpleCounter(0, 0));
            }
            if (classes != null) {
                for (IClassCoverage c : classes) {
                    for (CounterEntity e : CounterEntity.values()) {
                        m.put(e, SimpleCounter.add(m.get(e), c.getCounter(e)));
                    }
                }
            }
            if (sources != null) {
                for (ISourceFileCoverage s : sources) {
                    for (CounterEntity e : CounterEntity.values()) {
                        m.put(e, SimpleCounter.add(m.get(e), s.getCounter(e)));
                    }
                }
            }
            return m;
        }

        private static Map<CounterEntity, ICounter> aggregateMethods(Collection<IMethodCoverage> methods) {
            ICounter instruction = new SimpleCounter(0, 0);
            ICounter branch = new SimpleCounter(0, 0);
            ICounter line = new SimpleCounter(0, 0);
            ICounter complexity = new SimpleCounter(0, 0);
            int methodMissed = 0;
            int methodCovered = 0;

            if (methods != null) {
                for (IMethodCoverage mc : methods) {
                    instruction = SimpleCounter.add(instruction, mc.getInstructionCounter());
                    branch = SimpleCounter.add(branch, mc.getBranchCounter());
                    line = SimpleCounter.add(line, mc.getLineCounter());
                    complexity = SimpleCounter.add(complexity, mc.getComplexityCounter());
                    int status = mc.getInstructionCounter().getStatus();
                    if (status == ICounter.FULLY_COVERED || status == ICounter.PARTLY_COVERED) {
                        methodCovered++;
                    } else if (status == ICounter.NOT_COVERED) {
                        methodMissed++;
                    }
                }
            }
            ICounter methodCounter = new SimpleCounter(methodMissed, methodCovered);
            int classStatus = instruction.getStatus();
            boolean classCovered = classStatus == ICounter.FULLY_COVERED || classStatus == ICounter.PARTLY_COVERED;
            ICounter classCounter = methods == null || methods.isEmpty() ? new SimpleCounter(0, 0) : (classCovered ? new SimpleCounter(0, 1) : new SimpleCounter(1, 0));

            Map<CounterEntity, ICounter> m = new HashMap<>();
            m.put(CounterEntity.INSTRUCTION, instruction);
            m.put(CounterEntity.BRANCH, branch);
            m.put(CounterEntity.LINE, line);
            m.put(CounterEntity.COMPLEXITY, complexity);
            m.put(CounterEntity.METHOD, methodCounter);
            m.put(CounterEntity.CLASS, classCounter);
            return m;
        }

        private static Map<CounterEntity, ICounter> aggregateSourceFile(List<FilteredClassCoverage> classes, List<IMethodCoverage> methods) {
            Map<CounterEntity, ICounter> m = aggregateMethods(methods);

            int classMissed = 0;
            int classCovered = 0;
            if (classes != null) {
                for (FilteredClassCoverage c : classes) {
                    if (c == null) {
                        continue;
                    }
                    int status = c.getInstructionCounter().getStatus();
                    if (status == ICounter.FULLY_COVERED || status == ICounter.PARTLY_COVERED) {
                        classCovered++;
                    } else if (status == ICounter.NOT_COVERED) {
                        classMissed++;
                    }
                }
            }
            m.put(CounterEntity.CLASS, new SimpleCounter(classMissed, classCovered));
            return m;
        }
    }
}
