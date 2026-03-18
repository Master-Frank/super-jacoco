package com.frank.superjacoco.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @description:
 * @author: copy from com.didichuxing.chefuqa.common.util;
 * @time: 2019/12/24 9:11 PM
 */
public class CmdExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(CmdExecutor.class);

    public static ExecuteResult execute(List<String> command, Path workDir, Long timeoutMs, Path logFile) throws Exception {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException();
        }
        if (timeoutMs == null || timeoutMs <= 0) {
            throw new IllegalArgumentException();
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        if (workDir != null) {
            pb.directory(workDir.toFile());
        }

        Process p = pb.start();
        TeeReader tee = new TeeReader(p.getInputStream(), logFile);
        tee.start();
        boolean finished;
        try {
            finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw e;
        }

        if (!finished) {
            p.destroyForcibly();
            throw new TimeoutException();
        }

        tee.join(TimeUnit.SECONDS.toMillis(5));
        return new ExecuteResult(p.exitValue(), tee.getCaptured());
    }

    public static int executeCmd(List<String> command, Path workDir, Long timeoutMs, Path logFile) throws Exception {
        return execute(command, workDir, timeoutMs, logFile).getExitCode();
    }

    private static class TeeReader extends Thread {
        private static final Logger OUT_LOG = LoggerFactory.getLogger("commandOutputLogger");
        private static final int MAX_CAPTURE_CHARS = 64 * 1024;
        private final InputStream is;
        private final Path logFile;
        private final StringBuilder captured = new StringBuilder();

        private TeeReader(InputStream is, Path logFile) {
            super("cmd-tee");
            this.is = is;
            this.logFile = logFile;
        }

        @Override
        public void run() {
            Charset charset;
            try {
                charset = Charset.forName("GBK");
            } catch (Exception e) {
                charset = Charset.defaultCharset();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, charset));
                 BufferedWriter writer = logFile == null ? null : Files.newBufferedWriter(logFile, charset, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        OUT_LOG.info(trimmed);
                    }
                    if (writer != null) {
                        writer.write(line);
                        writer.newLine();
                        writer.flush();
                    }
                    appendLimited(captured, line + "\n");
                }
            } catch (IOException e) {
                LOG.error("command output read failed", e);
            }
        }

        public String getCaptured() {
            return captured.toString();
        }

        private void appendLimited(StringBuilder sb, String s) {
            if (s == null || s.isEmpty()) {
                return;
            }
            if (sb.length() >= MAX_CAPTURE_CHARS) {
                return;
            }
            int remaining = MAX_CAPTURE_CHARS - sb.length();
            if (s.length() <= remaining) {
                sb.append(s);
            } else {
                sb.append(s, 0, remaining);
            }
        }
    }
}
