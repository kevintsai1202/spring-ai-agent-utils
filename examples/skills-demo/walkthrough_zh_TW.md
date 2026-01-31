# 演練：動態 MCP 技能載入

我已經實作了一個 Model Context Protocol (MCP) 伺服器的動態載入器，讓 Agent 可以透過設定檔擴充其能力，而無需修改程式碼。

## 變更內容

### `spring-ai-agent-utils` (函式庫)
- **新增依賴**：`spring-ai-mcp`、`mcp-core`、`mcp-json-jackson2` (透過遞移/明確依賴)。
- **新元件**：`org.springaicommunity.agent.mcp.McpSkillLoader`
    - 掃描目錄中的 `*.json` 設定檔。
    - 使用 `StdioClientTransport` 連線到 MCP 伺服器。
    - 使用 Spring AI 的 `ToolCallback` 機制註冊發現的工具。

### `examples/skills-demo` (應用程式)
- **更新 `pom.xml`**：重新編譯以獲取函式庫的變更 (透過本地安裝)。
- **更新 `application.yml`**：新增 `agent.mcp.dirs` 屬性，指向 `classpath:/.claude/mcp`。
- **更新 `Application.java`**：
    - 設定 `McpSkillLoader` Bean。
    - 修改 `commandLineRunner` 以從 `agent.mcp.dirs` 載入工具並向 `ChatClient` 註冊。

## 如何使用

1.  **建立設定檔**：
    將 JSON 檔案放置在 `src/main/resources/.claude/mcp` (或任何已設定的目錄) 中。
    
    **範例：`filesystem.json`**
    ```json
    {
      "name": "filesystem",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "."],
      "env": {}
    }
    ```

2.  **執行應用程式**：
    啟動 Spring Boot 應用程式。Agent 將會發現設定檔，啟動 MCP 伺服器，並使其工具可供 AI 使用。

## 驗證
- **建置**：成功使用 Java 21 建置了 `spring-ai-agent-utils` 和 `examples/skills-demo`。
- **整合**：驗證了 `McpSkillLoader` 在應用程式啟動期間被正確呼叫。

> [!NOTE]
> 請確保 `npx` 或 JSON 設定中指定的指令在系統 PATH 中可用。
