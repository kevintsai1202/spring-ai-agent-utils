/*
* Copyright 2025 - 2025 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springaicommunity.agent.tools.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.task.repository.TaskRepository;
import org.springaicommunity.agent.utils.MarkdownParser;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author Christian Tzolov
 */

public class TaskTool {

	private static final String TASK_DESCRIPTION_TEMPLATE = """
			Launch a new agent to handle complex, multi-step tasks autonomously.

			The Task tool launches specialized agents (subprocesses) that autonomously handle complex tasks. Each agent type has specific capabilities and tools available to it.

			Available agent types and the tools they have access to:
			- general-purpose: General-purpose agent for researching complex questions, searching for code, and executing multi-step tasks. When you are searching for a keyword or file and are not confident that you will find the right match in the first few tries use this agent to perform the search for you. (Tools: *)
			- statusline-setup: Use this agent to configure the user's Claude Code status line setting. (Tools: Read, Edit)
			- Explore: Fast agent specialized for exploring codebases. Use this when you need to quickly find files by patterns (eg. "src/components/**/*.tsx"), search code for keywords (eg. "API endpoints"), or answer questions about the codebase (eg. "how do API endpoints work?"). When calling this agent, specify the desired thoroughness level: "quick" for basic searches, "medium" for moderate exploration, or "very thorough" for comprehensive analysis across multiple locations and naming conventions. (Tools: All tools)
			- Plan: Software architect agent for designing implementation plans. Use this when you need to plan the implementation strategy for a task. Returns step-by-step plans, identifies critical files, and considers architectural trade-offs. (Tools: All tools)
			- claude-code-guide: Use this agent when the user asks questions ("Can Claude...", "Does Claude...", "How do I...") about: (1) Claude Code (the CLI tool) - features, hooks, slash commands, MCP servers, settings, IDE integrations, keyboard shortcuts; (2) Claude Agent SDK - building custom agents; (3) Claude API (formerly Anthropic API) - API usage, tool use, Anthropic SDK usage. **IMPORTANT:** Before spawning a new agent, check if there is already a running or recently completed claude-code-guide agent that you can resume using the "resume" parameter. (Tools: Glob, Grep, Read, WebFetch, WebSearch)
			%s

			When using the Task tool, you must specify a subagent_type parameter to select which agent type to use.

			When NOT to use the Task tool:
			- If you want to read a specific file path, use the Read or Glob tool instead of the Task tool, to find the match more quickly
			- If you are searching for a specific class definition like "class Foo", use the Glob tool instead, to find the match more quickly
			- If you are searching for code within a specific file or set of 2-3 files, use the Read tool instead of the Task tool, to find the match more quickly
			- Other tasks that are not related to the agent descriptions above


			Usage notes:
			- Always include a short description (3-5 words) summarizing what the agent will do
			- Launch multiple agents concurrently whenever possible, to maximize performance; to do that, use a single message with multiple tool uses
			- When the agent is done, it will return a single message back to you. The result returned by the agent is not visible to the user. To show the user the result, you should send a text message back to the user with a concise summary of the result.
			- You can optionally run agents in the background using the run_in_background parameter. When an agent runs in the background, you will need to use TaskOutput to retrieve its results once it's done. You can continue to work while background agents run - When you need their results to continue you can use TaskOutput in blocking mode to pause and wait for their results.
			- When running tasks in the background, the Task tool will return a task_id immediately. Use the TaskOutput tool with this task_id to check status and retrieve results.
			- Agents can be resumed using the `resume` parameter by passing the agent ID from a previous invocation. When resumed, the agent continues with its full previous context preserved. When NOT resuming, each invocation starts fresh and you should provide a detailed task description with all necessary context.
			- When the agent is done, it will return a single message back to you along with its agent ID. You can use this ID to resume the agent later if needed for follow-up work.
			- Provide clear, detailed prompts so the agent can work autonomously and return exactly the information you need.
			- Agents with "access to current context" can see the full conversation history before the tool call. When using these agents, you can write concise prompts that reference earlier context (e.g., "investigate the error discussed above") instead of repeating information. The agent will receive all prior messages and understand the context.
			- The agent's outputs should generally be trusted
			- Clearly tell the agent whether you expect it to write code or just to do research (search, file reads, web fetches, etc.), since it is not aware of the user's intent
			- If the agent description mentions that it should be used proactively, then you should try your best to use it without the user having to ask for it first. Use your judgement.
			- If the user specifies that they want you to run agents "in parallel", you MUST send a single message with multiple Task tool use content blocks. For example, if you need to launch both a code-reviewer agent and a test-runner agent in parallel, send a single message with both tool calls.

			Example usage:

			<example_agent_descriptions>
			"code-reviewer": use this agent after you are done writing a signficant piece of code
			"greeting-responder": use this agent when to respond to user greetings with a friendly joke
			</example_agent_description>

			<example>
			user: "Please write a function that checks if a number is prime"
			assistant: Sure let me write a function that checks if a number is prime
			assistant: First let me use the Write tool to write a function that checks if a number is prime
			assistant: I'm going to use the Write tool to write the following code:
			<code>
			function isPrime(n) {
			if (n <= 1) return false
			for (let i = 2; i * i <= n; i++) {
				if (n %% i === 0) return false
			}
			return true
			}
			</code>
			<commentary>
			Since a signficant piece of code was written and the task was completed, now use the code-reviewer agent to review the code
			</commentary>
			assistant: Now let me use the code-reviewer agent to review the code
			assistant: Uses the Task tool to launch the code-reviewer agent
			</example>

			<example>
			user: "Hello"
			<commentary>
			Since the user is greeting, use the greeting-responder agent to respond with a friendly joke
			</commentary>
			assistant: "I'm going to use the Task tool to launch the greeting-responder agent"
			</example>
			""";

