package com.xiaoju.basetech.util;

/**
 * @description:
 * @author: gaoweiwei_v
 * @time: 2019/6/20 4:28 PM
 */

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.CloneCommand;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Paths;

@Component
public class GitHandler {
    static final Logger logger = LoggerFactory.getLogger(GitHandler.class);

    @Value(value = "${gitlab.username}")
    private  String username;

    @Value(value = "${gitlab.password}")
    private  String password;

    public Git cloneRepository(String gitUrl, String codePath, String commitId) throws GitAPIException {
        CloneCommand cloneCommand = Git.cloneRepository()
                .setURI(gitUrl)
                .setDirectory(new File(codePath))
                .setCloneAllBranches(true);

        if (shouldUseCredentialsProvider(gitUrl) && !StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
            cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password));
        }

        Git git = cloneCommand.call();
        if (!StringUtils.isEmpty(commitId)) {
            checkoutBranch(git, commitId);
        }
        return git;
    }

    private static boolean shouldUseCredentialsProvider(String gitUrl) {
        if (StringUtils.isEmpty(gitUrl)) {
            return false;
        }
        if (gitUrl.startsWith("git@")) {
            return false;
        }
        try {
            URI uri = URI.create(gitUrl);
            if (uri.getScheme() == null) {
                return false;
            }
            return uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https");
        } catch (Exception e) {
            return false;
        }
    }

    private static Ref checkoutBranch(Git git, String branch) {
        try {
            return git.checkout()
                    .setName(branch)
                    .call();
        } catch (GitAPIException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isValidGitRepository(String codePath) {
        Path folder = Paths.get(codePath);
        if (Files.exists(folder) && Files.isDirectory(folder)) {
            // If it has been at least initialized
            if (RepositoryCache.FileKey.isGitRepository(folder.toFile(), FS.DETECTED)) {
                // we are assuming that the clone worked at that time, caller should call hasAtLeastOneReference
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }


}
