# Diagrams

Visual (Mermaid) versions of key architecture and flow diagrams.

The source docs (in `setup/`, `debug/`, `design/`, etc.) use ASCII
box-drawing for diagrams — they render as plaintext anywhere, work
in any terminal, and diff cleanly in git. **This folder** has the
same diagrams in [Mermaid](https://mermaid.js.org/) format, which
GitHub, GitLab, VSCode, IntelliJ, and the MkDocs Material site all
render as actual pictures.

Pick whichever reads better for you — content is equivalent.

## Index

| Diagram | Source doc (ASCII) | Visual (Mermaid) |
|---|---|---|
| Nano startup order (5-wave dep chain) | [`setup/00-first-time-setup.md`](../setup/00-first-time-setup.md#startup-order-handled-by-depends_on-healthchecks) | [`nano-startup-order.md`](nano-startup-order.md) |
| Request flow (browser → gateway → service → MySQL) | [`setup/00-first-time-setup.md`](../setup/00-first-time-setup.md#5-·-verify-the-service-registered) | [`request-flow.md`](request-flow.md) |
| Effort vs. capability ladder | [`setup/00-first-time-setup.md`](../setup/00-first-time-setup.md#6-·-how-many-services-do-i-actually-need-to-run) | [`services-effort-ladder.md`](services-effort-ladder.md) |
| Docker network mental model | [`setup/docker-howto.md`](../setup/docker-howto.md#1-mental-model--whats-actually-running) | [`docker-mental-model.md`](docker-mental-model.md) |
| Dependency stack (Layer 1 / 2 / 3) | [`setup/01-startup-runbook.md`](../setup/01-startup-runbook.md#the-dependency-stack-for-this-project) | [`dependency-stack.md`](dependency-stack.md) |

## Why have both?

| Format | Pros | Cons |
|---|---|---|
| **ASCII** (in source docs) | Universal — grep-able, terminal-friendly, `git diff` clean, no toolchain | Not "pretty"; wide diagrams wrap on narrow screens |
| **Mermaid** (this folder) | Renders as real diagrams on GitHub/GitLab/MkDocs/VSCode; color-coded; auto-layout | Needs a renderer; harder to edit without preview; shows raw syntax in plain `cat` |

## Contributing

- Add new diagrams as their own `.md` file in this folder
- Update the index above with a row linking to the original ASCII (if any)
- Keep each `.md` focused on ONE diagram with a short caption
