# Journey Connect Team Handoff Scope

This branch is the minimized team-delivery baseline derived from the full enterprise archive.

## Included
- Authentication and user features
- Posts, regions, feeds and interactions
- Recommendation runtime required by the team service
- Admin runtime and Admin UI
- Database schema and executable migrations
- Runtime tests and generic CI that remain applicable

## Simplified presentation scope
- Recommendation is presented as one service feature, not as P0/P1/P2 internal phases.
- Admin is presented as user, report and post moderation with authorization and audit history.

## Excluded
- Data Platform and Intelligence Platform contracts
- RCA and Operations Platform governance packages
- System-wide enterprise contracts and phase handoff records
- Phase-specific contract verifiers, evidence packs and one-shot materialization workflows

## Current limitation
Production deployment and runtime-operation ownership have not yet been assigned by the team.
