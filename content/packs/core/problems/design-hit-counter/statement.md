Design a counter that records hits and reports how many happened in the previous 300 seconds.

- `hit`, given a timestamp in seconds, records one hit at that time.
- `count`, given a timestamp, returns how many hits fall in the window `(timestamp - 300,
  timestamp]` — that is, within the last 300 seconds including the current second.

Timestamps arrive in non-decreasing order, and several hits may share one.

BeeCode passes test arguments as JSON, so the operations arrive as a replay: a list of
`[name, timestamp]` pairs. Return a list holding one result per `count` operation, in order. That is
an honest simplification, not a disguise.

## Constraints

- `1 <= number of operations <= 10000`
- `1 <= timestamp <= 10^9`, non-decreasing across the whole replay.

## Follow-up

Keeping every hit makes `count` O(hits in the window) after discarding the stale ones, which is
amortised O(1) per hit. If hits were dense — many per second — a 300-slot ring buffer would be
better. What has to be stored per slot for that to work?
