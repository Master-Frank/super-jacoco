package com.frank.superjacoco.util;

/**
 * @description:
 * @author: gaoweiwei_v
 * @time: 2019/8/27 4:17 PM
 */

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.frank.superjacoco.entity.CoverageReportEntity;
import com.frank.superjacoco.entity.ModuleInfo;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class MavenModuleUtil {

    public void addMavenModule(CoverageReportEntity coverageReport) {
        try {
            Path workDir = resolveMavenWorkDir(coverageReport);
            String pomPath = workDir.resolve("pom.xml").toString();
            File pomFile = new File(pomPath);
            if (!pomFile.exists()) {
                coverageReport.setRequestStatus(Constants.JobStatus.FAILADDMODULE.val());
                return;
            }
            // 添加lombok配置
            File lombokConfig = workDir.resolve("lombok.config").toFile();
            FileWriter lombokWriter = new FileWriter(lombokConfig);
            lombokWriter.write("lombok.addLombokGeneratedAnnotation = true");
            lombokWriter.flush();
            lombokWriter.close();
            replaceArgLine(pomPath);
            coverageReport.setRequestStatus(Constants.JobStatus.ADDMODULE_DONE.val());
            return;
        } catch (Exception e) {
            log.error("添加集成模块执行异常:{}", coverageReport.getUuid(), e);
            coverageReport.setErrMsg("添加集成模块执行异常:" + e.getMessage());
            coverageReport.setRequestStatus(Constants.JobStatus.FAILADDMODULE.val());
        }
    }

    private static Path resolveMavenWorkDir(CoverageReportEntity coverageReport) {
        Path root = Paths.get(coverageReport.getNowLocalPath()).toAbsolutePath().normalize();
        String subModule = coverageReport.getSubModule();
        if (StringUtils.isEmpty(subModule)) {
            return root;
        }
        Path candidate = root.resolve(subModule).toAbsolutePath().normalize();
        if (!candidate.startsWith(root)) {
            return root;
        }
        if (Files.exists(candidate.resolve("pom.xml"))) {
            return candidate;
        }
        return root;
    }

    public static void replaceArgLine(String pomPath) {
        File pomFile = new File(pomPath);
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader parentbReader = new BufferedReader(new FileReader(pomFile))) {
                String s;
                while ((s = parentbReader.readLine()) != null) {
                    sb.append(s).append("\n");
                }
            }

            String pomStr = sb.toString();
            String updated = pomStr;

            if (updated.contains("<argLine>") && !updated.contains("<argLine>@{argLine}")) {
                updated = updated.replace("<argLine>", "<argLine>@{argLine} ");
            }

            updated = ensureSurefireArgLine(updated);

            if (!updated.equals(pomStr)) {
                try (FileWriter writer = new FileWriter(pomFile)) {
                    writer.write(updated);
                    writer.flush();
                }
            }
        } catch (IOException e) {
            log.error("replaceArgLineError", e);
        }

    }

    private static String ensureSurefireArgLine(String pomStr) {
        if (StringUtils.isEmpty(pomStr)) {
            return pomStr;
        }

        String updated = pomStr;

        Pattern pluginPattern = Pattern.compile("(?s)<plugin>.*?<artifactId>maven-surefire-plugin</artifactId>.*?</plugin>");
        Matcher matcher = pluginPattern.matcher(updated);
        if (matcher.find()) {
            String pluginBlock = matcher.group();
            String newPluginBlock = pluginBlock;

            if (pluginBlock.contains("<argLine>")) {
                if (!pluginBlock.contains("<argLine>@{argLine}")) {
                    newPluginBlock = newPluginBlock.replace("<argLine>", "<argLine>@{argLine} ");
                }
            } else if (pluginBlock.contains("<configuration>")) {
                newPluginBlock = newPluginBlock.replace(
                        "<configuration>",
                        "<configuration>\n<argLine>@{argLine}</argLine>\n"
                );
            } else {
                newPluginBlock = newPluginBlock.replace(
                        "</plugin>",
                        "<configuration>\n<argLine>@{argLine}</argLine>\n</configuration>\n</plugin>"
                );
            }

            if (!newPluginBlock.equals(pluginBlock)) {
                updated = updated.substring(0, matcher.start()) + newPluginBlock + updated.substring(matcher.end());
            }
            return updated;
        }

        String pluginToInsert = "<plugin>\n"
                + "<groupId>org.apache.maven.plugins</groupId>\n"
                + "<artifactId>maven-surefire-plugin</artifactId>\n"
                + "<version>2.22.1</version>\n"
                + "<configuration>\n"
                + "<argLine>@{argLine}</argLine>\n"
                + "</configuration>\n"
                + "</plugin>\n";

        int pluginMgmtIdx = updated.indexOf("<pluginManagement>");
        if (pluginMgmtIdx >= 0) {
            int pluginsIdx = updated.indexOf("<plugins>", pluginMgmtIdx);
            if (pluginsIdx >= 0) {
                int insertPos = pluginsIdx + "<plugins>".length();
                return updated.substring(0, insertPos) + "\n" + pluginToInsert + updated.substring(insertPos);
            }
        }

        int buildIdx = updated.indexOf("<build>");
        if (buildIdx >= 0) {
            int pluginsIdx = updated.indexOf("<plugins>", buildIdx);
            if (pluginsIdx >= 0) {
                int insertPos = pluginsIdx + "<plugins>".length();
                return updated.substring(0, insertPos) + "\n" + pluginToInsert + updated.substring(insertPos);
            }
            int buildEndIdx = updated.indexOf("</build>", buildIdx);
            if (buildEndIdx >= 0) {
                String pluginsBlock = "<plugins>\n" + pluginToInsert + "</plugins>\n";
                return updated.substring(0, buildEndIdx) + pluginsBlock + updated.substring(buildEndIdx);
            }
        }

        int projectEndIdx = updated.lastIndexOf("</project>");
        if (projectEndIdx >= 0) {
            String buildBlock = "<build>\n<plugins>\n" + pluginToInsert + "</plugins>\n</build>\n";
            return updated.substring(0, projectEndIdx) + buildBlock + updated.substring(projectEndIdx);
        }

        return updated;
    }

    //获取一个moduleGAV等基本信息
    public static ModuleInfo getModuleInfo(String pomFile) {
        ModuleInfo moduleInfo = new ModuleInfo();
        SAXReader reader = new SAXReader();
        try {
            Document document = reader.read(new File(pomFile));
            Element root = document.getRootElement();
            Element ee = root.element("parent");
            if (ee != null) {
                if (ee.element("version") != null) {
                    moduleInfo.setParentVersion(ee.element("version").getText());
                    moduleInfo.setVersion(moduleInfo.getParentVersion());
                }
                if (ee.element("groupId") != null) {
                    moduleInfo.setParentGroupId(ee.element("groupId").getText());
                    moduleInfo.setGroupId(moduleInfo.getParentGroupId());

                }
                if (ee.element("artifactId") != null) {
                    moduleInfo.setParentArtifactId(ee.element("artifactId").getText());
                    moduleInfo.setArtifactId(moduleInfo.getParentArtifactId());
                }
            }
            if (root.element("properties") != null) {
                moduleInfo.setProperties(root.element("properties"));
            }
            if (root.element("packaging") != null) {
                moduleInfo.setPackaging(root.element("packaging").getText());
            }
            int i = 0;
            for (Iterator<Element> it = root.elementIterator(); it.hasNext(); ) {
                Element e = it.next();
                if (i < 3) {
                    if (e.getName().equals("version")) {
                        moduleInfo.setVersion(e.getText());
                        i++;
                    } else if (e.getName().equals("groupId")) {
                        moduleInfo.setGroupId(e.getText());
                        i++;
                    } else if (e.getName().equals("artifactId")) {
                        moduleInfo.setArtifactId(e.getText());
                        i++;
                    }
                }else {
                    break;
                }
            }
            if(!StringUtils.isEmpty(moduleInfo.getVersion())&&!StringUtils.isEmpty(moduleInfo.getGroupId())
                    &&!StringUtils.isEmpty(moduleInfo.getArtifactId())){
                moduleInfo.setFlag(true);
            }
        } catch (Exception e) {
            log.error("getModuleInfo failed, pomPath={}", pomFile, e);
        }
        return moduleInfo;

    }

    //获取所有的子模块pom文件路径
    public static ArrayList<String> getChildPomsPath(String pomPath) {
        File dir = new File(pomPath).getParentFile();
        ArrayList<String> list = new ArrayList<>();
        if (!dir.exists()) {
            return list;
        }
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                File[] childFiles = file.listFiles();
                for (File childFile : childFiles) {
                    if (childFile.getName().equals("pom.xml")) {
                        list.add(childFile.getAbsolutePath());
                    }
                }
            }
        }

        return list;

    }

    //获取pom中有效的modules列表
    public static ArrayList<String> getValidModules(String pomPath) {
        ArrayList<String> validModuleList = new ArrayList<>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(pomPath));
            StringBuilder sb = new StringBuilder();
            String s;
            while ((s = reader.readLine()) != null) {
                sb.append(s.trim());
            }
            String pomStr = sb.toString();
            String moduleregex = "<modules>.*?</modules>";
            Pattern modulepattern = Pattern.compile(moduleregex);
            Matcher moduleM = modulepattern.matcher(pomStr);
            String modules;
            if (moduleM.find()) {
                modules = moduleM.group();
                modules = modules.replaceAll("<!--.*?<module>.*?</module>.*?-->", ",");
                modules = modules.replaceAll("</?modules?>", ",");
                String[] module = modules.split(",");
                for (String m : module) {
                    if (!m.equals("")) {
                        validModuleList.add(m);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            log.error("pom not found, pomPath={}", pomPath, e);
        } catch (IOException e) {
            log.error("read pom failed, pomPath={}", pomPath, e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                log.error("close reader failed, pomPath={}", pomPath, e);
            }
        }
        return validModuleList;
    }


    public static StringBuilder dependencyStr(String pomPath, ModuleInfo moduleInfo, StringBuilder dependencyBuilder) {
        //moduleInfo是pomFile的GAV等信息
        if (moduleInfo.getPackaging() == null || moduleInfo.getPackaging().equals("jar")) {
            if (moduleInfo.getArtifactId() != null) {
                String groupId = getModuleGroupId(moduleInfo);
                String version = getModuleVersion(moduleInfo);
                StringBuilder sb = new StringBuilder("<dependency>\n");
                sb.append("<artifactId>" + moduleInfo.getArtifactId() + "</artifactId>\n");
                if (groupId != null) {
                    sb.append("<groupId>" + groupId + "</groupId>\n");
                }
                if (version != null) {
                    sb.append("<version>" + version + "</version>\n");
                }
                sb.append("</dependency>\n");
                dependencyBuilder.append(sb.toString());
            }
        } else {
            ArrayList<String> validModuleList = getValidModules(pomPath);
            for (int i = 0; i < validModuleList.size(); i++) {
                String childPom = new File(pomPath).getParent() + "/" + validModuleList.get(i) + "/pom.xml";
                ModuleInfo moduleInfoChild = getModuleInfo(childPom);
                if (moduleInfo.isFlag()) {
                    replaceArgLine(childPom);
                    moduleInfoChild.setParent(moduleInfo);
                    moduleInfoChild.setFlag(true);
                    dependencyBuilder = dependencyStr(childPom, moduleInfoChild, dependencyBuilder);
                }
            }
        }
        return dependencyBuilder;
    }


    public static String getModuleGroupId(ModuleInfo moduleInfo) {
        String groupId = moduleInfo.getGroupId();
        if (groupId == null) {
            if (moduleInfo.getParent() != null) {
                groupId = getModuleGroupId(moduleInfo.getParent());
            }
        }
        return groupId;

    }

    public static String getModuleVersion(ModuleInfo moduleInfo) {
        String version = moduleInfo.getVersion();
        if (version != null && version.contains("$")) {
            ModuleInfo moduleInfo1 = moduleInfo;
            String versionName = version.replace("$", "").replace("{", "").replace("}", "");
            while (moduleInfo1.getParent() != null) {
                Element properties = moduleInfo1.getParent().getProperties();
                if (properties != null && properties.element(versionName) != null) {
                    version = properties.element(versionName).getText();
                    return version;
                } else {
                    moduleInfo1 = moduleInfo1.getParent();
                }
            }

        } else if (version == null) {
            version = moduleInfo.getParentVersion();
        }

        return version;

    }
}
