## Instructions for AI

To generate a PR title and description for the current branch, execute these commands:

```bash
git log origin/master..HEAD --oneline --format="%h %s"
git diff origin/master..HEAD --stat
```

Then produce output in this exact format:

---

## Description

_One-paragraph summary: what changed and why. Lead with the most important change. Include architecture pattern if relevant. Keep under 6 sentences._

## Changes by Area

### Features
- **Title**: Description with file references

### Bug Fixes
- **Title**: Description with file references

### Refactors
- **Title**: Description with file references

### Dependencies

| Library | Old | New |
|---|---|---|
| Name | x.y.z | a.b.c |

### Configuration
- **Area**: Description with file references

## Breaking Changes

1. **Title**: Impact and mitigation

## Testing

- **Area**: Test files, what they verify, tooling used

## Notes

- Design decisions, limitations, follow-up work, deployment gotchas

---

## Rules

- **Title format**: `<Category>/<kebab-case-description>` — Categories: Feat, Fix, Refactor, Build, Chore, Docs, Test
- **Only include sections that have content** — omit empty sections
- **Dependencies table**: only rows that changed
- **Breaking Changes**: minSdk/bump, deleted public API, removed deps, schema changes
- **Testing**: new test files, modified tests, manual verification, build verification
- **File references**: use backticks and relative paths from repo root
- **No placeholders, no filler** — every line must be substantive
