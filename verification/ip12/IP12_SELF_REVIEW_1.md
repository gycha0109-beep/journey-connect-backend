# IP-12 Self Review 1 — Architecture and Boundary

- Found: 2
- Fixed: 2
- Held: 0

1. Disabled requests could resolve an account hash before activation checks. The operational gate now accepts a lazy supplier and resolves identity only after enabled, kill-switch, sampling and cohort-presence gates.
2. Timed work could emit timeout and hard-timeout completion callbacks. Completion reporting is now single-shot; hard timeout reinforces interruption only.

Reverification: backend typed-stub compile PASS, IP-12 direct contract 41 PASS, upstream direct regression PASS.
