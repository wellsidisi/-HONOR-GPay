package com.honorwallet.gpay.lsp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** 跑一条 shell 命令（可选 root）。不抛异常，结果都放在 Result 里。 */
public final class Shell {

    private static final int TIMEOUT_SECONDS = 20;

    public static final class Result {
        public final boolean ok;
        public final int code;
        public final String output;

        Result(boolean ok, int code, String output) {
            this.ok = ok;
            this.code = code;
            this.output = output;
        }

        @Override
        public String toString() {
            return "ok=" + ok + " code=" + code + " out=" + output;
        }
    }

    private Shell() {
    }

    public static Result su(String command) {
        return exec(new String[]{"su", "-c", command});
    }

    public static Result sh(String command) {
        return exec(new String[]{"sh", "-c", command});
    }

    private static Result exec(String[] argv) {
        Process p = null;
        try {
            p = new ProcessBuilder(argv).redirectErrorStream(true).start();
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Result(false, -1, "timeout after " + TIMEOUT_SECONDS + "s");
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(line);
                }
            }
            int code = p.exitValue();
            return new Result(code == 0, code, sb.toString().trim());
        } catch (IOException e) {
            return new Result(false, -1, e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, -1, "interrupted");
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }
}
