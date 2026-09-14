package simpledb.http;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.plan.Planner;
import simpledb.plan.Plan;
import simpledb.plan.Optimizer;
import simpledb.plan.BasicQueryPlanner;
import simpledb.parse.Lexer;
import simpledb.parse.Parser;
import simpledb.parse.QueryData;
import simpledb.parse.Token;
import simpledb.parse.BadSyntaxException;
import simpledb.parse.SemanticAnalyzer;
import simpledb.parse.SemanticError;
import simpledb.metadata.MetadataMgr;
import simpledb.engine.Executor;
import simpledb.engine.ExecutorImpl;
import simpledb.logical.LogicalPlan;
import simpledb.query.Constant;
import simpledb.shared.ExecuteResult;
import simpledb.shared.ColumnDef;
import simpledb.storage.BufferManager;
import simpledb.storage.CacheStats;

/**
 * 基于 JDK 内置 HttpServer 的轻量级 REST API 服务器。
 * 零外部依赖，为前端提供 SQL 执行、Token 解析、Plan 可视化等接口。
 */
public class HttpApiServer {
    private final SimpleDB db;
    private final HttpServer server;
    private final String staticDir;

    public HttpApiServer(SimpleDB db, int port, String staticDir) throws IOException {
        this.db = db;
        this.staticDir = staticDir;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        registerRoutes();
        server.setExecutor(null);
    }

