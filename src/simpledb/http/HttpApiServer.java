package simpledb.http;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.List;

import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.plan.Planner;
import simpledb.plan.Plan;
import simpledb.plan.Optimizer;
import simpledb.plan.BasicQueryPlanner;
import simpledb.parse.Lexer;
import simpledb.parse.Parser;
import simpledb.parse.Token;
import simpledb.parse.BadSyntaxException;
import simpledb.parse.SemanticAnalyzer;
import simpledb.parse.SemanticError;
import simpledb.ast.AstNode;
import simpledb.ast.SelectNode;
import simpledb.ast.InsertNode;
import simpledb.ast.DeleteNode;
import simpledb.ast.UpdateNode;
import simpledb.metadata.MetadataMgr;
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
        server.createContext("/api/ast", this::handleAst);
        server.createContext("/api/analyze", this::handleAnalyze);
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/api/tables", this::handleTables);
        server.createContext("/api/schema/", this::handleSchema);
        server.createContext("/api/buffer-slots", this::handleBufferSlots);
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

            // 根据首关键字判断语句类型
            String trimmed = sql.trim().toUpperCase();
            if (trimmed.startsWith("EXPLAIN")) {
                // EXPLAIN：输出执行计划
                Parser parser = new Parser(sql);
                AstNode ast = parser.updateCmd();
                String result = planner.explain(((simpledb.ast.ExplainNode) ast).originalSql(), tx);
                tx.commit();
                long elapsed = System.currentTimeMillis() - start;
                sendJson(exchange, 200, "{\"success\":true,\"type\":\"EXPLAIN\",\"planText\":\""
                    + JsonHelper.escape(result) + "\",\"timing\":" + elapsed + "}");
            } else if (trimmed.startsWith("SELECT")) {
                // SELECT：执行查询
                Plan plan = planner.createQueryPlan(sql, tx);
                ExecuteResult result = executePlan(plan, tx);
                tx.commit();
                long elapsed = System.currentTimeMillis() - start;
                String json = JsonHelper.toJson(result);
                json = json.substring(0, json.length()-1) + ",\"timing\":" + elapsed + "}";
                sendJson(exchange, 200, json);
            } else {
                // INSERT / DELETE / UPDATE / CREATE / DROP
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
            // 如果以 EXPLAIN 开头，剥离前缀提取内部 SELECT
            String trimmed = sql.trim();
            if (trimmed.toUpperCase().startsWith("EXPLAIN")) {
                trimmed = trimmed.substring(7).trim();
            }
            if (!trimmed.toUpperCase().startsWith("SELECT")) {
                tx.rollback();
                sendJson(exchange, 200, JsonHelper.error("SEMANTIC", "执行计划仅支持 SELECT 查询语句", 0, 0));
                return;
            }

            // 解析
            Plan rawPlan, optPlan;
            Parser parser = new Parser(trimmed);
            simpledb.ast.SelectNode data = parser.query();

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

    private void handleAst(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        String sql = readBody(exchange);
        try {
            Parser parser = new Parser(sql);
            AstNode data;
            String trimmed = sql.trim().toUpperCase();
            if (trimmed.startsWith("SELECT")) {
                data = parser.query();
            } else {
                data = parser.updateCmd();
            }
            String astJson = JsonHelper.toJsonAst(data);
            // 包装为 { "ast": ..., "type": "..." }
            String typeName = data.getClass().getSimpleName().replace("Data", "");
            String response = "{\"ast\":" + astJson + ",\"type\":\"" + typeName + "\"}";
            sendJson(exchange, 200, response);
        } catch (BadSyntaxException e) {
            sendJson(exchange, 200, JsonHelper.error("SYNTAX", e.getMessage(), e.getLine(), e.getColumn()));
        } catch (Exception e) {
            sendJson(exchange, 200, JsonHelper.error("SYNTAX", e.getMessage(), 0, 0));
        }
    }

    private void handleAnalyze(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        String sql = readBody(exchange);
        Transaction tx = db.newTx();
        try {
            String trimmed = sql.trim().toUpperCase();
            MetadataMgr mdm = db.mdMgr();
            java.util.List<String> checks = new java.util.ArrayList<>();

            if (trimmed.startsWith("SELECT") || trimmed.startsWith("EXPLAIN")) {
                Parser parser = new Parser(sql);
                SelectNode selectData;
                if (trimmed.startsWith("EXPLAIN")) {
                    AstNode ast = parser.updateCmd();
                    selectData = ((simpledb.ast.ExplainNode) ast).query();
                } else {
                    selectData = (SelectNode) parser.query();
                }

                // 检查表是否存在
                for (String tbl : selectData.tables()) {
                    if (mdm.tableExists(tbl, tx)) {
                        checks.add("{\"name\":\"表 '" + tbl + "' 存在性\",\"status\":\"pass\",\"message\":\"表 '" + tbl + "' 已存在于数据库中\"}");
                    } else {
                        checks.add("{\"name\":\"表 '" + tbl + "' 存在性\",\"status\":\"fail\",\"message\":\"表 '" + tbl + "' 不存在于数据库中\"}");
                        tx.commit();
                        sendAnalyzeResult(exchange, false, checks);
                        return;
                    }
                }

                // 检查列是否存在
                simpledb.record.Schema combinedSchema = new simpledb.record.Schema();
                for (String tbl : selectData.tables()) {
                    simpledb.record.Layout layout = mdm.getLayout(tbl, tx);
                    combinedSchema.addAll(layout.schema());
                }
                for (String fld : selectData.fields()) {
                    if (fld.equals("*")) continue;
                    if (combinedSchema.hasField(fld)) {
                        checks.add("{\"name\":\"列 '" + fld + "' 存在性\",\"status\":\"pass\",\"message\":\"列 '" + fld + "' 存在于表中\"}");
                    } else {
                        checks.add("{\"name\":\"列 '" + fld + "' 存在性\",\"status\":\"fail\",\"message\":\"列 '" + fld + "' 不存在于表 " + selectData.tables() + "\"}");
                        tx.commit();
                        sendAnalyzeResult(exchange, false, checks);
                        return;
                    }
                }

                // 完整语义分析（类型检查等）
                SemanticAnalyzer analyzer = new SemanticAnalyzer(mdm, tx);
                analyzer.analyzeQuery(selectData);
                checks.add("{\"name\":\"完整语义分析\",\"status\":\"pass\",\"message\":\"类型检查、谓词验证均通过\"}");

            } else if (trimmed.startsWith("INSERT")) {
                Parser parser = new Parser(sql);
                InsertNode insertData = parser.insert();

                // 检查表是否存在
                String tbl = insertData.tableName();
                if (mdm.tableExists(tbl, tx)) {
                    checks.add("{\"name\":\"表 '" + tbl + "' 存在性\",\"status\":\"pass\",\"message\":\"表 '" + tbl + "' 已存在于数据库中\"}");
                } else {
                    checks.add("{\"name\":\"表 '" + tbl + "' 存在性\",\"status\":\"fail\",\"message\":\"表 '" + tbl + "' 不存在于数据库中\"}");
                    tx.commit();
                    sendAnalyzeResult(exchange, false, checks);
                    return;
                }

                SemanticAnalyzer analyzer = new SemanticAnalyzer(mdm, tx);
                analyzer.analyzeInsert(insertData);
                checks.add("{\"name\":\"INSERT 语义分析\",\"status\":\"pass\",\"message\":\"列数、列名、值类型均正确\"}");

            } else if (trimmed.startsWith("DELETE")) {
                Parser parser = new Parser(sql);
                DeleteNode deleteData = parser.delete();

                String tbl = deleteData.tableName();
                if (mdm.tableExists(tbl, tx)) {
                    checks.add("{\"name\":\"表 '" + tbl + "' 存在性\",\"status\":\"pass\",\"message\":\"表 '" + tbl + "' 已存在于数据库中\"}");
                } else {
                    checks.add("{\"name\":\"表 '" + tbl + "' 存在性\",\"status\":\"fail\",\"message\":\"表 '" + tbl + "' 不存在于数据库中\"}");
                    tx.commit();
                    sendAnalyzeResult(exchange, false, checks);
                    return;
                }

                SemanticAnalyzer analyzer = new SemanticAnalyzer(mdm, tx);
                analyzer.analyzeDelete(deleteData);
                checks.add("{\"name\":\"DELETE 语义分析\",\"status\":\"pass\",\"message\":\"WHERE 谓词验证通过\"}");

            } else if (trimmed.startsWith("UPDATE")) {
                Parser parser = new Parser(sql);
                UpdateNode updateData = parser.modify();

                String tbl = updateData.tableName();
                if (mdm.tableExists(tbl, tx)) {
                    checks.add("{\"name\":\"表 '" + tbl + "' 存在性\",\"status\":\"pass\",\"message\":\"表 '" + tbl + "' 已存在于数据库中\"}");
                } else {
                    checks.add("{\"name\":\"表 '" + tbl + "' 存在性\",\"status\":\"fail\",\"message\":\"表 '" + tbl + "' 不存在于数据库中\"}");
                    tx.commit();
                    sendAnalyzeResult(exchange, false, checks);
                    return;
                }

                SemanticAnalyzer analyzer = new SemanticAnalyzer(mdm, tx);
                analyzer.analyzeUpdate(updateData);
                checks.add("{\"name\":\"UPDATE 语义分析\",\"status\":\"pass\",\"message\":\"列名、类型、谓词验证均通过\"}");

            } else {
                // CREATE / DROP 等 DDL 不需要语义检查
                checks.add("{\"name\":\"DDL 语句\",\"status\":\"pass\",\"message\":\"DDL 语句无需语义检查\"}");
                tx.commit();
                sendAnalyzeResult(exchange, true, checks);
                return;
            }
            tx.commit();
            sendAnalyzeResult(exchange, true, checks);
        } catch (simpledb.parse.SemanticError e) {
            tx.rollback();
            java.util.List<String> checks = new java.util.ArrayList<>();
            checks.add("{\"name\":\"语义分析\",\"status\":\"fail\",\"message\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            sendAnalyzeResult(exchange, false, checks);
        } catch (Exception e) {
            tx.rollback();
            java.util.List<String> checks = new java.util.ArrayList<>();
            checks.add("{\"name\":\"语义分析\",\"status\":\"fail\",\"message\":\"" + JsonHelper.escape(e.getMessage()) + "\"}");
            sendAnalyzeResult(exchange, false, checks);
        }
    }

    private void sendAnalyzeResult(HttpExchange exchange, boolean success, java.util.List<String> checks) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"success\":").append(success).append(",\"checks\":[");
        for (int i = 0; i < checks.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(checks.get(i));
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
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

    private void handleBufferSlots(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }
        try {
            BufferManager bm = db.getBufferManager();
            List<BufferManager.SlotInfo> slots = bm.getBufferSlots();
            sendJson(exchange, 200, JsonHelper.toJsonBufferSlots(slots));
        } catch (Exception e) {
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
