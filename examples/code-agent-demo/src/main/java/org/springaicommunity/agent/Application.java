package org.springaicommunity.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.springaicommunity.agent.tools.AskUserQuestionTool;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question;
import org.springaicommunity.agent.tools.AskUserQuestionTool.Question.Option;
import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.tools.SmartWebFetchTool;
import org.springaicommunity.agent.tools.TodoWriteTool;
import org.springaicommunity.agent.utils.AgentEnvironment;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(ChatClient.Builder chatClientBuilder,
			@Value("${BRAVE_API_KEY:#{null}}") String braveApiKey,
			@Value("classpath:/prompt/MAIN_AGENT_SYSTEM_PROMPT_V2.md") Resource systemPrompt,
			@Value("${agent.model.knowledge.cutoff:Unknown}") String agentModelKnowledgeCutoff,
			@Value("${agent.model:Unknown}") String agentModel,
			@Value("${agent.skills.paths}") List<Resource> skillPaths, ToolCallbackProvider mcpToolCallbackProvider) {

		return args -> {
			// @formatter:off
			ChatClient chatClient = chatClientBuilder
				.defaultSystem(p -> p.text(systemPrompt) // system prompt
					.param(AgentEnvironment.ENVIRONMENT_INFO_KEY, AgentEnvironment.info())
					.param(AgentEnvironment.GIT_STATUS_KEY, AgentEnvironment.gitStatus())
					.param(AgentEnvironment.AGENT_MODEL_KEY, agentModel)
					.param(AgentEnvironment.AGENT_MODEL_KNOWLEDGE_CUTOFF_KEY, agentModelKnowledgeCutoff))

				// AirBnb MCP Tools
				.defaultToolCallbacks(mcpToolCallbackProvider)

				// Skills tool
				.defaultToolCallbacks(SkillsTool.builder().addSkillsResources(skillPaths).build())

				// Todo management tool
				.defaultTools(TodoWriteTool.builder().build())

				// Ask user question tool
				.defaultTools(AskUserQuestionTool.builder()
					.questionAnswerFunction(Application::handleQuestions)
					.answersValidation(false)
					.build())

				// Common agentic tools
				.defaultTools(
					ShellTools.builder().build(), // needed by the skills to execute scripts
					FileSystemTools.builder().build(),// needed by the skills to read/write additional resources
					SmartWebFetchTool.builder(chatClientBuilder.clone().build()).build(),
					BraveWebSearchTool.builder(braveApiKey).resultCount(15).build(),				
					GrepTool.builder().build())

				// Advisors
				.defaultAdvisors(
					ToolCallAdvisor.builder()
						.conversationHistoryEnabled(false)
						.build(), // tool calling advisor
					MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(500).build())
						.order(Ordered.HIGHEST_PRECEDENCE + 1000)
						.build())
					// logging advisor	
					// MyLoggingAdvisor.builder()
					// 	.showAvailableTools(false)
					// 	.showSystemMessage(false)
					// 	.build()) 
				.build();
				// @formatter:on

			// Start the chat loop
			System.out.println("\nI am your assistant.\n");

			try (Scanner scanner = new Scanner(System.in)) {
				while (true) {
					System.out.print("\n> USER: ");
					System.out.println("\n> ASSISTANT: " + chatClient.prompt(scanner.nextLine()).call().content());
				}
			}
		};
	}

	private static Map<String, String> handleQuestions(List<Question> questions) {
		Map<String, String> answers = new HashMap<>();
		Scanner scanner = new Scanner(System.in);

		for (Question q : questions) {
			System.out.println("\n" + q.header() + ": " + q.question());

			List<Option> options = q.options();
			for (int i = 0; i < options.size(); i++) {
				Option opt = options.get(i);
				System.out.printf("  %d. %s - %s%n", i + 1, opt.label(), opt.description());
			}

			if (q.multiSelect()) {
				System.out.println("  (Enter numbers separated by commas, or type custom text)");
			}
			else {
				System.out.println("  (Enter a number, or type custom text)");
			}

			String response = scanner.nextLine().trim();
			answers.put(q.question(), parseResponse(response, options));
		}

		return answers;
	}

	private static String parseResponse(String response, List<Option> options) {
		try {
			// Try parsing as option number(s)
			String[] parts = response.split(",");
			List<String> labels = new ArrayList<>();
			for (String part : parts) {
				int index = Integer.parseInt(part.trim()) - 1;
				if (index >= 0 && index < options.size()) {
					labels.add(options.get(index).label());
				}
			}
			return labels.isEmpty() ? response : String.join(", ", labels);
		}
		catch (NumberFormatException e) {
			// Not a number, use as free text
			return response;
		}
	}

}
