# Architecture Optimization Checklist

This file tracks the incremental architecture upgrades we’re implementing and validating with `.\scripts\Test-Local.ps1`.

## Status
- [x] 1. Provider abstraction + fallback routing (avoid bricking on quota)
- [x] 2. Consolidate Gemini plumbing + single `HttpClient` bean
- [x] 3. Retry/backoff + concurrency limits
- [x] 4. Token control + chat context compaction
- [ ] 5. LaTeX parsing fixtures + edge case support
- [ ] 6. Observability + `requestId`

## Notes
- The repo is not a git repository in this workspace, so we keep manual checkpoints under `checkpoints/`.
