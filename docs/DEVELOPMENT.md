# Regular development process

## Cadence and scope

Codex continues this new project once every 24 hours when the local scheduler can run. No particular clock time is a user requirement. A session may produce multiple meaningful commits; there is no arbitrary commit quota.

The recurring task targets only Rajansharma0786/relayforge. It replaces the earlier daily routine for langchain4j-enterprise-copilot. The owner authorizes implementation, tests, documentation, commits, and pushes to this repository.

Local scheduled work requires the computer awake, Codex running, credentials available, and permission to access the network. A trigger is an opportunity to work, not a guarantee that a publish will succeed.

## Each session

1. Inspect repository instructions, working-tree state, remote main, this roadmap, and the progress log.
2. Recover any completed but unpublished commits before starting new changes. Preserve user edits and never force-push.
3. Choose the next unfinished feasible item, prioritizing regressions and broken checks.
4. Implement one or more complete improvements. Separate logical changes into useful commits, rather than splitting files arbitrarily.
5. Run behavioral tests for each change. Update the quickstart, contracts, or architecture when behavior changes.
6. Update the progress log with actual scope, executed checks, remaining limits, and next action. Mark only completed work done.
7. Review the diff and publish tested commits. Respect branch protections and use a PR if required.
8. Verify the remote commit and report a short result with links.

Do one regular session per Asia/Kolkata calendar date, while allowing recovery of unpublished work and user-requested extra work. Multiple commits within that session are expected when there are several coherent improvements.

## Verification

```bash
PYTHONPATH=src python3 -m unittest discover -s tests -v
PYTHONPATH=src python3 -m relayforge --help
git diff --check
```

Use temporary database paths in tests and demos. Never commit runtime databases, real webhook payloads, credentials, or generated build output.

## Attribution and limitations

Development is AI-assisted with Codex, including scheduled sessions. Commit messages and progress notes describe the changes actually made. Do not claim tests, benchmarks, deployments, or human authorship that did not occur.

No empty commits, backdating, fabricated results, or date-only changes. If no useful tested change can be completed, report the blocker and preserve work.

Paid services, production deployment, deletion, force-pushing, and unrelated repositories require separate authorization. Once the roadmap is complete, prioritize real maintenance, evaluation, and user-reported gaps.
