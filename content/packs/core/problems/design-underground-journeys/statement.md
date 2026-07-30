Track journey times between pairs of stations.

- `in`, given `[passenger, station, time]`, records a passenger entering a station.
- `out`, given `[passenger, station, time]`, records the same passenger leaving, completing one
  journey.
- `average`, given `[start, end]`, returns the mean time of every completed journey from `start` to
  `end`, as a float.

A passenger is inside at most one station at a time, and every `out` follows an `in` for that
passenger. Journeys are directed: `start` to `end` is not the same as `end` to `start`.

BeeCode passes test arguments as JSON, so the operations arrive as a replay: a list of
`[name, arguments]` pairs, where `arguments` is the list described above. Return a list holding one
result per `average` operation, in order. That is an honest simplification, not a disguise.

`average` is only asked about pairs that have at least one completed journey.

## Constraints

- `1 <= number of operations <= 10000`
- Station names are lowercase strings; passengers are integers.
- `1 <= time <= 10^9`, non-decreasing across the replay.

## Follow-up

Two maps. One holds who is currently travelling; the other accumulates per-route totals. Why store a
running total and a count rather than every individual journey time?
