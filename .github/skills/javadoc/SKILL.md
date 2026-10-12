---
name: javadoc
description: Use when creating or updating Javadoc in Java source files — documents classes, methods, and fields (including private and restricted members) with professional Javadoc-compliant English comments, and applies Lombok @Getter/@Setter/@Data field documentation rules.
disable-model-invocation: true
argument-hint: "[optional: Java file paths; defaults to active editor file]"
---
# Create or Update Javadoc in Java Source Files

## Inputs
- **Target files**: Use the Java file paths provided as arguments. If no arguments are provided, use the file currently open in the active editor.
- **Project context**: JRomManager is a Retro-Gaming ROM manager written in Java, JavaFX, and GWT with a web server. Modules include `jrmcore`, `jrmfx`, `jrmserver`, `jrmstandalone`, `jrmcli`, `WebClient`, and `res-icons`.

## Role
You are an expert Java code documentation developer.

## Steps
1. Read and analyze each target file, plus any types it references, to fully understand its purpose and behavior before writing.
2. Maintain writing uniformity and consistency with the existing Javadoc style found in the file and its module.
3. Write Javadoc comments at the **class**, **method**, and **field** levels — including private, package-private, and protected members.
4. Fix **only** pre-existing Javadoc that is incomplete, inaccurate, or not in English. Leave well-formed, accurate English Javadoc untouched.
5. Translate any non-English comments or Javadoc into professional English.
6. Verify file integrity after editing: introduce no syntax or compilation errors, and change no runtime behavior.
7. After editing, give a brief chat-side summary of what was documented or fixed per file. Do not write this summary to any file.

## Expectations
**Format**
- Javadoc goes directly in the Java source code. Do **not** generate Markdown-formatted Javadoc.
- Save files as UTF-8 without BOM.
- Use standard Javadoc tags: `@param`, `@return`, `@throws`, `@see`, `@since`, `{@link ...}`, `{@code ...}`. The `@since` and `@author` tags are optional; include them only when a meaningful value is available.
- Keep Javadoc HTML-safe where HTML elements are used (e.g. `<p>`, `<pre>`).
- The first sentence is a concise summary ending with a period, followed by detailed paragraphs as needed.

**Tone**: technical and professional.
**Audience**: project managers and developers.

## Constraints
- Modify **only** the target Java file(s). Within them, you may edit Javadoc/comment text and add imports strictly needed for `{@link}` references. Do not alter code logic.
- For **Lombok** annotations:
  - Declare `@param` in a field's Javadoc if `@Setter` is present on the field, or `@Data` on its class.
  - Declare `@return` in a field's Javadoc if `@Getter` is present on the field, or `@Data` on its class.
  - Delombok will distribute these comments across the methods it generates — do **not** mention Lombok in the Javadoc text itself.

## Example — class, field, and method

```java
/**
 * Wraps a standard JDK {@link java.security.MessageDigest} to compute MD5 or SHA-1 hashes.
 * <p>
 * Concrete instances are created with a named algorithm and updated with byte
 * sequences; the resulting hash is exposed as a lower-case hexadecimal string.
 *
 * @since 2.5
 */
public class MsgDigest extends MDigest
{
    /** The underlying JDK message digest instance. */
    private final transient MessageDigest digest;

    /**
     * Constructs a new digest for the given algorithm.
     *
     * @param algorithm the digest algorithm to use
     * @throws NoSuchAlgorithmException if {@code algorithm} is not available
     * @throws NullPointerException if {@code algorithm} is {@code null}
     */
    public MsgDigest(final MDigest.Algo algorithm) throws NoSuchAlgorithmException
    {
        // ...
    }
}
```

## Example — Lombok-annotated fields

```java
public class User
{
    /**
     * The user display name.
     * @param name the user display name
     */
    @Setter
    private String name;

    /**
     * The user roles.
     * @return the user roles
     */
    @Getter
    private String[] roles;
}
```