	public static record TaskCall( // @formatter:off
			@ToolParam(description = "A short (3-5 word) description of the task") String description,
			@ToolParam(description = "The task for the agent to perform") String prompt,
			@ToolParam(description = "The type of specialized agent to use for this task") String subagent_type,
			@ToolParam(description = "Optional model to use for this agent. If not specified, inherits from parent. Prefer small models for quick, straightforward tasks to minimize cost and latency.", required = false) String model,
			@ToolParam(description = "Optional agent ID to resume from. If provided, the agent will continue from the previous execution transcript.", required = false) String resume,
			@ToolParam(description = "Set to true to run this agent in the background. Use TaskOutput to read the output later.", required = false) Boolean run_in_background ) { // @formatter:on
	}

	public static class TaskFunction implements Function<TaskCall, String> {

		private static final Logger logger = LoggerFactory.getLogger(TaskFunction.class);

		private final Map<String, TaskType> tasksMap;

		private final ChatClient.Builder chatClientBuilder;

		private final List<ToolCallback> tools;

		// Storage for background tasks
		private final TaskRepository taskRepository;

		public TaskFunction(Map<String, TaskType> tasksMap, ChatClient.Builder chatClientBuilder,
				TaskRepository taskRepository, List<ToolCallback> tools) {
			this.tasksMap = tasksMap;
			this.chatClientBuilder = chatClientBuilder;
			this.taskRepository = taskRepository;
			this.tools = tools;
		}

		@Override
		public String apply(TaskCall taskCall) {

			TaskType taskType = this.tasksMap.get(taskCall.subagent_type);

			if (taskType == null) {
				return "Error: Unknown subagent type: " + taskCall.subagent_type();
			}

			if (Boolean.TRUE.equals(taskCall.run_in_background())) {
				// Create background task using CompletableFuture
				var bgTask = this.taskRepository.putTask("task_" + UUID.randomUUID(),
						() -> this.executeTaskChatClient(taskCall, taskType));

				return String.format(
						"task_id: %s\n\nBackground task started with ID: %s\nUse TaskOutput tool with task_id='%s' to retrieve results.",
						bgTask.getTaskId(), bgTask.getTaskId(), bgTask.getTaskId());
			}

			// Synchronous execution (existing behavior)
			return this.executeTaskChatClient(taskCall, taskType);

		}

