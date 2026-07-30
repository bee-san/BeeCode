`gas[i]` is the fuel available at station `i`, and `cost[i]` is the fuel needed to drive from
station `i` to station `i + 1` — wrapping from the last station back to the first.

You start with an empty tank and may begin at any station. Return the index of a station from
which you can drive the whole circuit, or `-1` if none works. The tank must never go negative.

If a valid start exists it is unique.

## Constraints

- `1 <= len(gas) == len(cost) <= 10000`
- `0 <= gas[i], cost[i] <= 10000`

## Follow-up

Two separate facts do all the work. First, whether *any* start exists depends only on a total.
Second, if the tank runs dry somewhere, that tells you something about every station you have
passed — not just the one you started from. What?
