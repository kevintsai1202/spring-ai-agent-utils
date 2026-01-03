# Spring AI Agent Utils

A Spring AI library that brings Claude Code-inspired tools and skills to your AI agents.

## Overview

Spring AI Agent Utils reimplements core [Claude Code](https://code.claude.com/docs/en/overview) capabilities as Spring AI tools, enabling sophisticated agentic workflows with file operations, shell execution, web access, task management, and extensible skills.

## Features

### Core Tools

- **FileSystemTools** - Read, write, and edit files with precise control
- **ShellTools** - Execute shell commands with background process support
- **GrepTool** - Pure Java grep implementation for code search with regex, glob filtering, and multiple output modes
- **TodoWriteTool** - Structured task management with state tracking
- **SmartWebFetchTool** - AI-powered web content summarization with caching
- **BraveWebSearchTool** - Web search with domain filtering

### Agent Skills

Extend agent capabilities with reusable, composable knowledge modules defined in Markdown with YAML front-matter:

```yaml
---
name: ai-tutor
description: Use when user asks to explain technical concepts
---

# AI Tutor
[Detailed skill documentation...]
```

Skills can include executable scripts and reference materials, loaded dynamically from `.claude/skills/` directories.

## Installation

**Maven:**
```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

```java
@SpringBootApplication
public class Application {

    @Bean
    CommandLineRunner demo(ChatClient.Builder chatClientBuilder) {
        return args -> {
            ChatClient chatClient = chatClientBuilder
                // Load skills
                .defaultToolCallbacks(SkillsToolProvider.create(".claude/skills"))

                // Register tools
                .defaultTools(new ShellTools())
                .defaultTools(new FileSystemTools())
                .defaultTools(new GrepTool())
                .defaultTools(SmartWebFetchTool.builder(chatClient).build())
                .defaultTools(BraveWebSearchTool.builder(apiKey).build())
                .defaultTools(new TodoWriteTool())
                .build();

            String response = chatClient
                .prompt("Search for Spring AI documentation and summarize it")
                .call()
                .content();
        };
    }
}
```

## Tool Details

### FileSystemTools

A comprehensive file manipulation toolkit providing read, write, and edit operations for working with files in the local filesystem.

**Features:**
- Read files with line range support and pagination
- Write new files or overwrite existing ones
- Precise string replacement editing
- Line number formatting for easy reference
- Long line truncation (2000 chars max)
- Automatic parent directory creation
- UTF-8 encoding support
- Replace all or single occurrence editing

**Available Tools:**

#### 1. Read - Read File Contents

Reads a file from the local filesystem with optional line range support for handling large files.

**Parameters:**
- `filePath` (required) - The absolute path to the file to read
- `offset` (optional) - The line number to start reading from (1-indexed)
- `limit` (optional) - The number of lines to read (default: 2000)

**Basic Usage:**

```java
FileSystemTools fileTools = new FileSystemTools();

// Read entire file (up to 2000 lines)
String content = fileTools.read(
    "/path/to/file.txt",
    null,                    // offset (start from beginning)
    null,                    // limit (read up to 2000 lines)
    toolContext
);

// Read specific line range
String content = fileTools.read(
    "/path/to/large-file.log",
    100,                     // Start from line 100
    50,                      // Read 50 lines
    toolContext
);

// Read from line 500 onwards
String content = fileTools.read(
    "/path/to/file.java",
    500,                     // Start from line 500
    2000,                    // Read up to 2000 lines
    toolContext
);
```

**Output Format:**
```
File: /path/to/file.txt
Showing lines 1-10 of 150

     1→First line of content
     2→Second line of content
     3→Third line of content
     ...
    10→Tenth line of content
```

**Key Features:**
- **Line numbers**: Results formatted with `cat -n` style line numbers (right-aligned, 6 chars, arrow separator)
- **Line truncation**: Lines longer than 2000 characters are truncated with "... (line truncated)" suffix
- **Pagination**: Read large files in chunks using offset and limit
- **Empty file detection**: Returns "File is empty" message for empty files
- **Error handling**: Clear error messages for non-existent files or directories

**Important Notes:**
- File path must be absolute, not relative
- Default limit is 2000 lines - recommended to read full file when possible
- Line numbers are 1-indexed (first line is line 1)
- Cannot read directories (use Bash tool with `ls` command)
- Supports reading various file types (text, images, PDFs, Jupyter notebooks with appropriate handling)

#### 2. Write - Create or Overwrite Files

Writes content to a file, creating new files or overwriting existing ones.

**Parameters:**
- `filePath` (required) - The absolute path to the file to write (must be absolute)
- `content` (required) - The content to write to the file

**Basic Usage:**

```java
// Create a new file
String result = fileTools.write(
    "/path/to/new-file.txt",
    "This is the file content\nWith multiple lines",
    toolContext
);
// Returns: "Successfully created file: /path/to/new-file.txt (45 bytes)"

// Overwrite an existing file
String result = fileTools.write(
    "/path/to/existing-file.txt",
    "New content replacing old content",
    toolContext
);
// Returns: "Successfully overwrote file: /path/to/existing-file.txt (33 bytes)"

// Create file with parent directories
String result = fileTools.write(
    "/path/to/new/directory/file.txt",
    "Content",
    toolContext
);
// Automatically creates /path/to/new/directory/ if it doesn't exist
```

**Important Notes:**
- **MUST read first**: If overwriting an existing file, you MUST use the Read tool first
- **Prefer Edit**: ALWAYS prefer editing existing files instead of writing new ones
- **No emojis**: Avoid writing emojis unless explicitly requested by the user
- **No proactive docs**: Never create documentation files (*.md, README) unless explicitly requested
- **Parent directories**: Automatically creates parent directories if they don't exist
- **Complete replacement**: Overwrites the entire file content (does not append)

**When to Use:**
- Creating new files explicitly requested by the user
- Generating configuration files, scripts, or source files
- Writing output from data transformations

**When NOT to Use:**
- Modifying existing files (use Edit instead)
- Making small changes to code (use Edit instead)

#### 3. Edit - Precise String Replacement

Performs exact string replacements in files with safety checks to prevent unintended changes.

**Parameters:**
- `filePath` (required) - The absolute path to the file to modify
- `old_string` (required) - The exact text to replace
- `new_string` (required) - The text to replace it with (must be different from old_string)
- `replace_all` (optional) - Replace all occurrences (default: false)

**Basic Usage:**

```java
// Single replacement (default)
String result = fileTools.edit(
    "/path/to/file.java",
    "public void oldMethod() {",       // old_string
    "public void newMethod() {",       // new_string
    null,                               // replace_all (false)
    toolContext
);

// Replace all occurrences (useful for variable renaming)
String result = fileTools.edit(
    "/path/to/file.java",
    "oldVariableName",                 // old_string
    "newVariableName",                 // new_string
    true,                              // replace_all
    toolContext
);

// Multi-line replacement
String result = fileTools.edit(
    "/path/to/config.yml",
    "database:\n  host: localhost\n  port: 5432",
    "database:\n  host: prod-server\n  port: 3306",
    null,
    toolContext
);
```

**Output Format:**
```
The file /path/to/file.java has been updated. Here's the result of running `cat -n` on a snippet of the edited file:
    15→    private static final String NAME = "newValue";
    16→
    17→    public void newMethod() {
    18→        // method implementation
    19→    }
```

**Safety Features:**

1. **Uniqueness Check**: If `old_string` appears multiple times and `replace_all=false`, the edit fails with an error:
   ```
   Error: old_string appears 5 times in the file. Either provide a larger string
   with more surrounding context to make it unique or use replace_all=true to
   change all instances.
   ```

2. **Existence Check**: Returns error if `old_string` is not found in the file

3. **Different Strings**: Validates that `old_string` and `new_string` are different

4. **Must Read First**: Should read the file first to see exact content and indentation

**Indentation Handling:**

When copying text from Read tool output, preserve exact indentation AFTER the line number prefix:

```
Read output:
    42→    public void method() {

Correct old_string:
"    public void method() {"

Incorrect old_string (includes line number):
"42→    public void method() {"
```

**Best Practices:**

1. **Read before editing**: Always use Read tool first to see exact content
   ```java
   String content = fileTools.read("/path/to/file.java", null, null, toolContext);
   // Now edit based on what you see
   ```

2. **Include context for uniqueness**: If replacement string appears multiple times, include surrounding lines
   ```java
   // Instead of just "foo"
   fileTools.edit(filePath, "bar\nfoo\nbaz", "bar\nnewValue\nbaz", null, toolContext);
   ```

3. **Use replace_all for renaming**: When renaming variables or constants throughout a file
   ```java
   fileTools.edit(filePath, "OLD_CONSTANT", "NEW_CONSTANT", true, toolContext);
   ```

4. **Preserve indentation**: Copy exact whitespace from Read output (spaces/tabs)

5. **Multi-line edits**: Use `\n` for newlines in both old_string and new_string

**Example Workflow:**

```java
FileSystemTools fileTools = new FileSystemTools();

// 1. Read the file to see current content
String content = fileTools.read(
    "/src/main/java/Example.java",
    null,
    null,
    toolContext
);

// 2. Identify exact string to replace (preserving indentation)
String oldCode = """
    public void calculate() {
        return value * 2;
    }""";

String newCode = """
    public void calculate() {
        return value * 3;
    }""";

// 3. Perform the edit
String result = fileTools.edit(
    "/src/main/java/Example.java",
    oldCode,
    newCode,
    null,
    toolContext
);

// Output shows snippet with context around the edit
```

**Common Use Cases:**

| Task | Configuration | Example |
|------|--------------|---------|
| Update single method | `replace_all=false` | Modify one method implementation |
| Rename variable | `replace_all=true` | Change `oldName` to `newName` everywhere |
| Update configuration | `replace_all=false` | Change one config value |
| Fix typos | `replace_all=true` | Fix misspelled word throughout file |
| Refactor imports | `replace_all=false` | Update specific import statement |

**Error Messages:**

| Error | Meaning | Solution |
|-------|---------|----------|
| "File does not exist" | File path is invalid | Check file path is correct |
| "old_string not found" | Text doesn't exist in file | Verify exact string including whitespace |
| "appears N times" | Multiple matches found | Add more context or use `replace_all=true` |
| "must be different" | old_string equals new_string | Ensure you're actually changing the content |

### ShellTools

A comprehensive shell execution toolkit that provides both synchronous and background command execution with full process management capabilities.

**Features:**
- Synchronous command execution with configurable timeout
- Background process execution for long-running commands
- Real-time output monitoring for background processes
- Separate stdout and stderr capture
- Process lifecycle management (kill, status checks)
- Regex filtering for output
- Cross-platform support (Windows/Unix)
- Automatic output truncation for large outputs
- Exit code reporting

**Available Tools:**

#### 1. Bash - Execute Shell Commands

The primary tool for executing shell commands either synchronously or in the background.

**Parameters:**
- `command` (required) - The shell command to execute
- `timeout` (optional) - Timeout in milliseconds (max 600000ms / 10 minutes, default: 120000ms / 2 minutes)
- `description` (optional) - Clear description of what the command does (5-10 words)
- `runInBackground` (optional) - Set to `true` to run command in background

**Basic Usage:**

```java
ShellTools shellTools = new ShellTools();

// Synchronous execution
String result = shellTools.bash(
    "ls -la",           // command
    null,               // timeout (uses default 2 minutes)
    "List all files",   // description
    null,               // runInBackground (false)
    toolContext
);

// With custom timeout
String result = shellTools.bash(
    "npm install",
    300000L,            // 5 minute timeout
    "Install dependencies",
    null,
    toolContext
);

// Background execution
String result = shellTools.bash(
    "npm run dev",
    null,
    "Start dev server",
    true,               // Run in background
    toolContext
);
// Returns: "bash_id: shell_1234567890"
```

**Output Format:**
```
bash_id: shell_1234567890

[command output]

STDERR:
[error output if any]

Exit code: [exit code if non-zero]
```

**Important Notes:**
- Commands timeout after 2 minutes by default (configurable up to 10 minutes)
- Output is truncated if it exceeds 30,000 characters
- Background commands return immediately with a `bash_id` for monitoring
- Use Unix shell (`/bin/bash`) on Linux/Mac, CMD on Windows
- Don't use `&` when using `runInBackground=true` (handled automatically)

#### 2. BashOutput - Monitor Background Processes

Retrieves output from running or completed background shell processes.

**Parameters:**
- `bash_id` (required) - The ID of the background shell (from Bash tool)
- `filter` (optional) - Regex pattern to filter output lines

**Usage:**

```java
// Start background process
String startResult = shellTools.bash(
    "mvn clean install",
    null,
    "Build project",
    true,
    toolContext
);
// Extract bash_id from result (e.g., "shell_1234567890")

// Monitor output
String output = shellTools.bashOutput(
    "shell_1234567890",  // bash_id
    null,                 // no filter
    toolContext
);

// Filter output with regex (only show ERROR lines)
String filteredOutput = shellTools.bashOutput(
    "shell_1234567890",
    ".*ERROR.*",         // regex filter
    toolContext
);
```

**Output Format:**
```
Shell ID: shell_1234567890
Status: Running (or Completed)
Exit code: 0

New output:
STDOUT:
[new stdout content since last check]

STDERR:
[new stderr content since last check]
```

**Key Features:**
- **Incremental output**: Only returns NEW output since last check
- **Regex filtering**: Filter output lines matching a pattern (filtered lines are consumed)
- **Status tracking**: Shows if process is running or completed
- **Exit code**: Available when process completes

**Important Notes:**
- Only shows output generated since the last `BashOutput` call
- Filtered lines are marked as "read" and won't appear in subsequent calls
- Returns "No new output since last check" if nothing new is available

#### 3. KillShell - Terminate Background Processes

Gracefully terminates a running background shell process.

**Parameters:**
- `bash_id` (required) - The ID of the background shell to kill

**Usage:**

```java
// Kill a background process
String result = shellTools.killShell(
    "shell_1234567890",
    toolContext
);
// Returns: "Successfully killed shell: shell_1234567890"
```

**Termination Process:**
1. Attempts graceful shutdown with `destroy()`
2. Waits up to 5 seconds for process to terminate
3. Forces termination with `destroyForcibly()` if needed
4. Removes process from active shell tracking

**Example Workflow:**

```java
ShellTools shellTools = new ShellTools();

// 1. Start a long-running background task
String start = shellTools.bash(
    "python train_model.py",
    null,
    "Train ML model",
    true,
    toolContext
);
// Returns: "bash_id: shell_1234567890 ..."

// 2. Periodically check progress
String output1 = shellTools.bashOutput("shell_1234567890", ".*epoch.*", toolContext);
// Shows only lines containing "epoch"

Thread.sleep(5000);

String output2 = shellTools.bashOutput("shell_1234567890", null, toolContext);
// Shows all new output since last check

// 3. Kill if needed
String killResult = shellTools.killShell("shell_1234567890", toolContext);
// Returns: "Successfully killed shell: shell_1234567890"
```

**Cross-Platform Support:**

| Platform | Shell Used | Example |
|----------|------------|---------|
| Windows | `cmd.exe /c` | `cmd.exe /c dir` |
| Linux/Mac | `/bin/bash -c` | `/bin/bash -c ls -la` |

**Configuration & Limits:**

| Setting | Default | Maximum | Description |
|---------|---------|---------|-------------|
| Timeout | 120000ms (2 min) | 600000ms (10 min) | Command execution timeout |
| Output Length | N/A | 30000 chars | Output truncated if exceeded |

**Best Practices:**

1. **Use descriptive names**: Provide clear descriptions for better logging
   ```java
   shellTools.bash("git status", null, "Check git status", null, toolContext);
   ```

2. **Background for long tasks**: Use background execution for commands that take > 30 seconds
   ```java
   shellTools.bash("npm run test", null, "Run test suite", true, toolContext);
   ```

3. **Monitor background processes**: Regularly check output of background processes
   ```java
   String output = shellTools.bashOutput(bashId, null, toolContext);
   ```

4. **Filter wisely**: Use regex filters to focus on relevant output
   ```java
   // Only show test failures
   shellTools.bashOutput(bashId, ".*(FAIL|ERROR).*", toolContext);
   ```

5. **Clean up**: Kill background processes when no longer needed
   ```java
   shellTools.killShell(bashId, toolContext);
   ```

### GrepTool

A pure Java implementation of grep functionality that doesn't require external ripgrep installation. Provides powerful search capabilities with regex patterns, glob filtering, and multiple output modes.

**Features:**
- Full Java regex support for pattern matching
- File type filtering (java, js, ts, py, rust, go, etc.)
- Glob pattern matching (`*.java`, `**/*.tsx`)
- Multiple output modes: `files_with_matches`, `count`, `content`
- Context lines (before/after matching lines)
- Case-insensitive search
- Multiline pattern matching
- Configurable limits and depth

**Basic Usage:**

```java
// Default configuration
GrepTool grepTool = new GrepTool();

// Custom configuration
GrepTool customGrepTool = new GrepTool(
    200000,  // maxOutputLength - Maximum output before truncation (default: 100000)
    50,      // maxDepth - Directory traversal depth (default: 100)
    5000     // maxLineLength - Max line length to process (default: 10000)
);
```

**Configuration Parameters:**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `maxOutputLength` | 100000 | Maximum output length before truncation |
| `maxDepth` | 100 | Maximum directory traversal depth (prevents infinite recursion) |
| `maxLineLength` | 10000 | Maximum line length to process (longer lines are skipped) |

**Search Examples:**

```java
// Search for pattern in current directory
String result = grepTool.grep("TODO", null, null, null, null, null, null, null, null, null, null, null, null);

// Search Java files only
String result = grepTool.grep(
    "public class.*",        // pattern
    "./src",                  // path
    null,                     // glob
    OutputMode.files_with_matches,  // outputMode
    null, null, null, null,  // context options
    null,                     // caseInsensitive
    "java",                   // type
    null, null, null          // limit options
);

// Search with glob pattern and show content
String result = grepTool.grep(
    "Error|Exception",        // pattern
    ".",                      // path
    "**/*.log",              // glob
    OutputMode.content,       // outputMode
    2, null, null,           // 2 lines before context
    true,                     // showLineNumbers
    true,                     // caseInsensitive
    null,                     // type
    100, null, null          // limit to 100 lines
);
```

**Output Modes:**
- `files_with_matches` (default) - Shows only file paths containing matches
- `count` - Shows match count per file
- `content` - Shows matching lines with optional context and line numbers

## Skills Development

Skills are markdown files that teach the AI agent how to perform specific tasks. Based on [Claude Code's Agent Skills](https://code.claude.com/docs/en/skills#agent-skills), the AI automatically invokes relevant skills through semantic matching.

### Skill File Structure

Every skill requires a `SKILL.md` file with YAML frontmatter and markdown instructions:

```
.claude/skills/
└── my-skill/
    ├── SKILL.md          # Required: Skill definition
    ├── reference.md      # Optional: Detailed documentation
    ├── examples.md       # Optional: Usage examples
    ├── scripts/          # Optional: Helper scripts
    └── pyproject.toml    # Optional: Python dependencies
```

### Required Frontmatter Fields

```markdown
---
name: my-skill
description: What this skill does and when to use it. Include specific
  capabilities and trigger keywords users would naturally say.
allowed-tools: Read, Grep, Bash
model: claude-sonnet-4-5-20250929
---

# My Skill

## Instructions
Provide clear, step-by-step guidance for the AI agent.

## Examples
Show concrete examples of using this skill.

## Additional Resources
- For complete details, see [reference.md](reference.md)
- For usage examples, see [examples.md](examples.md)
```

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Lowercase letters, numbers, hyphens only (max 64 chars) |
| `description` | Yes | What it does + when to use it (max 1024 chars). Used for semantic matching |
| `allowed-tools` | No | Comma-separated tools the agent can use without asking permission |
| `model` | No | Specific model to use when this skill is active |

### Skill Locations

Where you store a skill determines its scope:

| Location | Path | Scope |
|----------|------|-------|
| **Personal** | `~/.claude/skills/` | User, across all projects |
| **Project** | `.claude/skills/` | Team in this repository |

**Tip**: Use project skills (`.claude/skills/`) for team collaboration by committing them to version control.

### How Skills Are Invoked

The `SkillsToolProvider` implements a three-step process:

1. **Discovery**: At startup, loads skill names and descriptions (lightweight)
2. **Activation**: When a request semantically matches a skill's description, the AI invokes it
3. **Execution**: The full `SKILL.md` content is loaded and the AI follows its instructions

### Best Practices

**Write Effective Descriptions:**

✅ **Good**: Include specific capabilities and trigger keywords
```yaml
description: Extract text and tables from PDF files, fill forms, merge documents.
  Use when working with PDF files or when the user mentions PDFs, forms,
  or document extraction.
```

❌ **Poor**: Too vague
```yaml
description: Helps with documents
```

**Keep Skills Focused:**
- Keep `SKILL.md` under 500 lines
- Use supporting files (`reference.md`, `examples.md`) for detailed content
- Link to supporting files using relative paths

**Progressive Disclosure:**
```markdown
## Quick Start
[Essential instructions here]

## Additional Resources
- For API details, see [reference.md](reference.md)
- For examples, see [examples.md](examples.md)
```

The AI loads supporting files only when needed, preserving context.

**Tool Access for Skills:**

To enable skills to load additional references or run scripts, include the appropriate tools when registering the `SkillsToolProvider`:

```java
ChatClient chatClient = chatClientBuilder
    .defaultToolCallbacks(SkillsToolProvider.create(".claude/skills"))

    // Required for skills to load reference files (reference.md, examples.md, etc.)
    .defaultTools(new FileSystemTools())

    // Required for skills to execute scripts (Python, shell scripts, etc.)
    .defaultTools(new ShellTools())

    // Other tools...
    .build();
```

Without these tools registered:
- Skills cannot read supporting files like `reference.md` or `examples.md`
- Skills cannot execute scripts in the `scripts/` directory
- The AI will be limited to only the content in `SKILL.md`

**Note**: You can restrict tool access per skill using the `allowed-tools` frontmatter field to limit which operations the AI can perform when a specific skill is active.

## Architecture

The library implements Claude Code's tool augmentation patterns:

- **Tool Callbacks** - Spring AI's tool registration mechanism
- **Builder Pattern** - Fluent configuration for complex tools
- **Front-Matter Parsing** - YAML metadata extraction from Markdown
- **Retry & Caching** - Network resilience and performance optimization
- **Safety Checks** - Optional domain validation and content filtering

## Configuration

**application.properties:**
```properties
# Model selection (supports Anthropic, OpenAI, Google)
spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY}
spring.ai.anthropic.options.model=claude-sonnet-4-5-20250929

# Web tools
brave.api.key=${BRAVE_API_KEY}
```

## Examples

See [examples/skills-demo](../examples/skills-demo) for a complete working application demonstrating all tools and agent skills.

## Requirements

- Java 17+
- Spring Boot 3.x / 4.x
- Spring AI 2.0.0+

## License

Apache License 2.0

## Credits

Inspired by [Claude Code](https://code.claude.com) by Anthropic. Architecture insights from:
- [Claude Code Documentation](https://code.claude.com/docs/en/overview)
- [Claude Code Agent Skills](https://code.claude.com/docs/en/skills#agent-skills)
- [Claude Code Internals](https://agiflow.io/blog/claude-code-internals-reverse-engineering-prompt-augmentation/)
- [Claude Code Skills](https://mikhail.io/2025/10/claude-code-skills/)

## Links

- [GitHub Repository](https://github.com/spring-ai-community/spring-ai-agent-utils)
- [Issue Tracker](https://github.com/spring-ai-community/spring-ai-agent-utils/issues)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
