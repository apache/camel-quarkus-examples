# Apache Camel Quarkus Examples - AI Agent Guidelines

Guidelines for AI agents contributing examples to **apache/camel-quarkus-examples**.

This repository contains runnable example applications that demonstrate Apache
Camel running on [Quarkus](https://quarkus.io/), in JVM and native mode.

For the canonical, project-wide agent rules, see the Camel Quarkus
[`AGENTS.md`](https://github.com/apache/camel-quarkus/blob/main/AGENTS.md).
The notes below adapt the essentials to this examples repository — note that
Camel Quarkus follows GitHub-based conventions that differ from Apache Camel
core (e.g. GitHub issues instead of JIRA).

## Project Info

- Build: Maven 3.9+ (use the provided `./mvnw` wrapper)
- Java: 17+ (CI also exercises JDK 21)
- BOMs: `quarkus-bom` + `quarkus-camel-bom` (extension versions are inherited,
  never pinned per-dependency)
- Tests: `@QuarkusTest` (JVM) and `@QuarkusIntegrationTest` (packaged/native)
- Issue tracker: **GitHub issues** at https://github.com/apache/camel-quarkus/issues
  (Camel Quarkus does **not** use JIRA)

## Rules of Engagement (essentials)

- **Attribution**: every AI-generated PR description, review or issue comment MUST
  identify itself as AI-generated and name the human operator, e.g.
  `_Claude Code on behalf of [Human Name]_`.
- **Target the `camel-quarkus-main` branch** for PRs (it tracks the current
  Camel Quarkus SNAPSHOT), as stated in the repository's PR template. The `main`
  branch tracks the latest released Quarkus Platform.
- **One example per PR**, kept small and self-contained. Do not exceed 10 PRs per
  day per operator. Quality over quantity.
- **Branch from your own fork** (not apache/), with a descriptive name (include
  the GitHub issue number when there is one, e.g. `123-rest-json-example`).
  Delete the branch after merge/close. Never push to a branch you did not create.
- **Green CI is required**, including license/format/import checks and (where
  applicable) the native build.
- **Never merge** without at least one human approval; never approve your own PR.

## Repository structure

- A root aggregator `pom.xml` (packaging `pom`) lists every example under
  `<modules>`. Each example, however, is **parentless and standalone** — it
  declares no `<parent>` and imports the Quarkus + Camel Quarkus BOMs directly,
  so it can be built on its own.

## Anatomy of an example

```
<example>/
├── README.adoc                       # AsciiDoc: dev, package, native commands
├── pom.xml                           # imports quarkus-bom + quarkus-camel-bom
├── src/main/java/org/acme/...        # *Route.java (extends RouteBuilder, @ApplicationScoped)
│                                     # beans (@ApplicationScoped / @Named)
├── src/main/resources/
│   ├── application.properties        # Quarkus + Camel config (ASF header)
│   └── camel/*.yaml                  # optional YAML DSL routes
└── src/test/java/org/acme/...        # @QuarkusTest / @QuarkusIntegrationTest
```

- Beans reached reflectively (e.g. via the `bean` component) may need
  `@RegisterForReflection` so they work in native mode.

## Build, run and validate

```bash
# dev mode with live reload
mvn clean compile quarkus:dev

# JVM package + run
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar

# native build + run (needs GraalVM or the container builder)
mvn clean package -Dnative
./target/*-runner

# tests
mvn clean test

# license / formatting / imports (run before pushing)
mvn license:check formatter:validate impsort:check
```

CI validates platform compatibility (`cq:examples-check-platform`), license and
formatting, compilation, tests, and native builds (Linux/Windows, JDK 17/21).

## Conventions

- **Maven coordinates**: `groupId` = `org.apache.camel.quarkus.examples`;
  `artifactId` = `camel-quarkus-examples-<name>` (matches the directory).
- **Java package**: `org.acme.<domain>.<feature>` (e.g. `org.acme.timer.log`).
- **License headers**: 13-line ASF header on every `.java`, `.xml`,
  `.properties`, `.groovy` (template in `header.txt`), enforced by the license
  plugin.
- **Config**: inject with MicroProfile `@ConfigProperty(name = "...")` and back
  it with keys in `application.properties`.
- **README**: AsciiDoc, with `Start in Development mode`, `Package and run`, and
  (where relevant) native sections, plus a Feedback section.

## Adding a new example (checklist)

1. Scaffold the example with the Camel Quarkus Maven mojo — it creates the
   module and registers it in the root aggregator `pom.xml`:
   ```bash
   mvn org.l2x6.cq:cq-maven-plugin:create-example \
     -Dcq.artifactIdBase="yaml-to-log" \
     -Dcq.exampleName="YAML To Log" \
     -Dcq.exampleDescription="Shows how to use a YAML route with the log EIP"
   ```
2. Implement the routes/beans under `org.acme.<domain>.<feature>`, configure
   `application.properties`, add tests, and complete the generated `README.adoc`.
3. Verify locally: `mvn clean test`, `mvn quarkus:dev`, and — if the example is
   expected to — `mvn clean package -Dnative`.
4. Run `mvn license:check formatter:validate impsort:check`.
5. Open the PR from your fork **against `camel-quarkus-main`**, reference the
   GitHub issue (if any), and request review from active committers.

## Links

- Camel Quarkus user guide: https://camel.apache.org/camel-quarkus/latest/user-guide/index.html
- Create a new example (contributor guide): https://camel.apache.org/camel-quarkus/latest/contributor-guide/create-new-example.html
- Canonical agent rules: https://github.com/apache/camel-quarkus/blob/main/AGENTS.md
