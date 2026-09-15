package tests;

import java.io.File;

/**
 * 测试临时文件统一清理工具。
 * 所有测试数据库统一存放在 tmp/ 目录下，JVM 退出时自动清理。
 *
 * 用法：测试 main() 中调用 TestCleanup.init() 注册 shutdown hook。
 */
public class TestCleanup {

    /** 所有测试数据库的统一存放目录 */
    public static final File TMP_DIR = new File("tmp");

    private static boolean initialized = false;

    /**
     * 注册 JVM shutdown hook，退出时自动清理 tmp/ 目录。
     * 多次调用安全，只注册一次。
     */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            deleteDir(TMP_DIR);
        }));
    }

    /**
     * 立即删除 tmp/ 目录及其所有内容。
     */
    public static void deleteAll() {
        deleteDir(TMP_DIR);
    }

    /**
     * 删除指定目录及其所有内容。
     */
    public static void deleteDir(File dir) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
