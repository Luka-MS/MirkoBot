# Upgrade Plan: discord-music-bot (20260326210815)

- **Generated**: 2026-03-26 21:08:15
- **HEAD Branch**: feat/luka
- **HEAD Commit ID**: fe2dbd2d25ab02779921ed3418e67ad10687a54b

## Available Tools

**JDKs**
- JDK 17.0.12: `C:\Program Files\Java\jdk-17\bin` (current project JDK, used by step 2)
- JDK 21: **<TO_BE_INSTALLED>** (target version, required by steps 3 and 4)

**Build Tools**
- Maven 3.9.14: `C:\Users\LukaS\.maven\maven-3.9.14\bin` (compatible with Java 21, no upgrade needed)
- Maven Wrapper: not present (using system Maven directly)

## Guidelines

> Note: You can add any specific guidelines or constraints for the upgrade process here if needed, bullet points are preferred.

## Options

- Working branch: appmod/java-upgrade-20260326210815
- Run tests before and after the upgrade: true

## Upgrade Goals

- Upgrade Java from 17 to 21 (LTS)

### Technology Stack

| Technology/Dependency | Current | Min Compatible | Why Incompatible |
| --------------------- | ------- | -------------- | ---------------- |
| Java | 17 | 21 | User requested LTS upgrade |
| Maven | 3.9.14 | 3.9.0 | - (already compatible with Java 21) |
| maven-shade-plugin | 3.5.1 | 3.5.0 | - (already compatible) |
| JDA (Java Discord API) | 5.2.1 | 5.0.0 | - (Java 11+ compatible, works with Java 21) |
| lavaplayer | 2.2.2 | 2.0.0 | - (Java 11+ compatible, works with Java 21) |
| youtube-source | 1.11.0 | 1.0.0 | - (works with Java 21) |
| dotenv-java | 3.0.0 | 3.0.0 | - (works with Java 21) |
| logback-classic | 1.4.14 | 1.4.0 | - (Java 11+ compatible, works with Java 21) |

### Derived Upgrades

- Update `maven.compiler.source` and `maven.compiler.target` from `17` to `21` in `pom.xml` (Java 21 target bytecode is required)
- Update `java.version` property from `17` to `21` in `pom.xml` (consistency)
- No framework or dependency upgrades required — all current dependencies are already compatible with Java 21

## Upgrade Steps

- **Step 1: Setup Environment**
  - **Rationale**: JDK 21 is not installed on the system; it must be installed before upgrading.
  - **Changes to Make**:
    - [ ] Install JDK 21 (target version for the upgrade)
  - **Verification**:
    - Command: `#list_jdks` to confirm JDK 21 is available
    - Expected: JDK 21 available at installation path

---

- **Step 2: Setup Baseline**
  - **Rationale**: Establish pre-upgrade compile and test results using the current Java 17 JDK.
  - **Changes to Make**:
    - [ ] Run baseline compilation with JDK 17
    - [ ] Run baseline tests with JDK 17
  - **Verification**:
    - Command: `mvn clean test-compile -q && mvn clean test` with JDK 17
    - JDK: `C:\Program Files\Java\jdk-17`
    - Expected: Document compilation SUCCESS/FAILURE and test pass rate

---

- **Step 3: Upgrade Java Version to 21**
  - **Rationale**: Update the project's Java version properties in pom.xml from 17 to 21. All dependencies are already compatible, so no additional changes are required.
  - **Changes to Make**:
    - [ ] Update `java.version` property to `21` in `pom.xml`
    - [ ] Update `maven.compiler.source` to `21` in `pom.xml`
    - [ ] Update `maven.compiler.target` to `21` in `pom.xml`
  - **Verification**:
    - Command: `mvn clean test-compile -q`
    - JDK: JDK 21 (installed in step 1)
    - Expected: Compilation SUCCESS with Java 21 bytecode

---

- **Step 4: Final Validation**
  - **Rationale**: Verify all upgrade goals are met, the project compiles and all tests pass with Java 21.
  - **Changes to Make**:
    - [ ] Verify `java.version`, `maven.compiler.source`, `maven.compiler.target` are all `21` in `pom.xml`
    - [ ] Resolve any remaining compilation errors or test failures
    - [ ] Run full test suite; fix any failures iteratively
  - **Verification**:
    - Command: `mvn clean test`
    - JDK: JDK 21
    - Expected: Compilation SUCCESS + 100% tests pass

## Key Challenges

- **JDK 21 not pre-installed**
  - **Challenge**: Java 21 LTS is not present on the system (available: 17, 24, 25).
  - **Strategy**: Install JDK 21 in step 1 using the `#install_jdk` tool before any compilation.

- **No tests in project**
  - **Challenge**: The project has no test classes (only `src/main/java`). The test suite baseline will be 0/0.
  - **Strategy**: Document 0 tests as the baseline; compilation success is the primary validation gate.

## Plan Review

All steps are feasible. The upgrade from Java 17 → 21 is a single-hop LTS-to-LTS upgrade with no breaking API changes in the dependency set. All dependencies (JDA 5.x, lavaplayer 2.x, logback 1.4.x, dotenv-java 3.x) are confirmed compatible with Java 21. Maven 3.9.14 fully supports Java 21. No limitations identified.
