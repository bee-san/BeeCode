## The insight

Two maps doing two different jobs.

**In-flight passengers.** `travelling[passenger] = (station, time)`, set on `in` and removed on
`out`. Keyed by passenger, because that is who the pending record belongs to.

**Route statistics.** `totals[route]` and `counts[route]`, keyed by the ordered pair. On `out`,
compute the elapsed time and fold it in.

`average` is then one division.

## Why a running total rather than a list of times

The mean of `n` values needs only their sum and `n`. Keeping every individual time costs O(journeys)
space and buys nothing that is being asked for — and `average` would become O(journeys) per call
instead of O(1).

It is worth being explicit about what that gives up. Sum and count support the mean, and also the
variance if you additionally keep the sum of squares. They do **not** support the median, any
percentile, or the maximum: those need the values, or a sketch of them. Choosing the aggregate to fit
the queries is the design decision here.

## Why the route key is ordered

`"a>b"` and `"b>a"` are different journeys and must not share statistics. String concatenation with a
separator is one way; a tuple key `(start, end)` is cleaner in Python and the same idea. The
separator matters if station names could contain it — with lowercase names and `>`, they cannot.

## Removing on `out`

`travelling.pop(passenger)` both reads and clears, which matters because the passenger may travel
again immediately. Leaving the entry behind would make a later `out` compute from the wrong start.

## Pitfalls

**Storing every journey time.** Space for nothing, and a slower `average`.

**An unordered route key.** Merges two different routes.

**Not removing the in-flight record.** A second journey by the same passenger measures from the first
entry.

**Integer division.** The average is a float; `5 / 2` must be `2.5`.

## Cost

O(1) per operation, O(passengers travelling + distinct routes) space.
