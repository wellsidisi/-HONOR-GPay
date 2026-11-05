package com.honorwallet.gpay.lsp;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XposedBridge;

/** 统一日志：logcat（XposedBridge.log）+ 尽力写一个文件，方便用户直接贴回来。 */
public final class DevLog {

    public static final String TAG = "HWG";

    private static volatile String sFile;
    private static boolean sFileResolved;
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private DevLog() {
    }

    public static void i(String msg) {
        log("I", msg, null);
    }

    public static void e(String msg) {
        log("E", msg, null);
    }

    public static void e(String msg, Throwable t) {
        log("E", msg, t);
    }

    private static void log(String level, String msg, Throwable t) {
        String line = TAG + "/" + level + ": " + msg;
        try {
            XposedBridge.log(line);
        } catch (Throwable ignored) {
        }
        if (t != null) {
            try {
                XposedBridge.log(t);
            } catch (Throwable ignored) {
            }
        }
        String path = file();
        if (path == null) {
            return;
        }
        StringBuilder sb = new StringBuilder(FMT.format(new Date()))
                .append(' ').append(level).append(' ').append(msg).append('\n');
        if (t != null) {
            sb.append("    ").append(t.getClass().getName()).append(": ")
                    .append(t.getMessage()).append('\n');
            StackTraceElement[] st = t.getStackTrace();
            for (int k = 0; k < st.length && k < 12; k++) {
                sb.append("      at ").append(st[k]).append('\n');
            }
        }
        try (FileOutputStream out = new FileOutputStream(path, true)) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    private static String file() {
        if (!sFileResolved) {
            synchronized (DevLog.class) {
                if (!sFileResolved) {
                    for (String p : Conf.LOG_PATHS) {
                        try {
                            File f = new File(p);
                            File dir = f.getParentFile();
                            if (dir != null && !dir.exists()) {
                                //noinspection ResultOfMethodCallIgnored
                                dir.mkdirs();
                            }
                            if (f.exists() || f.createNewFile()) {
                                if (f.canWrite()) {
                                    sFile = p;
                                    break;
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    sFileResolved = true;
                }
            }
        }
        return sFile;
    }
}