		private String executeTaskChatClient(TaskCall taskCall, TaskType taskType) {

			var taskChatClient = this.createTaskChatClient(taskType);

			return taskChatClient.prompt()
				.user(taskCall.prompt)
				.system(taskType.content()) // Todo add the system suffix
				// Todo set model if provided.
				.call()
				.content();
		}

		private ChatClient createTaskChatClient(TaskType taskType) {

			var builder = this.chatClientBuilder.clone();

			if (!CollectionUtils.isEmpty(this.tools)) {
				if (CollectionUtils.isEmpty(taskType.tools())) {
					builder.defaultToolCallbacks(this.tools);
				}
				else {
					List<ToolCallback> taskTools = this.tools.stream()
						.filter(tc -> taskType.tools().contains(tc.getToolDefinition().name()))
						.toList();
					builder.defaultToolCallbacks(taskTools);
				}
			}

			if (!taskType.permissionMode().equals("default")) {
				logger.warn(
						"The task permissionMode is not supported yet. permissionMode = " + taskType.permissionMode());
			}

			if (!CollectionUtils.isEmpty(taskType.skills())) {
				logger.warn("The task skills filtering are not supported yet. skills = "
						+ String.join(",", taskType.skills()));
			}

			if (StringUtils.hasText(taskType.model())) {
				logger.warn("The task model override is not supported yet. model = " + taskType.model());
			}

			return builder.defaultAdvisors(ToolCallAdvisor.builder().build()).build();
		}

	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private List<TaskType> taskTypes = new ArrayList<>();

		private String taskDescriptionTemplate = TASK_DESCRIPTION_TEMPLATE;

		private ChatClient.Builder chatClientBuilder;

		private TaskRepository taskRepository;

		private List<ToolCallback> tools = new ArrayList<>();

		private Builder() {
			// Load the built-in task types (e.g. subagents)
			this.taskTypes
				.add(from(new DefaultResourceLoader().getResource("classpath:/agent/GENERAL_PURPOSE_SUBAGENT.md")));
			this.taskTypes.add(from(new DefaultResourceLoader().getResource("classpath:/agent/EXPLORE_SUBAGENT.md")));
		}

		public Builder tools(List<ToolCallback> tools) {
			this.tools.addAll(tools);
			return this;
		}

		public Builder tools(ToolCallback tool) {
			this.tools.add(tool);
			return this;
		}

		public Builder taskRepository(TaskRepository taskRepository) {
			Assert.notNull(taskRepository, "taskRepository must not be null");
			this.taskRepository = taskRepository;
			return this;
		}

		public Builder chatClientBuilder(ChatClient.Builder chatClientBuilder) {
			Assert.notNull(chatClientBuilder, "chatClientBuilder must not be null");
			this.chatClientBuilder = chatClientBuilder;
			return this;
		}

		public Builder taskDescriptionTemplate(String template) {
			Assert.hasText(template, "template must not be empty");
			this.taskDescriptionTemplate = template;
			return this;
		}

		public Builder addTaskDirectory(String taskRootDirectory) {
			this.addTaskDirectories(List.of(taskRootDirectory));
			return this;
		}

		public Builder addTaskDirectories(List<String> taskRootDirectories) {

			for (String taskRootDirectory : taskRootDirectories) {
				try {
					this.taskTypes.addAll(taskTypes(taskRootDirectory));
				}
				catch (IOException ex) {
					throw new RuntimeException("Failed to load tasks from directory: " + taskRootDirectory, ex);
				}
			}
			return this;
		}

