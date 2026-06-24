# Apache Camel Quarkus Examples - AI Agent Guidelines

Guidelines for AI agents contributing examples to **apache/camel-quarkus-examples**.

This repository contains runnable example applications that demonstrate Apache
Camel running on [Quarkus](https://quarkus.io/). Each example is a standalone
Maven module (there is **no aggregator `pom.xml` at the repo root**) and can be
built, tested and run on its own — in JVM or native mode.

These guidelines complement the canonical, org-wide rules in the main
[apache/camel `AGENTS.md`](https://github.com/apache/camel/blob/main/AGENTS.md).
Read that file for the full *Rules of Engagement*; the section below repeats the
essentials and adds what is specific to this examples repository.

## Project Info

- Build: Maven 3.9+ (use the provided `./mvnw` wrapper)
- Java: 17 (`maven.compiler.release` = 17; CI also exercises JDK 21)
- BOMs: `quarkus-bom` + `quarkus-camel-bom` (extension versions are inherited,
  never pinned per-dependency)
- Tests: `@QuarkusTest` (JVM) and `@QuarkusIntegrationTest` (packaged/native)
- JIRA project: `CAMEL` (https://issues.apache.org/jira/projects/CAMEL)

## Rules of Engagement (essentials)

- **Attribution**: every AI-generated PR description, review or JIRA comment MUST
  identify itself as AI-generated and name the human operator, e.g.
  `_Claude Code on behalf of [Human Name]_`.
- **JIRA ownership**: only pick **Unassigned** tickets. Before starting, assign
  the ticket to your operator and transition it to *In Progress*. Set
  `fixVersions` before closing.
- **Target the `camel-quarkus-main` branch** for PRs (it tracks the current
  Camel Quarkus SNAPSHOT), as stated in the repository's PR template. The `main`
  branch tracks the latest released Quarkus Platform.
- **One example per PR**, kept small and self-contained. Do not exceed 10 PRs per
  day per operator. Quality over quantity.
- **Branch from your own fork** (not apache/), with a descriptive name containing
  the topic and JIRA id (e.g. `CAMEL-12345-rest-json-example`). Delete the
  branch after merge/close. Never push to a branch you did not create.
- **Green CI is required**, including license/format/import checks and (where
  applicable) the native build.
- **Never merge** without at least one human approval; never approve your own PR.

## Repository structure

- One example per top-level directory, named semantically and hyphenated
  (e.g. `timer-log/`, `rest-json/`, `kafka/`). Each is an independent Maven
  module — there is no root aggregator, so nothing needs to be registered
  centrally.

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
  it with keys in `application.properties`; set `camel.context.name`.
- **README**: AsciiDoc, with `Start in Development mode`, `Package and run`, and
  (where relevant) native sections, plus a Feedback section.

## Adding a new example (checklist)

1. Create `<example>/` with a `pom.xml` importing `quarkus-bom` and
   `quarkus-camel-bom` and depending on the needed `camel-quarkus-*` extensions
   (no explicit versions).
2. Add routes/beans under `org.acme.<domain>.<feature>`, an
   `application.properties`, a test, and a `README.adoc` — all with ASF headers.
3. Verify locally: `mvn clean test`, `mvn quarkus:dev`, and — if the example is
   expected to — `mvn clean package -Dnative`.
4. Run `mvn license:check formatter:validate impsort:check`.
5. Open the PR from your fork **against `camel-quarkus-main`**, link the JIRA
   ticket, and request review from active committers.

## Links

- Camel Quarkus user guide: https://camel.apache.org/camel-quarkus/latest/user-guide/index.html
- Create a new example (contributor guide): https://camel.apache.org/camel-quarkus/latest/contributor-guide/create-new-example.html
- Canonical agent rules: https://github.com/apache/camel/blob/main/AGENTS.md
