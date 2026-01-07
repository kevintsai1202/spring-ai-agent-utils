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
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agent.tools.task.repository.DefaultTaskRepository;
import org.springaicommunity.agent.tools.task.repository.TaskRepository;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link TaskTool}.
 *
 * @author Christian Tzolov
 */
@DisplayName("TaskTool Tests")
class TaskToolTest {

	private TaskRepository taskRepository;

	private ChatClient.Builder chatClientBuilder;

	@BeforeEach
	void setUp() {
		this.taskRepository = new DefaultTaskRepository();
		ChatModel mockChatModel = mock(ChatModel.class);
		this.chatClientBuilder = ChatClient.builder(mockChatModel);
	}

	@Nested
	@DisplayName("Builder Validation Tests")
	class BuilderValidationTests {

		@Test
		@DisplayName("Should fail when taskRepository is null")
		void shouldFailWhenTaskRepositoryIsNull() {
			assertThatThrownBy(() -> TaskTool.builder().chatClientBuilder(chatClientBuilder).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("taskRepository must be provided");
		}

		@Test
		@DisplayName("Should fail when chatClientBuilder is null")
		void shouldFailWhenChatClientBuilderIsNull() {
			assertThatThrownBy(() -> TaskTool.builder().taskRepository(taskRepository).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("chatClientBuilder must be provided");
		}

		@Test
		@DisplayName("Should fail when task directory does not exist")
		void shouldFailWhenTaskDirectoryDoesNotExist() {
			assertThatThrownBy(() -> TaskTool.builder()
				.addTaskDirectory("/non/existent/directory")
				.chatClientBuilder(chatClientBuilder)
				.taskRepository(taskRepository)
				.build()).isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Failed to load tasks from directory");
		}

		@Test
		@DisplayName("Should reject directory that is actually a file")
		void shouldRejectDirectoryThatIsActuallyAFile(@TempDir Path tempDir) throws IOException {
			Path file = tempDir.resolve("not-a-directory.txt");
			Files.writeString(file, "content");

			assertThatThrownBy(() -> TaskTool.builder()
				.addTaskDirectory(file.toString())
				.chatClientBuilder(chatClientBuilder)
				.taskRepository(taskRepository)
				.build()).isInstanceOf(RuntimeException.class)
				.hasRootCauseMessage("Path is not a directory: " + file.toString());
		}

	}

	@Nested
	@DisplayName("TaskInput Tests")
	class TaskInputTests {

		@Test
		@DisplayName("Should create TaskInput with all fields")
		void shouldCreateTaskInputWithAllFields() {
			TaskTool.TaskCall input = new TaskTool.TaskCall("Test description", "Test prompt",
					"general-purpose", "gpt-4", "resume-id-123", true);

			assertThat(input.description()).isEqualTo("Test description");
			assertThat(input.prompt()).isEqualTo("Test prompt");
			assertThat(input.subagent_type()).isEqualTo("general-purpose");
			assertThat(input.model()).isEqualTo("gpt-4");
			assertThat(input.resume()).isEqualTo("resume-id-123");
			assertThat(input.run_in_background()).isTrue();
		}

		@Test
		@DisplayName("Should create TaskInput with optional fields as null")
		void shouldCreateTaskInputWithOptionalFieldsAsNull() {
			TaskTool.TaskCall input = new TaskTool.TaskCall("Test description", "Test prompt",
					"general-purpose", null, null, null);

			assertThat(input.description()).isEqualTo("Test description");
			assertThat(input.prompt()).isEqualTo("Test prompt");
			assertThat(input.subagent_type()).isEqualTo("general-purpose");
			assertThat(input.model()).isNull();
			assertThat(input.resume()).isNull();
			assertThat(input.run_in_background()).isNull();
		}

		@Test
		@DisplayName("Should create TaskInput with background execution enabled")
		void shouldCreateTaskInputWithBackgroundExecutionEnabled() {
			TaskTool.TaskCall input = new TaskTool.TaskCall("Background task", "Execute in background",
					"explore", null, null, true);

			assertThat(input.run_in_background()).isTrue();
			assertThat(input.subagent_type()).isEqualTo("explore");
		}

		@Test
		@DisplayName("Should create TaskInput with resume parameter")
		void shouldCreateTaskInputWithResumeParameter() {
			String resumeId = "agent-123-456";
			TaskTool.TaskCall input = new TaskTool.TaskCall("Resume task", "Continue previous work",
					"general-purpose", null, resumeId, false);

			assertThat(input.resume()).isEqualTo(resumeId);
			assertThat(input.run_in_background()).isFalse();
		}

	}

	@Nested
	@DisplayName("Builder Configuration Tests")
	class BuilderConfigurationTests {

		@Test
		@DisplayName("Should allow setting custom task repository")
		void shouldAllowSettingCustomTaskRepository() {
			TaskRepository customRepository = new DefaultTaskRepository();

			TaskTool.Builder builder = TaskTool.builder().taskRepository(customRepository);

			assertThat(builder).isNotNull();
		}

		@Test
		@DisplayName("Should reject null task repository")
		void shouldRejectNullTaskRepository() {
			assertThatThrownBy(() -> TaskTool.builder().taskRepository(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("taskRepository must not be null");
		}

		@Test
		@DisplayName("Should reject null chat client builder")
		void shouldRejectNullChatClientBuilder() {
			assertThatThrownBy(() -> TaskTool.builder().chatClientBuilder(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("chatClientBuilder must not be null");
		}

	}

	@Nested
	@DisplayName("Task Directory Validation Tests")
	class TaskDirectoryValidationTests {

		@Test
		@DisplayName("Should handle empty task directory")
		void shouldHandleEmptyTaskDirectory(@TempDir Path tempDir) {
			// Empty directory should build successfully with just built-in tasks
			TaskTool.Builder builder = TaskTool.builder()
				.addTaskDirectory(tempDir.toString())
				.chatClientBuilder(chatClientBuilder)
				.taskRepository(taskRepository);

			assertThat(builder).isNotNull();
		}

		@Test
		@DisplayName("Should handle directory with non-markdown files")
		void shouldHandleDirectoryWithNonMarkdownFiles(@TempDir Path tempDir) throws IOException {
			Files.writeString(tempDir.resolve("readme.txt"), "This is not a task");
			Files.writeString(tempDir.resolve("config.json"), "{}");

			// Should build successfully, ignoring non-markdown files
			TaskTool.Builder builder = TaskTool.builder()
				.addTaskDirectory(tempDir.toString())
				.chatClientBuilder(chatClientBuilder)
				.taskRepository(taskRepository);

			assertThat(builder).isNotNull();
		}

		@Test
		@DisplayName("Should handle nested directory structure")
		void shouldHandleNestedDirectoryStructure(@TempDir Path tempDir) throws IOException {
			Path level1 = tempDir.resolve("level1");
			Path level2 = level1.resolve("level2");
			Files.createDirectories(level2);

			Files.writeString(level2.resolve("readme.txt"), "Deep file");

			TaskTool.Builder builder = TaskTool.builder()
				.addTaskDirectory(tempDir.toString())
				.chatClientBuilder(chatClientBuilder)
				.taskRepository(taskRepository);

			assertThat(builder).isNotNull();
		}

	}

}