		public ToolCallback build() {
			Assert.notNull(this.taskRepository, "taskRepository must be provided");
			Assert.notEmpty(this.taskTypes, "At least one task must be configured");
			Assert.notNull(this.chatClientBuilder, "chatClientBuilder must be provided");

			String tasks = this.taskTypes.stream().map(s -> s.toPromptContent()).collect(Collectors.joining("\n"));

			return FunctionToolCallback
				.builder("Task",
						new TaskFunction(toTasksMap(this.taskTypes), this.chatClientBuilder, this.taskRepository,
								this.tools))
				.description(this.taskDescriptionTemplate.formatted(tasks))
				.inputType(TaskCall.class)
				.build();
		}

	}

	private static Map<String, TaskType> toTasksMap(List<TaskType> tasks) {

		Map<String, TaskType> tasksMap = new HashMap<>();

		for (TaskType taskFile : tasks) {
			tasksMap.put(taskFile.frontMatter().get("name").toString(), taskFile);
		}

		return tasksMap;
	}

	/**
	 * Represents a agent md file with its location and parsed content.
	 * https://code.claude.com/docs/en/sub-agents#configuration-fields
	 */
	private static record TaskType(Path path, Map<String, Object> frontMatter, String content) {

		public String name() {
			return this.frontMatter().get("name").toString();
		}

		public String description() {
			return this.frontMatter().get("description").toString();
		}

		public String model() {
			return this.frontMatter().containsKey("model") ? this.frontMatter().get("model").toString() : null;
		}

		public String permissionMode() {
			return this.frontMatter().containsKey("permissionMode")
					? this.frontMatter().get("permissionMode").toString() : "default";
		}

		public List<String> tools() {
			if (!this.frontMatter().containsKey("tools")) {
				return List.of();
			}
			String[] toolNames = this.frontMatter().get("tools").toString().split(",");
			return Stream.of(toolNames).map(tn -> tn.trim()).filter(tn -> StringUtils.hasText(tn)).toList();
		}

		public List<String> skills() {
			if (!this.frontMatter().containsKey("skills")) {
				return List.of();
			}
			String[] skillNames = this.frontMatter().get("skills").toString().split(",");
			return Stream.of(skillNames).map(tn -> tn.trim()).filter(tn -> StringUtils.hasText(tn)).toList();
		}

		public String toPromptContent() {
			return this.frontMatter()
				.entrySet()
				.stream()
				.map(e -> "-%s: /%s".formatted(e.getKey(), e.getValue(), e.getKey()))
				.collect(Collectors.joining("\n"));
		}

	}

	/**
	 * Recursively finds all subagent (Task) markdown files in the given root directory
	 * and returns their parsed contents.
	 * @param rootDirectory the root directory to search for tasks files
	 * @return a list of Task objects containing the path, front-matter, and content of
	 * each task markdown file
	 * @throws IOException if an I/O error occurs while reading the directory or files
	 */
	private static List<TaskType> taskTypes(String rootDirectory) throws IOException {
		Path rootPath = Paths.get(rootDirectory);

		if (!Files.exists(rootPath)) {
			throw new IOException("Root directory does not exist: " + rootDirectory);
		}

		if (!Files.isDirectory(rootPath)) {
			throw new IOException("Path is not a directory: " + rootDirectory);
		}

		List<TaskType> taskFiles = new ArrayList<>();

		try (Stream<Path> paths = Files.walk(rootPath)) {
			paths.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".md"))
				.forEach(path -> {
					try {
						String markdown = Files.readString(path, StandardCharsets.UTF_8);
						MarkdownParser parser = new MarkdownParser(markdown);
						taskFiles.add(new TaskType(path, parser.getFrontMatter(), parser.getContent()));
					}
					catch (IOException e) {
						throw new RuntimeException("Failed to read task file: " + path, e);
					}
				});
		}

		return taskFiles;
	}

	private static TaskType from(Resource resource) {
		try {
			String markdown = resource.getContentAsString(StandardCharsets.UTF_8);
			MarkdownParser parser = new MarkdownParser(markdown);
			return new TaskType(resource.getFile().toPath(), parser.getFrontMatter(), parser.getContent());
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to read task file: " + resource.getFilename(), e);
		}
	}

}
