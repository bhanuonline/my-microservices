# Contributing

Thanks for your interest in improving angle-app.

## Ground Rules

- Discuss non-trivial changes in an issue before opening a PR
- One feature/fix per PR — keep them small and reviewable
- Follow existing patterns (interfaces first, config-driven, no hardcoded secrets)
- Add tests for anything that isn't UI/glue

## Branch Naming

- `feature/<short-desc>` — new feature
- `fix/<short-desc>` — bug fix
- `refactor/<short-desc>` — no behavior change
- `docs/<short-desc>` — docs only

## Commit Messages

- First line ≤ 72 chars, imperative mood: "add SMA indicator"
- Body wraps at 72, explains **why**, not **what**
- Reference issues: `Fixes #42`

## Code Style

- Java 17 features welcome (records, pattern matching, switch expressions)
- Lombok allowed — prefer `@RequiredArgsConstructor` for constructor injection
- No wildcard imports
- 4-space indent
- Classes < 300 lines; split by responsibility

## Adding a Strategy

See [STRATEGY-GUIDE.md](STRATEGY-GUIDE.md).

## Adding a Broker

See [BROKER-INTEGRATION.md](BROKER-INTEGRATION.md).

## Tests

- Run `./mvnw test` before pushing
- Add unit tests under `src/test/java/...` matching the package structure
- No calls to real broker APIs from tests

## PR Checklist

- [ ] `./mvnw clean install` passes
- [ ] New public methods have Javadoc
- [ ] `CHANGELOG.md` updated under `[Unreleased]`
- [ ] Docs updated if behavior/config changed
- [ ] No secrets committed (`git diff` scanned)

## Review Cycle

- Small PRs get reviewed in 1–2 days
- Address feedback with new commits (don't force-push during review)
- Squash on merge

## Reporting Bugs

Include:
- Steps to reproduce
- Expected vs actual
- Java version, OS
- Relevant log snippet (redact secrets)
- `git rev-parse HEAD`
