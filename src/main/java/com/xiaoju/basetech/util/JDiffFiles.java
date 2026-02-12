package com.xiaoju.basetech.util;


/**
 * @description:
 * @author: charlyne
 * @time: 2019/6/13 10:44 AM
 */


import com.xiaoju.basetech.entity.CoverageReportEntity;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JDiffFiles {

    static final Logger logger = LoggerFactory.getLogger(JDiffFiles.class);

    private static String workTreePath(Git git, String relPath) {
        if (git == null || relPath == null) {
            return null;
        }
        File workTree = git.getRepository() == null ? null : git.getRepository().getWorkTree();
        if (workTree != null) {
            return new File(workTree, relPath).getPath();
        }
        String parent = git.getRepository() == null || git.getRepository().getDirectory() == null
                ? null
                : git.getRepository().getDirectory().getParent();
        if (parent == null) {
            return null;
        }
        return parent + "/" + relPath;
    }

    public static HashMap<String, String> diffMethodsListNew(CoverageReportEntity coverageReport) {
        HashMap<String, String> map = new HashMap<>();

        if (coverageReport.getType() == Constants.ReportType.FULL.val()) {
            coverageReport.setRequestStatus(Constants.JobStatus.DIFF_METHOD_DONE.val());
            return map;
        }
        if (coverageReport.getBaseVersion().equals(coverageReport.getNowVersion())) {

            coverageReport.setErrMsg("两个commitid一致,没有增量代码");
            coverageReport.setRequestStatus(Constants.JobStatus.NODIFF.val());
            coverageReport.setReportUrl(Constants.NO_DIFFCODE_REPORT);
            coverageReport.setLineCoverage((double) 100);
            coverageReport.setBranchCoverage((double) 100);
            return map;
        }
        try {
            File newF = new File(coverageReport.getNowLocalPath());
            File oldF = new File(coverageReport.getBaseLocalPath());
            try (Git newGit = Git.open(newF); Git oldGit = Git.open(oldF)) {
                Repository newRepository = newGit.getRepository();
                Repository oldRepository = oldGit.getRepository();
                ObjectId baseObjId = oldRepository.resolve(coverageReport.getBaseVersion());
                ObjectId nowObjId = newRepository.resolve(coverageReport.getNowVersion());
                AbstractTreeIterator newTree = prepareTreeParser(newRepository, nowObjId);
                AbstractTreeIterator oldTree = prepareTreeParser(oldRepository, baseObjId);
                List<DiffEntry> diff = newGit.diff().setOldTree(oldTree).setNewTree(newTree).setShowNameAndStatusOnly(true).call();
                for (DiffEntry diffEntry : diff) {
                    if (diffEntry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                        continue;
                    }
                    if (diffEntry.getNewPath().indexOf(coverageReport.getSubModule()) < 0) {
                        continue;
                    }
                    if(diffEntry.getNewPath().indexOf("src/test/java")!=-1){
                        continue;
                    }
                    if (diffEntry.getNewPath().endsWith(".java")) {
                        String nowclassFile = diffEntry.getNewPath();
                        if (diffEntry.getChangeType() == DiffEntry.ChangeType.ADD) {
                            map.put(nowclassFile.replace(".java", ""), "true");
                        } else if (diffEntry.getChangeType() == DiffEntry.ChangeType.MODIFY) {
                            MethodParser methodParser = new MethodParser();
                            HashMap<String, String> baseMMap = methodParser.parseMethodsMd5(workTreePath(oldGit, nowclassFile));
                            HashMap<String, String> nowMMap = methodParser.parseMethodsMd5(workTreePath(newGit, nowclassFile));
                            HashMap<String, String> resMap = diffMethods(baseMMap, nowMMap);
                            if (resMap.isEmpty()) {
                                continue;
                            } else {
                                StringBuilder builder = new StringBuilder("");
                                for (String v : resMap.values()) {
                                    builder.append(v + "#");
                                }
                                map.put(nowclassFile.replace(".java", ""), builder.toString());
                            }
                        }
                    }
                }
            }
            if (map.isEmpty()) {
                coverageReport.setLineCoverage((double) 100);
                coverageReport.setBranchCoverage((double) 100);
                coverageReport.setRequestStatus(Constants.JobStatus.SUCCESS.val());
                coverageReport.setReportUrl(Constants.NO_DIFFCODE_REPORT);
                // 删除下载的代码
                try {
                    Path now = Paths.get(coverageReport.getNowLocalPath()).toAbsolutePath().normalize();
                    Path target = now.getParent();
                    if (target != null) {
                        Path root = target.getParent();
                        if (root != null) {
                            SafeFileOps.deleteRecursively(root, target);
                        }
                    }
                } catch (Exception e) {
                    logger.error("uuid={}删除下载代码失败", coverageReport.getUuid(), e);
                }
                coverageReport.setErrMsg("没有增量代码");
            } else {
                coverageReport.setRequestStatus(Constants.JobStatus.DIFF_METHOD_DONE.val());
            }
            return map;
        } catch (Exception e) {
            logger.error("计算增量方法出错uuid{}", coverageReport.getUuid(), e);
            coverageReport.setErrMsg("计算增量方法出错:" + e.getMessage());
            coverageReport.setRequestStatus(Constants.JobStatus.DIFF_METHOD_FAIL.val());
            return null;
        }
    }

    public static HashMap<String, String> diffMethodsListForEnv(String repoPath, String basePath, String nowPath, String baseVersion, String nowVersion) {
        HashMap<String, String> map = new HashMap<>();
        try {
            File repoDir = new File(repoPath);
            try (Git git = Git.open(repoDir)) {
                Repository repository = git.getRepository();
                ObjectId baseObjId = repository.resolve(baseVersion);
                ObjectId nowObjId = repository.resolve(nowVersion);
                AbstractTreeIterator newTree = prepareTreeParser(repository, nowObjId);
                AbstractTreeIterator oldTree = prepareTreeParser(repository, baseObjId);
                List<DiffEntry> diff = git.diff().setOldTree(oldTree).setNewTree(newTree).setShowNameAndStatusOnly(true).call();
                for (DiffEntry diffEntry : diff) {
                    if (diffEntry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                        continue;
                    }
                    if(diffEntry.getNewPath().indexOf("src/test/java")!=-1){
                        continue;
                    }
                    if (diffEntry.getNewPath().endsWith(".java")) {
                        String nowclassFile = diffEntry.getNewPath();
                        if (diffEntry.getChangeType() == DiffEntry.ChangeType.ADD) {
                            map.put(nowclassFile.replace(".java", ""), "true");
                        } else if (diffEntry.getChangeType() == DiffEntry.ChangeType.MODIFY
                                || diffEntry.getChangeType() == DiffEntry.ChangeType.RENAME
                                || diffEntry.getChangeType() == DiffEntry.ChangeType.COPY) {
                            MethodParser methodParser = new MethodParser();
                            String baseRel = diffEntry.getOldPath();
                            if (baseRel == null || baseRel.isEmpty() || DiffEntry.DEV_NULL.equals(baseRel)) {
                                baseRel = nowclassFile;
                            }
                            String baseFile = Paths.get(basePath, baseRel).toString();
                            String nowFile = Paths.get(nowPath, nowclassFile).toString();
                            HashMap<String, String> baseMMap = methodParser.parseMethodsMd5(baseFile);
                            HashMap<String, String> nowMMap = methodParser.parseMethodsMd5(nowFile);
                            HashMap<String, String> resMap = diffMethods(baseMMap, nowMMap);
                            if (resMap.isEmpty()) {
                                continue;
                            } else {
                                StringBuilder builder = new StringBuilder("");
                                for (String v : resMap.values()) {
                                    builder.append(v + "#");
                                }
                                map.put(nowclassFile.replace(".java", ""), builder.toString());
                            }
                        }
                    }
                }
            }
            return map;
        } catch (org.eclipse.jgit.errors.RepositoryNotFoundException e) {
            try {
                return diffMethodsListForEnvWithoutGit(Paths.get(basePath), Paths.get(nowPath));
            } catch (Exception ex) {
                logger.error("diffMethodsListForEnvWithoutGit failed", ex);
                return null;
            }
        } catch (Exception e) {
            logger.error("计算增量方法出错", e);
            return null;
        }
    }

    private static HashMap<String, String> diffMethodsListForEnvWithoutGit(Path baseRoot, Path nowRoot) throws IOException {
        HashMap<String, String> map = new HashMap<>();
        Path base = baseRoot.toAbsolutePath().normalize();
        Path now = nowRoot.toAbsolutePath().normalize();

        MethodParser methodParser = new MethodParser();
        try (java.util.stream.Stream<Path> stream = Files.walk(now)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null && p.getFileName().toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            Path rel = now.relativize(p);
                            String relUnix = rel.toString().replace('\\', '/');
                            if (relUnix.contains("src/test/java")) {
                                return;
                            }
                            Path baseFile = base.resolve(rel);
                            String key = relUnix.substring(0, relUnix.length() - ".java".length());
                            if (!Files.exists(baseFile)) {
                                map.put(key, "true");
                                return;
                            }
                            if (Files.mismatch(baseFile, p) == -1) {
                                return;
                            }
                            HashMap<String, String> baseMMap = methodParser.parseMethodsMd5(baseFile.toString());
                            HashMap<String, String> nowMMap = methodParser.parseMethodsMd5(p.toString());
                            HashMap<String, String> resMap = diffMethods(baseMMap, nowMMap);
                            if (resMap.isEmpty()) {
                                return;
                            }
                            StringBuilder builder = new StringBuilder("");
                            for (String v : resMap.values()) {
                                builder.append(v).append("#");
                            }
                            map.put(key, builder.toString());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        return map;
    }
    //这里找出来的diff是所有的java文件,不支持扫描指定目录
    public static HashMap<String, String> diffMethodsList(CoverageReportEntity coverageReport, Git baseGit, String baseCommitId, Git nowGit, String nowCommitId, String subModule) {
        HashMap<String, String> map = new HashMap<>();
        if (baseCommitId.equals(nowCommitId)) {
            return map;
        }
        try {
            ObjectId baseObjId = baseGit.getRepository().resolve(baseCommitId);
            ObjectId nowObjId = nowGit.getRepository().resolve(nowCommitId);
            AbstractTreeIterator baseTree = prepareTreeParser(baseGit.getRepository(), baseObjId);
            AbstractTreeIterator nowTree = prepareTreeParser(nowGit.getRepository(), nowObjId);
            List<DiffEntry> diff = nowGit.diff()
                    .setNewTree(nowTree)
                    .setOldTree(baseTree)
                    .setShowNameAndStatusOnly(true)
                    .call();
            for (DiffEntry diffEntry : diff) {
                if (diffEntry.getChangeType() == DiffEntry.ChangeType.DELETE) {
                    continue;
                }
                if (diffEntry.getNewPath().indexOf(subModule) < 0) {
                    continue;
                }
                if (diffEntry.getNewPath().endsWith(".java")) {
                    String nowclassFile = diffEntry.getNewPath();
                    if (diffEntry.getChangeType() == DiffEntry.ChangeType.ADD) {
                        map.put(nowclassFile.replace(".java", ""), "true");
                    } else if (diffEntry.getChangeType() == DiffEntry.ChangeType.MODIFY) {
                        MethodParser methodParser = new MethodParser();
                        HashMap<String, String> baseMMap = methodParser.parseMethodsMd5(workTreePath(baseGit, nowclassFile));
                        HashMap<String, String> nowMMap = methodParser.parseMethodsMd5(workTreePath(nowGit, nowclassFile));
                        HashMap<String, String> resMap = diffMethods(baseMMap, nowMMap);
                        if (resMap.isEmpty()) {
                            continue;
                        } else {
                            StringBuilder builder = new StringBuilder("");
                            for (String v : resMap.values()) {
                                builder.append(v + "#");
                            }
                            map.put(nowclassFile.replace(".java", ""), builder.toString());
                        }
                    }
                }
            }
            return map;
        } catch (Exception e) {
            coverageReport.setRequestStatus(Constants.JobStatus.GENERATEREPORT_FAIL.val());
        }
        return null;
    }

    public static HashMap<String, String> diffMethods(HashMap<String, String> baseMMap, HashMap<String, String> nowMMap) {
        HashMap<String, String> resMap = new HashMap<>();
        Iterator<String> iterator = nowMMap.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if (!baseMMap.containsKey(key)) {
                resMap.put(key, nowMMap.get(key));
            }
        }
        return resMap;
    }


    public static AbstractTreeIterator prepareTreeParser(Repository repository, AnyObjectId objectId) throws IOException {
        try {
            RevTree tree;
            try (RevWalk walk = new RevWalk(repository)) {
                RevObject any = walk.parseAny(objectId);
                if (any instanceof RevCommit) {
                    tree = ((RevCommit) any).getTree();
                } else if (any instanceof RevTree) {
                    tree = (RevTree) any;
                } else {
                    throw new IOException("Unsupported git object type: " + any.getType());
                }
            }
            CanonicalTreeParser TreeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                TreeParser.reset(reader, tree.getId());
            }
            return TreeParser;
        } catch (Exception e) {
            logger.error("prepareTreeParser failed", e);
            throw new IOException("prepareTreeParser failed", e);
        }
    }
}
