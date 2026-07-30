## The insight

The state is (day, situation), and that second dimension is what makes this a
two-dimensional problem despite the single input array. Three situations:

- **holding** — you own a share
- **cooling** — you sold today, so tomorrow you may not buy
- **free** — you own nothing and may buy

Each follows from yesterday:

```text
holding = max(holding, free - price)      # keep holding, or buy today
cooling = holding + price                 # sell today; only reachable from holding
free    = max(free, cooling)              # stay free, or yesterday's cooldown has passed
```

The answer is `max(free, cooling)` on the last day — never `holding`, since an unsold share
is not profit.

## Where the cooldown lives

`holding` builds on `free`, not on `cooling`. That single choice *is* the cooldown: the day
after a sale you are in `cooling`, and `cooling` only flows into `free` on the following day,
so there is no way to buy until then. If `holding` were allowed to build on `cooling`, the
rule would silently disappear and `[1, 2, 3, 0, 2]` would return 4.

## Compute all three before assigning any

Every new value must be derived from yesterday's triple. Overwriting `holding` first and then
using it for `free` mixes days, and the bug hides on short inputs — the shape of error that
[Largest Product of a Contiguous Run](maximum-product-subarray) has too.

## The initial values

`holding = -prices[0]`, because you must have bought to be holding on day 0. `free = 0` and
`cooling = 0`: on day 0 nothing has been sold, and `0` is the correct profit for both, since
having sold nothing and being free are the same thing at the start.

## Why greedy fails

"Take every rising pair" is optimal without a cooldown, but here consecutive small gains
compete with a later larger one. `[1, 2, 3]` shows it: the greedy two trades give
`1 + 1 = 2` before the cooldown, and with the cooldown the second trade cannot happen at all,
so the answer is the single trade `3 - 1 = 2`. Add `[1, 2, 3, 0, 2]` and the interaction
becomes genuinely non-local.

## Pitfalls

**Returning `holding`.** Counts an unsold share.

**Letting `holding` build on `cooling`.** Removes the cooldown.

**Assigning in place.** Mixes days.

**A single day.** `0`; the loop never runs and `free` is already `0`.

## Cost

O(n) time, O(1) space.
