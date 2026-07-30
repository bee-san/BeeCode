`tickets` is a list of `[from, to]` airport-code pairs. Reconstruct the trip that uses
**every ticket exactly once**, starting at `"JFK"`.

If several such trips exist, return the one that is smallest in dictionary order when read
as a list of airport codes. A valid trip always exists.

Return the airports in visiting order, including the start and every repeat.

## Constraints

- `1 <= len(tickets) <= 300`
- Every airport code is three uppercase letters.
- Tickets may repeat.

## Follow-up

Using every edge exactly once is an **Eulerian path**. Plain backtracking — try the
smallest unused ticket, undo if you get stuck — is correct but can be exponential.
Hierholzer's algorithm gets it in linear time by never undoing: fly onwards greedily until
stuck, and then note that the airport you are stranded at must be the *end* of the trip.
Build the answer backwards from there.
