Given a list of daily `temperatures`, return a list where entry `i` says **how many
days you must wait** after day `i` for a warmer temperature. If no later day is
warmer, the answer for that day is `0`.

## Constraints

- `1 <= len(temperatures) <= 100_000`
- `-10_000 <= temperatures[i] <= 10_000`
- "Warmer" is strictly greater.

## Follow-up

Scanning forward from every day is O(n²), and at 100,000 days that is 10 billion
comparisons. The efficient solution touches each day at most twice. The trick is to
keep a stack of days *still waiting for an answer* — and to notice what must be true
about the temperatures on that stack.
