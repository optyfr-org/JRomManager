---
name: javadoc-package
description: Use when creating or updating Javadoc in package-info.java files — generates professional English package-level documentation describing the package purpose, key types, and cross-references.
disable-model-invocation: true
argument-hint: "[optional: package-info.java paths; defaults to active editor file]"
---
# Create or Update Javadoc in `package-info.java`

## Inputs
- **Target files**: Use the `package-info.java` paths provided as arguments. If no arguments are provided, use the file currently open in the active editor.
- **Project context**: JRomManager is a Retro-Gaming ROM manager written in Java, JavaFX, and GWT with a web server. Modules include `jrmcore`, `jrmfx`, `jrmserver`, `jrmstandalone`, `jrmcli`, `WebClient`, and `res-icons`.

## Role
You are an expert Java code documentation developer.

## Steps
1. Read the target `package-info.java`, then survey the classes/interfaces/enums in the same package to understand the package's purpose and its key types.
2. Write a concise first-sentence summary of what the package provides, ending with a period.
3. Follow with one or more detailed paragraphs (using `<p>`) explaining the package's role, how its types cooperate, and how it integrates with the rest of JRomManager.
4. Where helpful, add a `<ul>` listing the most important types, each as an `{@link}` cross-reference with a short description.
5. Append `@author optyfr` (the project's convention). Add `@since` only when a meaningful value is known.
6. Verify file integrity after editing: introduce no syntax or compilation errors, and change no runtime behavior. The `package` declaration must remain intact.
7. After editing, give a brief chat-side summary of what was documented per file. Do not write this summary to any file.

## Format
- Javadoc is written directly in the source, never as Markdown-formatted comments.
- Files are UTF-8 without BOM.
- Keep Javadoc HTML-safe (`<p>`, `<ul>`, `<li>`, `{@link ...}`, `{@code ...}`).
- Tone: technical and professional. Audience: project managers and developers.

## Constraints
- Modify **only** the target `package-info.java` file(s). Within them, edit only the Javadoc comment and add imports strictly needed for `{@link}` references. Do not alter the `package` declaration or any code.

## Template

```java
/**
 * <one-sentence summary of what the package provides>.
 * <p>
 * <detailed paragraph: the package's role, how its types cooperate, and integration with JRomManager>.
 * </p>
 * <ul>
 * <li>{@link <fully.qualified.TypeName>}: <short description of the type's role>.</li>
 * </ul>
 *
 * @author optyfr
 * 
 * @since <version, if known>
 */
package <fully.qualified.package.name>;
```

## Example — based on the project's `jrm.digest` package

```java
/**
 * Provides hash computing utilities and digest implementations.
 * <p>
 * This package integrates different parsing and checksum algorithms (such as CRC32, MD5, and SHA-1) into a unified abstract
 * interface represented by {@link jrm.digest.MDigest}.
 * </p>
 * <ul>
 * <li>{@link jrm.digest.MDigest}: Abstract base for all hash implementations, exposing the algorithm and hex output contract.</li>
 * <li>{@link jrm.digest.CRCDigest}: CRC32 implementation wrapping {@link java.util.zip.CRC32}.</li>
 * <li>{@link jrm.digest.MsgDigest}: MD5/SHA-1 implementation wrapping {@link java.security.MessageDigest}.</li>
 * </ul>
 *
 * @author optyfr
 */
package jrm.digest;
```
