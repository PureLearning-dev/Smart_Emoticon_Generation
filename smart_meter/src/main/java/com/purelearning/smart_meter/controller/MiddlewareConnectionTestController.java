package com.purelearning.smart_meter.controller;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test/middleware")
@Tag(name = "Test - 中间件连通性", description = "用于测试 MySQL 与 Milvus 等中间件是否可用的探活接口")
public class MiddlewareConnectionTestController {

    private static final String MYSQL_URL = "jdbc:mysql://localhost:3306/smart_meter_system?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "root";

    private static final String MILVUS_URI = "http://127.0.0.1:19530";

    @GetMapping("/mysql")
    @Operation(summary = "测试 MySQL 连接", description = "尝试建立一次到 MySQL 的 JDBC 连接，返回连接是否成功及错误信息。")
    public ResponseEntity<Map<String, Object>> testMySql() {
        Map<String, Object> result = new HashMap<>();
        // MySQL 的 Connection 实现了 AutoCloseable，可以继续使用 try-with-resources
        try (Connection ignored = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD)) {
            result.put("success", true);
            result.put("message", "MySQL 连接成功");
        } catch (SQLException e) {
            result.put("success", false);
            result.put("message", "MySQL 连接失败");
            result.put("error", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/milvus")
    @Operation(summary = "测试 Milvus 连接", description = "创建 MilvusClientV2 并调用 listDatabases()，用来检测 Milvus 是否正常工作。")
    public ResponseEntity<Map<String, Object>> testMilvus() {
        Map<String, Object> result = new HashMap<>();

        // 1. 将 Client 声明在外面
        MilvusClientV2 client = null;

        try {
            // 2. 初始化连接
            client = new MilvusClientV2(
                    ConnectConfig.builder()
                            .uri(MILVUS_URI)
                            .build()
            );

            // 验证连通性
            client.listDatabases();

            result.put("success", true);
            result.put("message", "Milvus 连接成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Milvus 连接失败");
            result.put("error", e.getMessage());
        } finally {
            // 3. 在 finally 块中手动关闭，确保资源释放
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    // 关闭时的异常通常可以忽略，或者简单记录日志
                    System.err.println("关闭 Milvus 连接时出错: " + e.getMessage());
                }
            }
        }
        return ResponseEntity.ok(result);
    }
}