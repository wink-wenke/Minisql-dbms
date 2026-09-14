package simpledb.server;

import simpledb.http.HttpApiServer;

/**
 * MiniSQL Web 服务器入口。
 * 启动 HTTP API 服务 + 前端静态文件服务。
 *
 * 用法：java -cp build simpledb.server.StartWebServer [数据库目录] [端口]
 */
public class StartWebServer {
    public static void main(String[] args) throws Exception {
        String dirname = args.length > 0 ? args[0] : "studentdb";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        String staticDir = "frontend/dist";

        System.out.println("========================================");
        System.out.println("  MiniSQL Database - Web Interface");
        System.out.println("========================================");
        System.out.println("Initializing database: " + dirname);

        SimpleDB db = new SimpleDB(dirname);

        System.out.println("Starting HTTP server on port " + port + " ...");

        HttpApiServer httpServer = new HttpApiServer(db, port, staticDir);
        httpServer.start();

        System.out.println("Server ready!");
        System.out.println("Open browser: http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop.");
    }
}