    private void registerRoutes() {
        server.createContext("/api/execute", this::handleExecute);
        server.createContext("/api/explain", this::handleExplain);
        server.createContext("/api/tokens", this::handleTokens);
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/api/tables", this::handleTables);
        server.createContext("/api/schema/", this::handleSchema);
        // 成员B引擎通道：SQL -> LogicalPlan -> Executor
        server.createContext("/api/engine/execute", this::handleEngineExecute);
        server.createContext("/api/engine/tables", this::handleEngineTables);
        server.createContext("/api/engine/schema/", this::handleEngineSchema);
        server.createContext("/", this::handleStatic);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    // ==================== API Handlers ====================

    private void handleExecute(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        String sql = readBody(exchange);
        Transaction tx = db.newTx();
        try {
            long start = System.currentTimeMillis();
            Planner planner = db.planner();

            // 判断是查询还是更新
            String trimmed = sql.trim().toUpperCase();
            if (trimmed.startsWith("SELECT") || trimmed.startsWith("EXPLAIN")) {
                // EXPLAIN 作为查询处理
                if (trimmed.startsWith("EXPLAIN")) {
                    String explainSql = sql.trim().substring(7).trim();
                    if (explainSql.endsWith(";")) explainSql = explainSql.substring(0, explainSql.length()-1);
                    String result = planner.explain(explainSql, tx);
                    tx.commit();
                    long elapsed = System.currentTimeMillis() - start;
                    sendJson(exchange, 200, "{\"success\":true,\"type\":\"EXPLAIN\",\"planText\":\""
                        + JsonHelper.escape(result) + "\",\"timing\":" + elapsed + "}");
                    return;
                }
                Plan plan = planner.createQueryPlan(sql, tx);
                ExecuteResult result = executePlan(plan, tx);
                tx.commit();
                long elapsed = System.currentTimeMillis() - start;
                String json = JsonHelper.toJson(result);
                // 在 JSON 末尾插入 timing
                json = json.substring(0, json.length()-1) + ",\"timing\":" + elapsed + "}";
                sendJson(exchange, 200, json);
            } else {
                int affected = planner.executeUpdate(sql, tx);
                tx.commit();
                long elapsed = System.currentTimeMillis() - start;
                sendJson(exchange, 200, "{\"success\":true,\"type\":\"UPDATE\",\"affectedRows\":"
                    + affected + ",\"timing\":" + elapsed + "}");
            }
        } catch (BadSyntaxException e) {
            tx.rollback();
            sendJson(exchange, 200, JsonHelper.error("SYNTAX", e.getMessage(), e.getLine(), e.getColumn()));
        } catch (simpledb.parse.SemanticError e) {
            tx.rollback();
            sendJson(exchange, 200, JsonHelper.error("SEMANTIC", e.getMessage(), 0, 0));
        } catch (Exception e) {
            tx.rollback();
            sendJson(exchange, 200, JsonHelper.error("ENGINE", e.getMessage(), 0, 0));
        }
    }

    private void handleExplain(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        String sql = readBody(exchange);
        Transaction tx = db.newTx();
        try {
            // 解析
            Plan rawPlan, optPlan;
            Parser parser = new Parser(sql);
            QueryData data = parser.query();

            // 语义分析
            MetadataMgr mdm = db.mdMgr();
            if (mdm != null) {
                SemanticAnalyzer analyzer = new SemanticAnalyzer(mdm, tx);
                analyzer.analyzeQuery(data);
            }

            // 生成原始计划
            simpledb.plan.QueryPlanner qp = new BasicQueryPlanner(mdm);
            rawPlan = qp.createPlan(data, tx);

            // 优化后计划
            optPlan = Optimizer.optimize(rawPlan);

            String before = Optimizer.visualize(rawPlan);
            String after = Optimizer.visualize(optPlan);

            tx.commit();
            sendJson(exchange, 200, JsonHelper.toJsonExplain(sql, before, after,
                optPlan.blocksAccessed(), optPlan.recordsOutput()));
        } catch (Exception e) {
            tx.rollback();
            sendJson(exchange, 200, JsonHelper.error("ENGINE", e.getMessage(), 0, 0));
        }
    }

    private void handleTokens(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        String sql = readBody(exchange);
        try {
            List<Token> tokens = Lexer.tokenize(sql);
            sendJson(exchange, 200, JsonHelper.toJsonTokens(tokens));
        } catch (BadSyntaxException e) {
            sendJson(exchange, 200, JsonHelper.error("LEXICAL", e.getMessage(), e.getLine(), e.getColumn()));
        } catch (Exception e) {
            sendJson(exchange, 200, JsonHelper.error("LEXICAL", e.getMessage(), 0, 0));
        }
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        try {
            BufferManager bm = db.getBufferManager();
            CacheStats stats = bm.getStats();
            Transaction tx = db.newTx();
            List<String> tables = db.mdMgr().listTables(tx);
            tx.commit();

            StringBuilder sb = new StringBuilder();
            sb.append("{\"cache\":").append(JsonHelper.toJsonCacheStats(stats));
            sb.append(",\"tables\":").append(JsonHelper.toJsonTables(tables));
            sb.append(",\"pageSize\":").append(SimpleDB.BLOCK_SIZE);
            sb.append(",\"bufferSize\":").append(SimpleDB.BUFFER_SIZE);
            sb.append("}");
            sendJson(exchange, 200, sb.toString());
        } catch (Exception e) {
            sendJson(exchange, 500, JsonHelper.error("ENGINE", e.getMessage(), 0, 0));
        }
    }

    private void handleTables(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        Transaction tx = db.newTx();
        try {
            List<String> tables = db.mdMgr().listTables(tx);
            tx.commit();
            sendJson(exchange, 200, JsonHelper.toJsonTables(tables));
        } catch (Exception e) {
            tx.rollback();
            sendJson(exchange, 500, JsonHelper.error("ENGINE", e.getMessage(), 0, 0));
        }
    }

    private void handleSchema(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String tableName = path.substring("/api/schema/".length());
        if (tableName.isEmpty()) {
            sendError(exchange, 400, "Table name required");
            return;
        }
        Transaction tx = db.newTx();
        try {
            MetadataMgr mdm = db.mdMgr();
            if (!mdm.tableExists(tableName, tx)) {
                tx.commit();
                sendJson(exchange, 404, JsonHelper.error("NOT_FOUND", "Table '" + tableName + "' not found", 0, 0));
                return;
            }
            List<ColumnDef> columns = mdm.getColumns(tableName, tx);
            tx.commit();
            sendJson(exchange, 200, JsonHelper.toJsonColumns(tableName, columns));
        } catch (Exception e) {
            tx.rollback();
            sendJson(exchange, 500, JsonHelper.error("ENGINE", e.getMessage(), 0, 0));
        }
    }

    // ==================== Static File Serving ====================

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (staticDir == null) {
            sendError(exchange, 404, "No frontend configured");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if ("/".equals(path)) path = "/index.html";

        Path filePath = Paths.get(staticDir, path);
        if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
            byte[] data = Files.readAllBytes(filePath);
            String contentType = guessContentType(path);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.getResponseBody().close();
        } else {
            // SPA fallback: 返回 index.html
            Path indexPath = Paths.get(staticDir, "index.html");
            if (Files.exists(indexPath)) {
                byte[] data = Files.readAllBytes(indexPath);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, data.length);
                exchange.getResponseBody().write(data);
                exchange.getResponseBody().close();
            } else {
                sendError(exchange, 404, "Not found");
            }
        }
    }

    // ==================== Utility Methods ====================

    private ExecuteResult executePlan(Plan plan, Transaction tx) {
        simpledb.query.Scan scan = plan.open();
        List<String> columns = new java.util.ArrayList<>(plan.schema().fields());
        java.util.List<java.util.List<simpledb.query.Constant>> rows = new java.util.ArrayList<>();
        while (scan.next()) {
            java.util.List<simpledb.query.Constant> row = new java.util.ArrayList<>();
            for (String col : columns) {
                row.add(scan.getVal(col));
            }
            rows.add(row);
        }
        scan.close();
        return ExecuteResult.queryResult(columns, rows);
    }

    // ============ 成员B引擎通道：SQL -> LogicalPlan -> Executor ============

    /**
     * 走成员B的引擎模块执行 SQL 子集。
     * 与 /api/execute（SimpleDB 原生 Planner）互补，用于演示
     * UPDATE / JOIN / ORDER BY / GROUP BY 等 Level-4 能力。
     */
    private void handleEngineExecute(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        String sql = readBody(exchange);
        Transaction tx = db.newTx();
        try {
            long start = System.currentTimeMillis();

            EngineSqlTranslator translator = new EngineSqlTranslator(db.mdMgr());
            EngineSqlTranslator.Translation translation = translator.translate(sql, tx);
            LogicalPlan plan = translation.plan;

            Executor executor = new ExecutorImpl(db.mdMgr());
            ExecuteResult result = executor.execute(plan, tx);

            // 引擎的 OrderByPlan 只支持升序，DESC 在结果层反转
            List<List<Constant>> rows = Collections.emptyList();
            if (result.getType() == ExecuteResult.ResultType.QUERY) {
                List<List<Constant>> raw = result.getRows();
                if (raw != null) {
                    if (translation.reverse) {
                        rows = new ArrayList<>(raw);
                        Collections.reverse(rows);
                    } else {
                        rows = raw;
                    }
                }
            }
            tx.commit();
            long elapsed = System.currentTimeMillis() - start;

            StringBuilder sb = new StringBuilder();
            sb.append("{\"success\":true,\"engine\":\"memberB\",");
            sb.append("\"type\":\"").append(result.getType()).append("\",");
            sb.append("\"timing\":").append(elapsed).append(",");
            sb.append("\"planText\":\"").append(JsonHelper.escape(plan.explain(0))).append("\",");
            if (result.getType() == ExecuteResult.ResultType.QUERY) {
                List<String> names = result.getColumnNames();
                sb.append("\"columns\":[");
                for (int i = 0; i < names.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(JsonHelper.escape(names.get(i))).append("\"");
                }
                sb.append("],\"rows\":[");
                for (int i = 0; i < rows.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("[");
                    List<Constant> row = rows.get(i);
                    for (int j = 0; j < row.size(); j++) {
                        if (j > 0) sb.append(",");
                        Constant v = row.get(j);
                        sb.append("\"").append(JsonHelper.escape(v == null ? "" : v.toString())).append("\"");
                    }
                    sb.append("]");
                }
                sb.append("],\"rowCount\":").append(rows.size()).append(",\"affectedRows\":0");
            } else {
                sb.append("\"columns\":[],\"rows\":[],\"rowCount\":0,");
                sb.append("\"affectedRows\":").append(result.getAffectedRows());
            }
            sb.append("}");
            sendJson(exchange, 200, sb.toString());
        } catch (Exception e) {
            tx.rollback();
            sendJson(exchange, 200, JsonHelper.error("ENGINE", e.getMessage(), 0, 0));
        }
    }

    private void handleEngineTables(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        Transaction tx = db.newTx();
        try {
            List<String> tables = db.mdMgr().listTables(tx);
            tx.commit();
            sendJson(exchange, 200, JsonHelper.toJsonTables(tables));
        } catch (Exception e) {
            tx.rollback();
            sendJson(exchange, 500, JsonHelper.error("ENGINE", e.getMessage(), 0, 0));
        }
    }

    private void handleEngineSchema(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String tableName = path.substring("/api/engine/schema/".length());
        Transaction tx = db.newTx();
        try {
            List<ColumnDef> columns = db.mdMgr().getColumns(tableName, tx);
            tx.commit();
            sendJson(exchange, 200, JsonHelper.toJsonColumns(tableName, columns));
        } catch (Exception e) {
            tx.rollback();
            sendJson(exchange, 500, JsonHelper.error("ENGINE", e.getMessage(), 0, 0));
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        boolean first = true;
        while ((line = reader.readLine()) != null) {
            if (!first) sb.append("\n");
            sb.append(line);
            first = false;
        }
        return sb.toString();
    }

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        sendJson(exchange, code, "{\"error\":\"" + JsonHelper.escape(message) + "\"}");
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private String guessContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (path.endsWith(".css"))  return "text/css; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".ico"))  return "image/x-icon";
        if (path.endsWith(".woff")) return "font/woff";
        if (path.endsWith(".woff2"))return "font/woff2";
        return "application/octet-stream";
    }
}
