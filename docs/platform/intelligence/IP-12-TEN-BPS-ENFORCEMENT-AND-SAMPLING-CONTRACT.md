# IP-12 Ten-BPS Enforcement and Sampling Contract

- authoritative production maximum: `10 BPS`
- current configured and effective value: `0 BPS`
- denominator: 10,000 buckets; 10 BPS = 0.10%
- accepted values: 0, 1, 9, 10
- rejected values: -1, 11, 50, 100, integer maximum
- same opaque account hash and policy version produce the same bucket
- anonymous and malformed account values never participate
- enabled false, kill-switch active or empty cohort produce dispatch 0

The broader IP-11.5 provisional capability is not used as production authority. The IP-12 adapter validates exact pilot bounds before constructing the production runtime snapshot.
