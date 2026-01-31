package org.springaicommunity.skills;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springaicommunity.agent.mcp.McpSkillLoader;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	McpSkillLoader mcpSkillLoader() {
		return new McpSkillLoader();
	}

	@Bean
	CommandLineRunner commandLineRunner(ChatClient.Builder chatClientBuilder,
			@Value("${agent.skills.dirs:Unknown}") List<Resource> agentSkillsDirs,
			@Value("${agent.mcp.dirs:Unknown}") List<Resource> mcpSkillsDirs,
			McpSkillLoader mcpSkillLoader,
			@Value("${app.brave.api-key:}") String braveApiKey) throws IOException {

		return args -> {

			java.util.List<Object> tools = new java.util.ArrayList<>();
			tools.add(ShellTools.builder().build());
			tools.add(FileSystemTools.builder().build());
			tools.add(SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build());

			// Load dynamic MCP skills
			tools.addAll(mcpSkillLoader.loadMcpSkills(mcpSkillsDirs));

			String resolvedBraveKey = (braveApiKey != null && !braveApiKey.isBlank()) ? braveApiKey
					: System.getenv("BRAVE_API_KEY");
			if (resolvedBraveKey != null && !resolvedBraveKey.isBlank()) {
				tools.add(BraveWebSearchTool.builder(resolvedBraveKey).resultCount(15).build());
			}

			ChatClient chatClient = chatClientBuilder // @formatter:off
				.defaultSystem("Always use the available skills to assist the user in their requests.")

				// Skills tool
				.defaultToolCallbacks(SkillsTool.builder().addSkillsResources(agentSkillsDirs).build())

				// Built-in tools and MCP tools
				.defaultTools(tools.toArray())

				.defaultAdvisors(
					// Tool Calling advisor
					ToolCallAdvisor.builder().build(),
					// Custom logging advisor
					MyLoggingAdvisor.builder()
						.showAvailableTools(false)
						.showSystemMessage(false)
						.build())
				.defaultToolContext(Map.of("foo", "bar"))
				.build();
				// @formatter:on

			var answer = chatClient
					.prompt("""
							請用簡單的語言解釋強化學習及其用途。
							請運用所需的技能。
							接著使用 Youtube 影片 https://youtu.be/vXtfdGphr3c?si=xy8U2Al_Um5vE4Jd 的逐字稿來佐證你的回答。
							最後輸出成pdf檔案,自行定義檔名
							Skills和scripts請使用絕對路徑。不要問我更多細節。
							""")
					.call()
					.content();

			System.out.println("The Answer: " + answer);
		};

	}

}
