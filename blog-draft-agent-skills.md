---
title: "Generic, LLM agnostic Agent Skills for Spring AI"
author: Christian Tzolov
date: 2026-01-05
category: spring-ai
tags: [spring-ai, agent-skills, tools, llm, anthropic, claude]
---


Agent Skills are folders of instructions, scripts, and resources that agents can discover and use to do things more accurately and efficiently.
​Rather than hardcoding knowledge into prompts or creating specialized tools for every task, skills allow AI agents to dynamically load domain-specific expertise when needed. This article shows how to implement a Generic Agent Skills support for Spring AI. While inspired by [Claude Code](https://code.claude.com) it designed to work across many LLM providers.

## What Are Agent Skills?

Agent Skills are modular capabilities packaged as Markdown files with `YAML frontmatter`. Unlike traditional function tools that execute code, skills provide **instructions and knowledge** to AI agents, enabling them to perform specialized tasks through progressive disclosure of information.

Skills use **semantic matching**: the AI automatically loads them based on matching user requests to skill descriptions, eliminating the need for explicit commands or special syntax.

**A Simple Example:**

```yaml
---
name: api-docs
description: Generate API documentation from source code. Analyze REST endpoints,
  GraphQL schemas, or OpenAPI specs. Use when user mentions API docs, ...
---

# API Documentation Generator

## Instructions
Step-by-step guidance for analyzing code and generating documentation...
```

When a user asks "Generate API docs for my Spring Boot app," the AI recognizes the match and automatically invokes this skill without requiring explicit commands like `/api-docs` or special tool calls.

Find more about the [Agent Skills Specification](https://agentskills.io/specification).

### Why Generic Skills for Spring AI

**Token Efficiency Through Progressive Disclosure** - Skills load in three tiers: metadata only at startup (~100 tokens per skill), full instructions when invoked (~5k tokens), and supporting files on-demand. You can register hundreds of skills while keeping your context window lean. This means adding 50 skills costs only ~5KB of context at startup versus 250KB if all content were loaded upfront.

**Model Agnostic - No Vendor Lock-In** - Unlike Claude Code and Claude API Skills implementations, which are tightly coupled to Anthropic's models, this Spring AI implementation works across LLM providers: Anthropic Claude, OpenAI GPT, Google Gemini, and any other Spring AI-supported model. Switch models without rewriting skills.

**Version Control Friendly** - Skills are plain Markdown files with YAML frontmatter, making them perfect for Git. Track changes, review diffs, and collaborate just like code. No proprietary formats or binary data.

**Reusable and Composable** - Skills can be shared across projects (personal skills in `~/.claude/skills/`), version-controlled with your code (project skills in `.claude/skills/`), combined to create complex workflows, and extended with helper scripts and reference materials.

## How SkillsTool Works

SkillsTool is a Spring AI tool that provides a single function called `Skill`. This function enables the AI to discover and load specialized knowledge modules on demand. Skills work in conjunction with other Spring AI tools like FileSystemTools (for reading reference files) and ShellTools (for executing helper scripts).

<img style="display: block; margin: auto;" src="https://raw.githubusercontent.com/spring-io/spring-io-static/refs/heads/main/blog/tzolov/20260105/skillstool1.png" width="450" align="right" alt="Figure 1: Skills discovery and registration flow showing how SKILL.md files are parsed and registered at startup"/>

Skills operate through a three-step process:

**1. Discovery (at startup)** - During initialization, SkillsTool scans configured skills directories (such as `.claude/skills/`) and parses the YAML frontmatter from each `SKILL.md` file. It extracts the `name` and `description` fields to build a lightweight skill registry. This registry is embedded directly into the `Skill` tool's description within an `<available_skills>` XML block, making it visible to the LLM without consuming conversation context.

**2. Semantic Matching (during conversation)** - When a user makes a request, the LLM examines the skill descriptions embedded in the tool definition. If the LLM determines a user request semantically matches a skill's description, it invokes the `Skill` tool with the skill name as a parameter. The matching happens naturally through the LLM's understanding—no explicit keyword matching or routing logic required.

**3. Execution (on skill invocation)** - When the `Skill` tool is called, SkillsTool loads the full `SKILL.md` content from disk and returns it to the LLM along with the skill's base directory path. The LLM then follows the instructions in the skill content. If the skill references additional files (like `reference.md`) or helper scripts, the LLM uses FileSystemTools' `Read` function or ShellTools' `Bash` function to access them—but only when needed, preserving context efficiency.


### Skills with References and Scripts

Skills can include additional reference files with instructions that are not part of SKILL.md but referenced within it. They can also include executable scripts.

<img style="display: block; margin: auto;" src="https://raw.githubusercontent.com/spring-io/spring-io-static/refs/heads/main/blog/tzolov/20260105/skillstool2.png" width="550" align="right" alt="Figure 2: Skill execution flow showing how the LLM uses FileSystemTools and ShellTools to access skill resources"/>

Here's an example from the ai-tutor skill that includes a YouTube transcript extraction helper:

**Directory Structure:**
```
.claude/skills/ai-tutor/
├── SKILL.md
├── scripts/
│   └── get_youtube_transcript.py
└── research_methodology.md
```

**In SKILL.md:**
```markdown
**If concept is unfamiliar or requires research:** Load `research_methodology.md` for detailed guidance.

**If user provides YouTube video:**
Call `uv run scripts/get_youtube_transcript.py <video_url_or_id>`
for video's transcript.
```


When a user asks "Explain the concepts from this video: https://youtube.com/watch?v=abc123. Follow the research methodology", the AI:

1. Invokes the ai-tutor skill and loads its SKILL.md content
2. Recognizes the need for research methodology and uses `Read` to load `research_methodology.md`
3. Recognizes the YouTube URL and uses `Bash` to execute the helper script via ShellTools
4. Receives the full transcript from the script
5. Explains the concepts using the teaching framework and research methodology

The script code never enters the context window—only the output does, making this token-efficient.


## Real-World Example: The AI Tutor Skill

TODO

## Integration with Existing Tools

TODO

### Current Limitations

TODO

- Scripts are not run in a sandbox
- Missing human in the loop 

## Getting Started

Ready to add Agent Skills to your Spring AI project?

**1. Add the dependency:**

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-agent-utils</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

**2. Configure your agent:**

```java
@SpringBootApplication
public class Application {

    @Bean
    CommandLineRunner demo(ChatClient.Builder chatClientBuilder) {
        return args -> {
            ChatClient chatClient = chatClientBuilder
                .defaultToolCallbacks(SkillsTool.builder()
                    .addSkillsDirectory(".claude/skills")
                    .build())
                .defaultTools(new FileSystemTools())
                .defaultTools(new ShellTools())
                .build();

            String response = chatClient.prompt()
                .user("Your task here")
                .call()
                .content();
        };
    }
}
```

**3. Create your first skill:**

```bash
mkdir -p .claude/skills/my-skill
cat > .claude/skills/my-skill/SKILL.md << 'EOF'
---
name: my-skill
description: What your skill does and when to use it. Include trigger keywords.
---

# My Skill

## Instructions
Step-by-step guidance for the AI...
EOF
```

## Conclusion

Agent Skills provide a practical way to organize and extend AI agent capabilities. By separating domain knowledge (skills) from execution capabilities (tools), you get:

- **Maintainable systems** - Update skills without changing code
- **Reusable components** - Share skills across projects and teams
- **Efficient context usage** - Add many skills without context penalties
- **Model flexibility** - Works with any Spring AI-supported LLM

The spring-ai-agent-utils implementation brings this pattern to the Spring ecosystem in a model-agnostic way. Whether you're building coding assistants, documentation generators, or domain-specific AI agents, skills provide a flexible foundation for organizing agent knowledge.

## Resources

- **GitHub Repository**: [spring-ai-agent-utils](https://github.com/spring-ai-community/spring-ai-agent-utils)
- **Complete Examples**:
  - [code-agent-demo](https://github.com/spring-ai-community/spring-ai-agent-utils/tree/main/examples/code-agent-demo) - Full-featured AI coding assistant
  - [skills-demo](https://github.com/spring-ai-community/spring-ai-agent-utils/tree/main/examples/skills-demo) - Focused skills demonstration
- **Agent Skills Specification**: [agentskills.io](https://agentskills.io/specification)
- **Claude Code Documentation**: [code.claude.com/docs](https://code.claude.com/docs/en/skills)
- **Spring AI Documentation**: [docs.spring.io/spring-ai](https://docs.spring.io/spring-ai/reference/)

The source code for all examples in this article is available in the spring-ai-agent-utils repository.

---

*Christian Tzolov ([@tzolov](https://github.com/tzolov)) is a Spring team member and contributor to Spring AI. He specializes in building agent systems and LLM-powered applications.*
