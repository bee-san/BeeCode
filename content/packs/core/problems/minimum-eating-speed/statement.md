There are `piles` of bananas and `hours` hours available. Each hour you choose one pile
and eat up to `speed` bananas from it; if the pile has fewer than `speed` left, you
finish it and still spend the whole hour on that pile — you do not move on to another
pile within the same hour.

Return the smallest integer `speed` that lets you eat every pile within `hours` hours.

## Constraints

- `1 <= len(piles) <= 10_000`
- `1 <= piles[i] <= 10^9`
- `len(piles) <= hours <= 10^9`

## Follow-up

You cannot binary search the input here — it is unsorted and the answer is not in it.
But the answer itself lives in a range you know, and feasibility is *monotonic*: if
some speed finishes in time, every larger speed does too. That is the only property
binary search actually needs. What are the bounds, and how do you test one speed?
