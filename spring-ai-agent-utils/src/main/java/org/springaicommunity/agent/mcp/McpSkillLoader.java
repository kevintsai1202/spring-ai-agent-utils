package org.springaicommunity.agent.mcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// MCP SDK Imports
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.json.McpJsonMapper;

// Spring AI MCP Imports
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.Resource;

public class McpSkillLoader {

    private static final Logger logger = LoggerFactory.getLogger(McpSkillLoader.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<ToolCallback> loadMcpSkills(List<Resource> mcpConfigDirs) {
        List<ToolCallback> toolCallbacks = new ArrayList<>();

        for (Resource configDir : mcpConfigDirs) {
            try {
                if (configDir.exists() && configDir.getFile().isDirectory()) {
                    toolCallbacks.addAll(loadFromDirectory(configDir.getFile().toPath()));
                }
            } catch (IOException e) {
                logger.error("Failed to load MCP skills from directory: " + configDir, e);
            }
        }

        return toolCallbacks;
    }

    private List<ToolCallback> loadFromDirectory(Path directory) {
        List<ToolCallback> callbacks = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> configFiles = paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .collect(Collectors.toList());

            for (Path configFile : configFiles) {
                try {
                    McpServerConfig config = objectMapper.readValue(configFile.toFile(), McpServerConfig.class);
                    callbacks.addAll(createMcpToolCallbacks(config));
                } catch (Exception e) {
                    logger.error("Failed to load MCP config from file: " + configFile, e);
                }
            }
        } catch (IOException e) {
            logger.error("Error walking directory: " + directory, e);
        }
        return callbacks;
    }

    private List<ToolCallback> createMcpToolCallbacks(McpServerConfig config) {
        logger.info("Initializing MCP client for server: {}", config.name());

        ServerParameters params = ServerParameters.builder(config.command())
                .args(config.args())
                .env(config.env())
                .build();

        var transport = new StdioClientTransport(params, McpJsonMapper.getDefault());

        var mcpClient = McpClient.sync(transport).requestTimeout(java.time.Duration.ofSeconds(10)).build();

        try {
            mcpClient.initialize();
        } catch (Exception e) {
            logger.error("Failed to initialize MCP client for " + config.name(), e);
            return List.of();
        }

        var tools = mcpClient.listTools();

        List<ToolCallback> convertedCallbacks = new ArrayList<>();

        for (var tool : tools.tools()) {
            // Use Spring AI's SyncMcpToolCallback
            // Assuming constructor: SyncMcpToolCallback(McpClient client, Tool tool)
            convertedCallbacks.add(new SyncMcpToolCallback(mcpClient, tool));
        }

        return convertedCallbacks;
    }

    public static record McpServerConfig(String name, String transport, String command, List<String> args,
            java.util.Map<String, String> env) {
    }
}
