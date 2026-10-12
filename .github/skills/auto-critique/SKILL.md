---
name: auto-critique
description: Use when you want to auto-critique and refine recently completed work — exhaustively reviews files edited earlier in the conversation against project conventions and correctness, presents structured findings, and on confirmation applies fixes then verifies with compile/lint and SonarQube checks. Useful after a Javadoc, refactor, or feature task to self-review and harden the result.
disable-model-invocation: true
argument-hint: "[optional: file paths to critique; defaults to files edited earlier in this conversation]"
---
# Auto-Critique and Refine Previous Work

## Inputs
- **Target files**: the files you (the agent) created or modified earlier in this conversation — the "previous work". This may be the result of any prior prompt (e.g. `/javadoc`, `/javadoc-package`, or any other task), on files of any type. If arguments are provided, use those instead. If no previous work is identifiable and no arguments are given, first run `git status` to list recently changed files, then confirm the selection with the user before proceeding.

## Role
You are a meticulous, unbiased code reviewer and refiner. Do not rubber-stamp your own previous work.

## Steps
1. **Identify targets**: determine the set of files to critique (see Inputs).
2. **Critique**: for each file, re-read it in full and review it exhaustively against:
   - **Project conventions** — any matching `.github/instructions/*.instructions.md` whose `applyTo` pattern covers the file type (for example `javadoc.instructions.md` for `*.java`), plus the file/module-local style already present. If no matching instruction file exists, fall back to the conventions implied by the prior prompt that produced the work and the existing style in the file.
   - **Correctness** — syntax, logic errors, edge cases, null handling, broken cross-references (e.g. `{@link}` in Java, dead links in Markdown), and unintended behavior changes introduced by the previous work.
   - **Quality** — clarity, consistency, naming, completeness, and anything that would not pass review.
3. **Present findings**: report a structured critique in chat — per file, list each finding with a severity (`blocker` / `major` / `minor`), its location, and the proposed fix. **Do not apply fixes yet.** If a file is clean, say so explicitly.
4. **Confirm**: ask the user which fixes to apply. Offer sensible choices (e.g. *Apply all*, *Apply blockers/major only*, *Let me specify*) and allow free-form input so they can pick a subset or skip. Wait for confirmation before touching any file.
5. **Apply fixes**: apply only the confirmed fixes directly into the files. Do not output file contents to the console.
6. **Verify**: after fixes, run a compile/lint check (`get_errors`) on every touched file, plus a SonarQube analysis (`analyze_file_list`) on the touched files. Follow the SonarQube workflow in `.github/instructions/sonarqube_mcp.instructions.md` (disable automatic analysis before making fixes, analyze the touched files at the end, then re-enable automatic analysis). If issues remain and are safely fixable, address them — the number of retries is left to your judgment, but stop and report rather than looping indefinitely.
7. **Summarize**: give a brief chat-side summary of what was fixed per file and the final verification status (errors remaining, SonarQube findings count).

## Expectations
**Format**: fixes go directly into the source files. The critique itself is presented in chat as a structured list; do **not** write the critique to any file.
**Tone**: technical and professional.
**Audience**: the developer who produced the work being critiqued.

## Constraints
- Critique and fix **only** the target files (plus imports strictly needed for a fix). Do not alter unrelated code or change runtime behavior.
- When fixing files of a type covered by an instruction file (e.g. `*.java` covered by `javadoc.instructions.md`), follow that instruction file.
- Do not introduce new errors. If a fix is risky or ambiguous, flag it and leave it for the user rather than guessing.
- A finding must describe a concrete, actionable problem — never pad the critique with non-issues.

## Example critique entry

```
ReportTree.java
  [major]   getSubjectSetFolderSuffix: the "CREATE"/"CREATEFULL" branch returns "_blue" only when isFixable is true, but the Javadoc does not mention the fixable distinction — clarify the @return wording.
  [minor]   IS_FIXABLE constant Javadoc says "whether a node is fixable" but the attribute is named "isFixable"; consider noting it mirrors the record attribute name.
  [—]       fetchDetail, applyFilter: clean.

autocritique.prompt.md
  [minor]   Step 6 references "limited retries" — replace with explicit guidance to stop and report rather than looping indefinitely.
  [—]       frontmatter, description: clean.
```
