---
name: write-profile-tests
description: Generate JUnit tests for Profile code using MAME DAT fixtures — validates XML parsing, data model construction, machine/software relationships, and filtering logic.
disable-model-invocation: true
argument-hint: "[Profile class or method to test; defaults to Profile.java]"
---
# Write Tests for Profile Code Using DAT Data

## Inputs
- **Target code**: Java classes or methods in `jrm.profile` package to test
- **Test data**: MAME DAT XML files in `jrmcore/src/test/resources/dats/`
- **Project context**: JRomManager is a Retro-Gaming ROM manager. The `Profile` class parses MAME DAT XML catalogs and builds a complex object model with machines, software lists, ROMs, disks, slots, and filters.

## Role
You are an expert Java test engineer specializing in parser validation and data model integrity testing.

## Steps
1. Read the target code to understand parsing logic, state management, and object relationships.
2. Examine the DAT XML fixtures in `jrmcore/src/test/resources/dats/` to understand test data structure.
3. Generate comprehensive JUnit 5 tests that validate:
   - **XML parsing**: Correct extraction of machine, software, ROM, disk, slot, and device attributes
   - **Data model construction**: Proper object graph assembly, parent-child relationships, and cross-references
   - **Edge cases**: Optional fields, missing attributes, clone relationships, device references
   - **Filtering logic**: Machine/software status filters, system boundaries, year ranges
   - **Checksum tracking**: MD5, SHA-1 detection flags, suspicious CRC identification
4. Use AssertJ assertions for readable, fluent test expressions.
5. Follow existing test patterns in `jrmcore/src/test/java/jrm/` for consistency.
6. Use `@DisplayName` annotations to describe test intent clearly.
7. Organize tests by feature or behavior being validated.

## Expectations
**Format**
- Use JUnit 5 (`@Test`, `@ParameterizedTest`, `@TempDir`, `@DisplayName`)
- Use AssertJ fluent assertions (`assertThat(...).isEqualTo(...)`)
- Load DAT files from classpath resources using `getClass().getResourceAsStream()` or `Path.of()`
- Structure tests as: arrange → act → assert
- Name test methods descriptively: `should_<behavior>_when_<condition>()` or `<feature>_<scenario>()`

**Coverage**
- Test both happy paths and error conditions
- Validate parsed counts match expected values (machines, ROMs, disks, samples)
- Verify parent-child relationships (machine → ROMs, machine → software lists, software → parts → data areas)
- Test clone relationships (`cloneof` fields)
- Validate device definitions and slot configurations

**Tone**: Technical and precise.
**Audience**: Developers maintaining the profile parsing code.

## Constraints
- Create tests in `jrmcore/src/test/java/jrm/profile/` package
- Do not modify production code in `jrm.profile` package
- Use only existing DAT fixtures or create minimal synthetic XML for edge cases
- Avoid testing UI components or progress handlers (mock if needed)
- Keep tests focused and independent

## Example — Parsing validation

```java
package jrm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfileParserTest {

    @Test
    @DisplayName("Should parse MAME DAT and extract correct machine count")
    void shouldParseMameDatWithCorrectMachineCount() throws Exception {
        // Arrange
        Path datFile = Path.of("src/test/resources/dats/MAME 0.288 ROMs (merged).xml");
        
        // Act
        Profile profile = Profile.loadDat(datFile);
        
        // Assert
        assertThat(profile.getMachinesCnt()).isGreaterThan(0);
        assertThat(profile.getMachineListList()).isNotEmpty();
    }

    @Test
    @DisplayName("Should parse machine with ROMs and verify checksums")
    void shouldParseMachineWithRomsAndVerifyChecksums() throws Exception {
        // Arrange
        Path datFile = Path.of("src/test/resources/dats/MAME 0.288 ROMs (merged).xml");
        Profile profile = Profile.loadDat(datFile);
        
        // Act - find a specific machine
        var machine = profile.getMachineListList().get(0)
            .getList().stream()
            .filter(m -> "1on1gov".equals(m.getName()))
            .findFirst()
            .orElseThrow();
        
        // Assert
        assertThat(machine.getRoms()).isNotEmpty();
        assertThat(machine.getRoms()).allSatisfy(rom -> {
            assertThat(rom.getName()).isNotBlank();
            assertThat(rom.getSize()).isGreaterThan(0);
            assertThat(rom.getCrc()).matches("^[0-9a-f]{8}$");
        });
    }

    @Test
    @DisplayName("Should parse software list with parent software and clones")
    void shouldParseSoftwareListWithParentAndClones() throws Exception {
        // Arrange
        Path datFile = Path.of("src/test/resources/dats/MAME 0.288 Software List ROMs (merged)/a2600.xml");
        Profile profile = Profile.loadDat(datFile);
        
        // Act
        var softwareList = profile.getMachineListList().get(0)
            .getSoftwareLists().stream()
            .findFirst()
            .orElseThrow();
        
        // Assert
        assertThat(softwareList.getList()).isNotEmpty();
        assertThat(softwareList.isSha1()).isTrue();
        
        // Verify clone relationships
        var clones = softwareList.getList().stream()
            .filter(sw -> sw.getCloneof() != null)
            .toList();
        assertThat(clones).isNotEmpty();
    }
}
```

## Example — Edge case validation

```java
@Test
@DisplayName("Should handle machine with optional ROMs")
void shouldHandleMachineWithOptionalRoms() throws Exception {
    // Arrange & Act
    Profile profile = Profile.loadDat(datFile);
    
    // Assert - find machine with optional ROMs
    var optionalRoms = profile.getMachineListList().stream()
        .flatMap(ml -> ml.getList().stream())
        .flatMap(m -> m.getRoms().stream())
        .filter(Rom::isOptional)
        .toList();
    
    assertThat(optionalRoms).isNotEmpty();
    assertThat(optionalRoms).allSatisfy(rom -> {
        assertThat(rom.isOptional()).isTrue();
    });
}

@Test
@DisplayName("Should track SHA-1 and MD5 presence flags")
void shouldTrackHashPresenceFlags() throws Exception {
    // Arrange & Act
    Profile profile = Profile.loadDat(datFile);
    
    // Assert
    assertThat(profile.isSha1Roms()).isTrue();
    assertThat(profile.isMd5Roms()).isFalse(); // if DAT only has SHA-1
}
```
