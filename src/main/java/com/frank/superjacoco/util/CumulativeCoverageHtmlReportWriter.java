package com.frank.superjacoco.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frank.superjacoco.entity.CoverageSetEntity;
import com.frank.superjacoco.entity.CoverageSnapshotEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class CumulativeCoverageHtmlReportWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REPORT_DIR = "cumulative";

    public Path writeReport(Path reportRoot,
                            CoverageSetEntity set,
                            CoverageSnapshotEntity snapshot,
                            Map<String, Map<Integer, String>> sourceLines) throws IOException {
        Path setRoot = reportRoot.resolve(set.getCoverageSetId()).toAbsolutePath().normalize();
        Path outputDir = setRoot.resolve(REPORT_DIR);
        SafeFileOps.deleteRecursively(setRoot, outputDir);
        Files.createDirectories(outputDir.resolve("assets"));
        Files.createDirectories(outputDir.resolve("data/classes"));

        ReportSummary summary = buildSummary(set, snapshot, sourceLines);
        writeSummaryJson(outputDir, summary);
        writeClassJson(outputDir, summary);
        writeAssets(outputDir);
        writeTemplates(outputDir);
        return outputDir.resolve("index.html");
    }

    public String buildReportUrl(String coverageSetId) {
        return LocalIpUtils.getTomcatBaseUrl() + coverageSetId + "/" + REPORT_DIR + "/index.html";
    }

    private ReportSummary buildSummary(CoverageSetEntity set,
                                       CoverageSnapshotEntity snapshot,
                                       Map<String, Map<Integer, String>> sourceLines) {
        ReportSummary summary = new ReportSummary();
        summary.coverageSetId = set.getCoverageSetId();
        summary.branch = set.getBranch();
        summary.commitId = snapshot.getMetadata().getCommitId();

        Map<String, PackageSummary> packageMap = new LinkedHashMap<>();
        List<Map.Entry<String, CoverageSnapshotEntity.FileCoverage>> files = new ArrayList<>(snapshot.getFiles().entrySet());
        files.sort(Comparator.comparing(Map.Entry::getKey));
        for (Map.Entry<String, CoverageSnapshotEntity.FileCoverage> entry : files) {
            String sourceFile = entry.getKey();
            CoverageSnapshotEntity.FileCoverage fileCoverage = entry.getValue();
            String classKey = normalizeClassKey(sourceFile);
            String packageKey = packageKey(classKey);
            PackageSummary packageSummary = packageMap.computeIfAbsent(packageKey, this::newPackageSummary);
            ClassSummary classSummary = buildClassSummary(classKey, sourceFile, fileCoverage,
                    sourceLines.getOrDefault(sourceFile, Map.of()));
            packageSummary.classes.add(classSummary);
            packageSummary.covered += classSummary.covered;
            packageSummary.missed += classSummary.missed;
            packageSummary.branchCovered += classSummary.branchCovered;
            packageSummary.branchMissed += classSummary.branchMissed;
        }

        summary.packages = new ArrayList<>(packageMap.values());
        summary.packages.sort(Comparator.comparing(item -> item.packageKey));
        for (PackageSummary packageSummary : summary.packages) {
            packageSummary.coverageRate = rate(packageSummary.covered, packageSummary.missed);
            packageSummary.branchCoverageRate = rate(packageSummary.branchCovered, packageSummary.branchMissed);
            packageSummary.classes.sort(Comparator.comparing(item -> item.classKey));
            summary.covered += packageSummary.covered;
            summary.missed += packageSummary.missed;
            summary.branchCovered += packageSummary.branchCovered;
            summary.branchMissed += packageSummary.branchMissed;
        }
        summary.lineCoverageRate = rate(summary.covered, summary.missed);
        summary.branchCoverageRate = rate(summary.branchCovered, summary.branchMissed);
        return summary;
    }

    private ClassSummary buildClassSummary(String classKey,
                                           String sourceFile,
                                           CoverageSnapshotEntity.FileCoverage fileCoverage,
                                           Map<Integer, String> source) {
        ClassSummary classSummary = new ClassSummary();
        classSummary.classKey = classKey;
        classSummary.packageKey = packageKey(classKey);
        classSummary.displayName = sourceFile.substring(sourceFile.lastIndexOf('/') + 1);
        classSummary.sourceFile = sourceFile;

        int maxLineFromCoverage = 1;
        for (String lineNo : fileCoverage.getLines().keySet()) {
            maxLineFromCoverage = Math.max(maxLineFromCoverage, Integer.parseInt(lineNo));
        }
        int maxLineFromSource = 0;
        for (Integer lineNo : source.keySet()) {
            maxLineFromSource = Math.max(maxLineFromSource, lineNo);
        }
        int maxLine = maxLineFromSource > 0 ? maxLineFromSource : maxLineFromCoverage;

        for (int lineNo = 1; lineNo <= maxLine; lineNo++) {
            CoverageSnapshotEntity.LineCoverage lineCoverage = fileCoverage.getLines().get(String.valueOf(lineNo));
            LineSummary lineSummary = new LineSummary();
            lineSummary.lineNo = lineNo;
            lineSummary.text = source.getOrDefault(lineNo, "");
            if (lineCoverage == null) {
                lineSummary.status = "EMPTY";
                lineSummary.hits = 0;
                lineSummary.branchCovered = 0;
                lineSummary.branchMissed = 0;
            } else {
                lineSummary.status = lineCoverage.getStatus();
                lineSummary.hits = nullSafe(lineCoverage.getHits());
                lineSummary.branchCovered = nullSafe(lineCoverage.getBranchCovered());
                lineSummary.branchMissed = nullSafe(lineCoverage.getBranchMissed());
                if ("COVERED".equals(lineSummary.status)) {
                    classSummary.covered++;
                } else {
                    classSummary.missed++;
                }
                classSummary.branchCovered += lineSummary.branchCovered;
                classSummary.branchMissed += lineSummary.branchMissed;
            }
            classSummary.lines.add(lineSummary);
        }
        classSummary.coverageRate = rate(classSummary.covered, classSummary.missed);
        classSummary.branchCoverageRate = rate(classSummary.branchCovered, classSummary.branchMissed);
        return classSummary;
    }

    private void writeSummaryJson(Path outputDir, ReportSummary summary) throws IOException {
        writeJsonGzip(outputDir.resolve("data/summary.json.gz"), summary);
    }

    private void writeClassJson(Path outputDir, ReportSummary summary) throws IOException {
        for (PackageSummary packageSummary : summary.packages) {
            for (ClassSummary classSummary : packageSummary.classes) {
                Path jsonPath = outputDir.resolve("data/classes/" + classSummary.classKey.replace('.', '/') + ".json.gz");
                writeJsonGzip(jsonPath, classSummary);
            }
        }
    }

    private void writeJsonGzip(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(Files.newOutputStream(path))) {
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(gzipOutputStream, value);
        }
    }

    private void writeAssets(Path outputDir) throws IOException {
        Files.writeString(outputDir.resolve("assets/report.css"), css(), StandardCharsets.UTF_8);
        Files.writeString(outputDir.resolve("assets/app.js"), js(), StandardCharsets.UTF_8);
    }

    private void writeTemplates(Path outputDir) throws IOException {
        Files.writeString(outputDir.resolve("index.html"), pageTemplate("index", "\u7d2f\u8ba1\u8986\u76d6\u7387\u62a5\u544a"), StandardCharsets.UTF_8);
        Files.writeString(outputDir.resolve("package.html"), pageTemplate("package", "\u5305\u5217\u8868"), StandardCharsets.UTF_8);
        Files.writeString(outputDir.resolve("class.html"), pageTemplate("class", "\u6e90\u7801\u8be6\u60c5"), StandardCharsets.UTF_8);
    }

    private String pageTemplate(String pageType, String title) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                  <link rel="stylesheet" href="assets/report.css">
                </head>
                <body data-page="%s">
                  <div class="page">
                    <div class="topbar">
                      <div class="topbar-left">
                        <div id="nav" class="nav"></div>
                        <div id="breadcrumbs" class="breadcrumbs"></div>
                      </div>
                      <div id="actions" class="actions"></div>
                    </div>
                    <header class="hero">
                      <div>
                        <h1 id="page-title">%s</h1>
                        <p id="page-subtitle" class="subtitle"></p>
                      </div>
                      <div id="summary-cards" class="summary-cards"></div>
                    </header>
                    <main id="app" class="content"></main>
                  </div>
                  <script src="assets/app.js"></script>
                </body>
                </html>
                """.formatted(title, pageType, title);
    }

    private String css() {
        return """
                :root {
                  --bg: #f3f5f9;
                  --panel: #ffffff;
                  --text: #172133;
                  --muted: #5a6982;
                  --line: #d9dfeb;
                  --green-bg: #def4df;
                  --green-dot: #2fa44f;
                  --yellow-bg: #fbf0b8;
                  --yellow-dot: #d6a200;
                  --red-bg: #f9d8d8;
                  --red-dot: #d04a4a;
                  --empty-bg: #f6f8fb;
                  --link: #1a55d1;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  background: var(--bg);
                  color: var(--text);
                  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
                }
                .page {
                  max-width: 1280px;
                  margin: 0 auto;
                  padding: 20px 24px 32px;
                }
                .topbar {
                  display: flex;
                  justify-content: space-between;
                  gap: 16px;
                  align-items: center;
                  margin-bottom: 16px;
                }
                .topbar-left {
                  display: flex;
                  align-items: center;
                  gap: 10px;
                  flex-wrap: wrap;
                }
                .nav {
                  display: flex;
                  align-items: center;
                }
                .nav button {
                  border: 1px solid #cdd6e6;
                  background: var(--panel);
                  color: var(--text);
                  border-radius: 999px;
                  padding: 8px 14px;
                  font-size: 13px;
                  cursor: pointer;
                }
                .breadcrumbs {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 8px;
                  font-size: 14px;
                }
                .breadcrumbs a {
                  color: var(--link);
                  text-decoration: none;
                }
                .breadcrumbs .sep {
                  color: #8290a8;
                }
                .actions {
                  display: flex;
                  gap: 8px;
                  flex-wrap: wrap;
                }
                .actions button, .actions a {
                  border: 1px solid #cdd6e6;
                  background: var(--panel);
                  color: var(--text);
                  border-radius: 999px;
                  padding: 8px 14px;
                  font-size: 13px;
                  text-decoration: none;
                  cursor: pointer;
                }
                .hero {
                  display: flex;
                  justify-content: space-between;
                  gap: 24px;
                  align-items: flex-start;
                  margin-bottom: 20px;
                }
                .hero h1 {
                  margin: 0 0 8px;
                  font-size: 32px;
                }
                .subtitle {
                  margin: 0;
                  color: var(--muted);
                  font-size: 15px;
                }
                .summary-cards {
                  display: grid;
                  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                  gap: 12px;
                  min-width: 360px;
                }
                .summary-card {
                  background: rgba(255,255,255,0.9);
                  border: 1px solid var(--line);
                  border-radius: 18px;
                  padding: 14px 16px;
                }
                .summary-card strong {
                  display: block;
                  font-size: 24px;
                  margin-bottom: 6px;
                  max-width: 100%;
                  overflow: hidden;
                  text-overflow: ellipsis;
                  white-space: nowrap;
                }
                .summary-card span {
                  color: var(--muted);
                  font-size: 13px;
                }
                .panel {
                  background: var(--panel);
                  border: 1px solid var(--line);
                  border-radius: 18px;
                  overflow: hidden;
                }
                table {
                  width: 100%;
                  border-collapse: collapse;
                }
                th, td {
                  padding: 12px 14px;
                  border-bottom: 1px solid #edf1f7;
                  text-align: left;
                  font-size: 14px;
                }
                th {
                  background: #f8fafd;
                  color: #50617b;
                  font-weight: 600;
                }
                td a {
                  color: var(--link);
                  text-decoration: none;
                }
                .source-panel {
                  background: var(--panel);
                  border: 1px solid var(--line);
                  border-radius: 18px;
                  overflow: hidden;
                }
                .source-line {
                  display: grid;
                  grid-template-columns: 58px 28px minmax(0, 1fr);
                  font-family: Consolas, Monaco, "Courier New", monospace;
                  font-size: 13px;
                  line-height: 1.5;
                }
                .source-line > span {
                  padding: 3px 10px;
                  border-bottom: 1px solid #edf1f7;
                }
                .line-no {
                  color: #7b879c;
                  text-align: right;
                  background: #fafbfd;
                }
                .line-branch {
                  display: flex;
                  align-items: center;
                  justify-content: center;
                }
                .line-code {
                  white-space: pre-wrap;
                  word-break: break-word;
                }
                .line-covered > span { background: var(--green-bg); }
                .line-partial > span { background: var(--yellow-bg); }
                .line-missed > span { background: var(--red-bg); }
                .line-empty > span { background: var(--empty-bg); }
                .branch-dot {
                  width: 10px;
                  height: 10px;
                  border-radius: 999px;
                  display: inline-block;
                  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.10);
                }
                .branch-full { background: var(--green-dot); }
                .branch-partial { background: var(--yellow-dot); }
                .branch-none { background: var(--red-dot); }
                .empty-state {
                  padding: 40px 24px;
                  text-align: center;
                  color: var(--muted);
                }
                @media (max-width: 900px) {
                  .hero {
                    flex-direction: column;
                  }
                  .summary-cards {
                    min-width: 0;
                    width: 100%%;
                  }
                }
                """;
    }

    private String js() {
        return """
                (function () {
                  const DEFAULT_PACKAGE_TOKEN = '__default__';

                  function query(name) {
                    return new URLSearchParams(window.location.search).get(name);
                  }

                  function escapeHtml(value) {
                    return String(value ?? '')
                      .replace(/&/g, '&amp;')
                      .replace(/</g, '&lt;')
                      .replace(/>/g, '&gt;')
                      .replace(/"/g, '&quot;');
                  }

                  function pct(value) {
                    if (value === null || value === undefined) return '-';
                    return (value * 100).toFixed(2) + '%';
                  }

                  function labelForPackage(packageKey) {
                    return packageKey ? packageKey : '(default package)';
                  }

                  function summaryPath() {
                    return 'data/summary.json.gz';
                  }

                  function classJsonPath(classKey) {
                    return 'data/classes/' + classKey.replace(/\\./g, '/') + '.json.gz';
                  }

                  function setText(id, value) {
                    const element = document.getElementById(id);
                    if (element) element.textContent = value ?? '';
                  }

                  function setCards(items) {
                    const root = document.getElementById('summary-cards');
                    root.innerHTML = items.map(function (item) {
                      const value = item.value ?? '';
                      return '<div class="summary-card">' +
                        '<strong title="' + escapeHtml(value) + '">' + escapeHtml(value) + '</strong>' +
                        '<span>' + escapeHtml(item.label) + '</span>' +
                        '</div>';
                    }).join('');
                  }

                  function setBreadcrumbs(items) {
                    const root = document.getElementById('breadcrumbs');
                    root.innerHTML = items.map(function (item, index) {
                      const current = index === items.length - 1;
                      const part = current
                        ? '<span>' + escapeHtml(item.label) + '</span>'
                        : '<a href="' + escapeHtml(item.href) + '">' + escapeHtml(item.label) + '</a>';
                      return index === 0 ? part : '<span class="sep">&gt;</span>' + part;
                    }).join('');
                  }

                  function setActions(items) {
                    const backItems = items.filter(function (item) { return item.type === 'button' && item.action === 'back'; });
                    const nav = document.getElementById('nav');
                    if (nav) {
                      nav.innerHTML = backItems.map(function (item) {
                        return '<button type="button" data-action="' + escapeHtml(item.action) + '" data-fallback="' + escapeHtml(item.fallback || 'index.html') + '">' + escapeHtml(item.label) + '</button>';
                      }).join('');
                      nav.querySelectorAll('button[data-action="back"]').forEach(function (button) {
                        button.addEventListener('click', function () {
                          if (window.history.length > 1) {
                            window.history.back();
                          } else {
                            window.location.href = button.getAttribute('data-fallback') || 'index.html';
                          }
                        });
                      });
                    }

                    const root = document.getElementById('actions');
                    const rest = items.filter(function (item) { return !(item.type === 'button' && item.action === 'back'); });
                    root.innerHTML = rest.map(function (item) {
                      if (item.type === 'button') {
                        return '<button type="button" data-action="' + escapeHtml(item.action) + '" data-fallback="' + escapeHtml(item.fallback || 'index.html') + '">' + escapeHtml(item.label) + '</button>';
                      }
                      return '<a href="' + escapeHtml(item.href) + '">' + escapeHtml(item.label) + '</a>';
                    }).join('');
                  }

                  function fetchJson(path) {
                    return fetch(path, { cache: 'no-store' }).then(function (response) {
                      if (!response.ok) throw new Error('Failed to load ' + path);
                      const contentEncoding = (response.headers.get('content-encoding') || '').toLowerCase();
                      if (path.endsWith('.gz') && contentEncoding.indexOf('gzip') === -1) {
                        if (typeof DecompressionStream === 'undefined') {
                          throw new Error('Browser does not support gzip decompression');
                        }
                        return response.arrayBuffer().then(function (buffer) {
                          const decompressed = new Blob([buffer]).stream().pipeThrough(new DecompressionStream('gzip'));
                          return new Response(decompressed).json();
                        });
                      }
                      return response.json();
                    });
                  }

                  function renderTable(headers, rows) {
                    return '<div class="panel"><table><thead><tr>' +
                      headers.map(function (header) { return '<th>' + escapeHtml(header) + '</th>'; }).join('') +
                      '</tr></thead><tbody>' +
                      rows.join('') +
                      '</tbody></table></div>';
                  }

                  function branchMarker(line) {
                    const total = (line.branchCovered || 0) + (line.branchMissed || 0);
                    if (!total) return '';
                    if (line.branchCovered === total) {
                      return '<span class="branch-dot branch-full" title="\\u5168\\u90e8' + total + '\\u4e2a\\u5206\\u652f\\u5df2\\u8986\\u76d6"></span>';
                    }
                    const markerClass = line.branchCovered > 0 ? 'branch-partial' : 'branch-none';
                    return '<span class="branch-dot ' + markerClass + '" title="\\u5171' + total + '\\u4e2a\\u5206\\u652f\\uff0c\\u5df2\\u8986\\u76d6' + (line.branchCovered || 0) + '\\u4e2a\\u5206\\u652f"></span>';
                  }

                  function lineClass(line) {
                    const totalBranches = (line.branchCovered || 0) + (line.branchMissed || 0);
                    if (totalBranches > 0 && (line.branchCovered || 0) > 0 && (line.branchMissed || 0) > 0) {
                      return 'line-partial';
                    }
                    if (line.status === 'COVERED') return 'line-covered';
                    if (line.status === 'MISSED') return 'line-missed';
                    return 'line-empty';
                  }

                  function renderIndex(summary) {
                    setText('page-title', '\\u7d2f\\u8ba1\\u8986\\u76d6\\u7387\\u62a5\\u544a');
                    setText('page-subtitle', 'Commit: ' + (summary.commitId || '-') + '  Branch: ' + (summary.branch || '-'));
                    setBreadcrumbs([{ label: '\\u7d2f\\u8ba1\\u8986\\u76d6\\u7387\\u62a5\\u544a', href: 'index.html' }]);
                    setActions([]);
                    setCards([
                      { label: 'Packages', value: String(summary.packages.length) },
                      { label: 'Files', value: String(summary.packages.reduce(function (n, item) { return n + item.classes.length; }, 0)) },
                      { label: 'Line Coverage', value: pct(summary.lineCoverageRate) },
                      { label: 'Branch Coverage', value: pct(summary.branchCoverageRate) }
                    ]);

                    const rows = summary.packages.map(function (item) {
                      return '<tr>' +
                        '<td><a href="package.html?pkg=' + encodeURIComponent(item.packageKey || DEFAULT_PACKAGE_TOKEN) + '">' + escapeHtml(labelForPackage(item.packageKey)) + '</a></td>' +
                        '<td>' + item.classes.length + '</td>' +
                        '<td>' + item.covered + '</td>' +
                        '<td>' + item.missed + '</td>' +
                        '<td>' + pct(item.coverageRate) + '</td>' +
                        '</tr>';
                    });
                    document.getElementById('app').innerHTML = renderTable(['Package', 'Classes', 'Covered', 'Missed', 'Coverage'], rows);
                  }

                  function renderPackage(summary) {
                    const packageParam = query('pkg');
                    const packageKey = packageParam === DEFAULT_PACKAGE_TOKEN ? '' : (packageParam || '');
                    const pkg = summary.packages.find(function (item) { return item.packageKey === packageKey; });
                    if (!pkg) {
                      document.getElementById('app').innerHTML = '<div class="empty-state">\\u672a\\u627e\\u5230\\u5bf9\\u5e94\\u5305\\u6570\\u636e</div>';
                      return;
                    }
                    const packageLabel = labelForPackage(pkg.packageKey);
                    setText('page-title', packageLabel);
                    setText('page-subtitle', '\\u7c7b\\u5217\\u8868');
                    setBreadcrumbs([
                      { label: '\\u7d2f\\u8ba1\\u8986\\u76d6\\u7387\\u62a5\\u544a', href: 'index.html' },
                      { label: packageLabel, href: 'package.html?pkg=' + encodeURIComponent(pkg.packageKey || DEFAULT_PACKAGE_TOKEN) }
                    ]);
                    setActions([
                      { type: 'button', label: '\\u8fd4\\u56de\\u4e0a\\u4e00\\u9875', action: 'back', fallback: 'index.html' },
                      { type: 'link', label: '\\u5305\\u5217\\u8868', href: 'index.html' }
                    ]);
                    setCards([
                      { label: 'Classes', value: String(pkg.classes.length) },
                      { label: 'Covered', value: String(pkg.covered) },
                      { label: 'Missed', value: String(pkg.missed) },
                      { label: 'Coverage', value: pct(pkg.coverageRate) }
                    ]);
                    const rows = pkg.classes.map(function (item) {
                      return '<tr>' +
                        '<td><a href="class.html?class=' + encodeURIComponent(item.classKey) + '">' + escapeHtml(item.displayName) + '</a></td>' +
                        '<td>' + escapeHtml(item.classKey) + '</td>' +
                        '<td>' + item.covered + '</td>' +
                        '<td>' + item.missed + '</td>' +
                        '<td>' + pct(item.coverageRate) + '</td>' +
                        '</tr>';
                    });
                    document.getElementById('app').innerHTML = renderTable(['File', 'Class', 'Covered', 'Missed', 'Coverage'], rows);
                  }

                  function renderClass(summary, classData) {
                    const packageLabel = labelForPackage(classData.packageKey);
                    setText('page-title', classData.displayName);
                    setText('page-subtitle', classData.classKey);
                    setBreadcrumbs([
                      { label: '\\u7d2f\\u8ba1\\u8986\\u76d6\\u7387\\u62a5\\u544a', href: 'index.html' },
                      { label: packageLabel, href: 'package.html?pkg=' + encodeURIComponent(classData.packageKey || DEFAULT_PACKAGE_TOKEN) },
                      { label: classData.displayName, href: 'class.html?class=' + encodeURIComponent(classData.classKey) }
                    ]);
                    setActions([
                      { type: 'button', label: '\\u8fd4\\u56de\\u4e0a\\u4e00\\u9875', action: 'back', fallback: 'package.html?pkg=' + encodeURIComponent(classData.packageKey || DEFAULT_PACKAGE_TOKEN) },
                      { type: 'link', label: '\\u5305\\u5217\\u8868', href: 'index.html' },
                      { type: 'link', label: '\\u7c7b\\u5217\\u8868', href: 'package.html?pkg=' + encodeURIComponent(classData.packageKey || DEFAULT_PACKAGE_TOKEN) }
                    ]);
                    setCards([
                      { label: 'Commit', value: summary.commitId || '-' },
                      { label: 'Covered', value: String(classData.covered) },
                      { label: 'Missed', value: String(classData.missed) },
                      { label: 'Coverage', value: pct(classData.coverageRate) }
                    ]);
                    const lines = classData.lines.map(function (line) {
                      return '<div class="source-line ' + lineClass(line) + '">' +
                        '<span class="line-no">' + line.lineNo + '.</span>' +
                        '<span class="line-branch">' + branchMarker(line) + '</span>' +
                        '<span class="line-code">' + escapeHtml(line.text || ' ') + '</span>' +
                        '</div>';
                    }).join('');
                    document.getElementById('app').innerHTML = '<div class="source-panel">' + lines + '</div>';
                  }

                  fetchJson(summaryPath()).then(function (summary) {
                    const page = document.body.dataset.page;
                    if (page === 'index') {
                      renderIndex(summary);
                      return;
                    }
                    if (page === 'package') {
                      renderPackage(summary);
                      return;
                    }
                    if (page === 'class') {
                      const classKey = query('class');
                      if (!classKey) {
                        document.getElementById('app').innerHTML = '<div class="empty-state">\\u7f3a\\u5c11 class \\u53c2\\u6570</div>';
                        return;
                      }
                      fetchJson(classJsonPath(classKey)).then(function (classData) {
                        renderClass(summary, classData);
                      }).catch(function (error) {
                        document.getElementById('app').innerHTML = '<div class="empty-state">' + escapeHtml(error.message) + '</div>';
                      });
                    }
                  }).catch(function (error) {
                    document.getElementById('app').innerHTML = '<div class="empty-state">' + escapeHtml(error.message) + '</div>';
                  });
                }());
                """;
    }

    private String normalizeClassKey(String filePath) {
        String normalized = filePath.replace('\\', '/');
        if (normalized.endsWith(".java")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        return normalized.replace('/', '.');
    }

    private String packageKey(String classKey) {
        int idx = classKey.lastIndexOf('.');
        return idx < 0 ? "" : classKey.substring(0, idx);
    }

    private PackageSummary newPackageSummary(String packageKey) {
        PackageSummary summary = new PackageSummary();
        summary.packageKey = packageKey;
        summary.displayName = packageKey.isEmpty() ? "(default package)" : packageKey;
        return summary;
    }

    private double rate(int covered, int missed) {
        int total = covered + missed;
        if (total <= 0) {
            return 0D;
        }
        return ((double) covered) / total;
    }

    private int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }

    private static class ReportSummary {
        public String coverageSetId;
        public String branch;
        public String commitId;
        public Double lineCoverageRate;
        public Double branchCoverageRate;
        public int covered;
        public int missed;
        public int branchCovered;
        public int branchMissed;
        public List<PackageSummary> packages = new ArrayList<>();
    }

    private static class PackageSummary {
        public String packageKey;
        public String displayName;
        public int covered;
        public int missed;
        public int branchCovered;
        public int branchMissed;
        public double coverageRate;
        public double branchCoverageRate;
        public List<ClassSummary> classes = new ArrayList<>();
    }

    private static class ClassSummary {
        public String packageKey;
        public String classKey;
        public String displayName;
        public String sourceFile;
        public int covered;
        public int missed;
        public int branchCovered;
        public int branchMissed;
        public double coverageRate;
        public double branchCoverageRate;
        public List<LineSummary> lines = new ArrayList<>();
    }

    private static class LineSummary {
        public int lineNo;
        public String status;
        public int hits;
        public int branchMissed;
        public int branchCovered;
        public String text;
    }
}
