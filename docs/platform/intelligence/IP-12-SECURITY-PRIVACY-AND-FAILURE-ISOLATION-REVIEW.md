# IP-12 Security, Privacy and Failure Isolation Review

## Data minimization

- projection-only candidates
- private, deleted, unpublished, moderation-blocked and unknown authority excluded by IP-11.5
- raw query/identity/session/JWT/full payload persistence absent
- production evidence sink remains no-op
- account hash is ephemeral and prohibited as a metric tag

## Failure isolation

- disabled state resolves no identity and submits no task
- kill-switch/cohort/sampling failures return bounded reasons only
- executor rejection, timeout, runtime failure, comparison failure, evidence failure, metrics failure and logging failure cannot acquire response authority
- request thread does not wait for completion
- task completion is reported once even if hard cancellation also interrupts the worker

## Open review

Full Spring SecurityContext, Micrometer and MVC integration execution is pending the IP-12 external Gradle attestation.
