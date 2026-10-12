---
name: write-parser-tests
description: Generate comprehensive JUnit 5 tests for parser classes — validates file format parsing, data extraction, error handling, and edge cases using real-world fixtures.
disable-model-invocation: true
argument-hint: "[Parser class to test; e.g., TorrentParser, CHDHeader, Profile]"
---
# Write Tests for Parser Classes

## Inputs
- **Target parser**: Any parser class in `jrmcore/src/main/java/jrm/` that reads file formats
- **Test data**: Fixture files in `jrmcore/src/test/resources/` or minimal synthetic inputs
- **Project context**: JRomManager parses various formats: MAME DAT XML, BitTorrent metainfo, CHD disk images, INI configs, NFO profiles

## Role
You are an expert Java test engineer specializing in file format validation and data integrity testing.

## Steps
1. Read the target parser class to understand:
   - Entry points (public static methods, constructors)
   - Input formats (file paths, byte arrays, streams)
   - Output model (parsed objects, data structures)
   - Error handling (exceptions, validation rules)

2. Examine existing test fixtures in `jrmcore/src/test/resources/`:
   - `dats/` - MAME DAT XML files (merged, split, software lists)
   - `torrents/` - Real-world .torrent files from Linux distributions
   - Other format-specific test data

3. Generate comprehensive JUnit 5 tests that validate:
   - **Happy path**: Parse valid input → verify all extracted fields
   - **Required fields**: Validate presence of mandatory attributes
   - **Optional fields**: Test parsing with/without optional data
   - **Edge cases**: Empty values, malformed data, boundary conditions
   - **Error conditions**: Invalid format, missing files, corrupt data
   - **Real-world scenarios**: Use production fixtures when available

4. Use AssertJ assertions for readable, fluent test expressions.

5. Follow existing test patterns in `jrmcore/src/test/java/jrm/` for consistency.

6. Organize tests by feature or behavior being validated.

## Expectations
**Format**
- Use JUnit 5 (`@Test`, `@ParameterizedTest`, `@TempDir`, `@DisplayName`, `@Nested`)
- Use AssertJ fluent assertions (`assertThat(...).isEqualTo(...)`, `assertThatThrownBy(...)`)
- Structure tests as: arrange → act → assert
- Name test methods descriptively: `should_<behavior>_when_<condition>()` or `<feature>_<scenario>()`

**Coverage**
- Test both success and failure paths
- Validate all public methods of the parser
- Test with real-world fixtures when available
- Use `@ParameterizedTest` for repetitive scenarios with multiple inputs
- Verify extracted data types (strings, numbers, dates, checksums)

**Tone**: Technical and precise.
**Audience**: Developers maintaining parser code.

## Constraints
- Create tests in `jrmcore/src/test/java/jrm/<package>/` matching the parser's package
- Do not modify production parser code
- Use existing fixtures or create minimal synthetic inputs for edge cases
- Avoid mocking parsers — test them with real inputs
- Keep tests focused and independent

## Common Parser Patterns

### File-based parsers (TorrentParser, Profile)
```java
@Test
@DisplayName("Should parse valid file and extract all metadata")
void shouldParseValidFileAndExtractAllMetadata() throws Exception {
    // Arrange
    Path inputFile = Path.of("src/test/resources/format/valid-file.ext");
    
    // Act
    ParsedResult result = MyParser.parse(inputFile);
    
    // Assert
    assertThat(result.getField1()).isEqualTo("expected-value");
    assertThat(result.getField2()).isGreaterThan(0);
    assertThat(result.getItems()).hasSize(5);
}
```

### Byte-stream parsers (CHDHeader, bencoding)
```java
@Test
@DisplayName("Should parse byte array and validate structure")
void shouldParseByteArrayAndValidateStructure() {
    // Arrange
    byte[] validData = new byte[] { /* valid bytes */ };
    
    // Act
    ParsedHeader header = new CHDHeader(validData);
    
    // Assert
    assertThat(header.getTag()).isEqualTo("MComprHD");
    assertThat(header.getLength()).isEqualTo(128);
    assertThat(header.getVersion()).isEqualTo(5);
}
```

### Config file parsers (INI, properties)
```java
@Test
@DisplayName("Should parse config with sections and key-value pairs")
void shouldParseConfigWithSectionsAndKeyValues() throws Exception {
    // Arrange
    Path configFile = Path.of("src/test/resources/config/test.ini");
    
    // Act
    ConfigParser parser = new ConfigParser(configFile);
    
    // Assert
    assertThat(parser.getSection("general"))
        .containsEntry("key1", "value1")
        .containsEntry("key2", "value2");
}
```

## Example — Complete parser test suite

```java
package jrm.io.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MyParserTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("Happy Path Tests")
    class HappyPathTests {
        
        @Test
        @DisplayName("Should parse valid file with all required fields")
        void shouldParseValidFileWithAllRequiredFields() throws Exception {
            Path inputFile = Path.of("src/test/resources/format/valid.ext");
            ParsedResult result = MyParser.parse(inputFile);
            
            assertThat(result).isNotNull();
            assertThat(result.getName()).isNotBlank();
            assertThat(result.getItems()).isNotEmpty();
        }
        
        @Test
        @DisplayName("Should parse file with optional fields present")
        void shouldParseFileWithOptionalFieldsPresent() throws Exception {
            Path inputFile = Path.of("src/test/resources/format/with-optional.ext");
            ParsedResult result = MyParser.parse(inputFile);
            
            assertThat(result.getOptionalField()).isNotNull();
            assertThat(result.getOptionalField()).isEqualTo("optional-value");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {
        
        @Test
        @DisplayName("Should throw exception when file is missing")
        void shouldThrowExceptionWhenFileIsMissing() {
            Path missingFile = Path.of("src/test/resources/format/nonexistent.ext");
            
            assertThatThrownBy(() -> MyParser.parse(missingFile))
                .isInstanceOf(IOException.class);
        }
        
        @Test
        @DisplayName("Should throw exception when required field is missing")
        void shouldThrowExceptionWhenRequiredFieldIsMissing() {
            byte[] invalidData = createMinimalInvalidData();
            
            assertThatThrownBy(() -> MyParser.parse(invalidData))
                .isInstanceOf(ParseException.class)
                .hasMessageContaining("required field");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {
        
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t", "\n"})
        @DisplayName("Should handle empty or whitespace-only values")
        void shouldHandleEmptyOrWhitespaceValues(String emptyValue) {
            // Test implementation
        }
        
        @Test
        @DisplayName("Should handle very large files")
        void shouldHandleVeryLargeFiles() throws Exception {
            // Test implementation with large fixture
        }
    }
}
```

## Checklist Before Submitting
- [ ] All public parser methods have at least one test
- [ ] Happy path, error conditions, and edge cases are covered
- [ ] Tests use real-world fixtures when available
- [ ] All tests pass: `./gradlew test`
- [ ] Test names are descriptive and `@DisplayName` is used
- [ ] No `System.out.println` or commented-out code
- [ ] Tests are independent and deterministic
