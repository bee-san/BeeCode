## The insight

"Everything except position `i`" is not one thing to compute — it is **two things
multiplied**:

```
answer[i] = (product of nums[0 .. i-1]) * (product of nums[i+1 .. n-1])
           = prefix[i] * suffix[i]
```

Both halves are running products, and a running product is cheap: `prefix[i + 1]` is just
`prefix[i] * nums[i]`. So one sweep left-to-right computes every prefix, one sweep
right-to-left computes every suffix, and the answer is their elementwise product. Split at
`i`, and the O(n²) recomputation collapses into two linear passes.

This is the prefix-sum idea with multiplication in place of addition. Whenever a
per-position answer depends on "all the elements on one side of me", reach for a running
accumulator instead of a nested loop.

## Two sweeps, one array

You do not need two extra arrays. Fill the output with the prefixes on the way up, then
multiply the suffixes in on the way down, carrying the suffix in a single scalar.

```python
def product_except_self(nums):
    answer = [1] * len(nums)

    prefix = 1
    for index in range(len(nums)):
        answer[index] = prefix
        prefix *= nums[index]

    suffix = 1
    for index in range(len(nums) - 1, -1, -1):
        answer[index] *= suffix
        suffix *= nums[index]

    return answer
```

The ordering inside each loop is the crux. In the first loop you **write before you
multiply**: `answer[index]` must receive the product of everything strictly *before*
`index`, so `nums[index]` is folded into `prefix` only after the store. Swap those two
lines and every entry is off by a factor of `nums[index]` — which, when that element is
zero, silently zeroes the one position that should have been non-zero. The second loop
follows the same rule mirrored.

Both accumulators start at `1`, the identity for multiplication, which is also why
`[5]` correctly returns `[1]`: position 0 has nothing on either side, so the answer is the
empty product.

Three mistakes to avoid:

**Reaching for division.** `total // nums[i]` is the first idea everyone has, and it is
banned for a reason beyond pedantry: it breaks on zeros. With one zero you must
special-case the zero's position; with **two** zeros the whole answer is zero, and the
patch for the one-zero case gets it wrong. `[0, 4, 0]` is in the test suite exactly for
this. The prefix/suffix method never divides, so zeros need no special handling at all —
they simply propagate as factors.

**Getting the sweep boundaries wrong.** `range(len(nums) - 1, -1, -1)` must reach index 0;
stopping at `0` exclusive leaves `answer[0]` without its suffix factor. Since `answer[0]`
is `1` after the first sweep, the bug shows up as a wrong first element only.

**Building `suffix` as a full array.** Correct, and a fine first version — but the suffix
is consumed immediately at each index and never revisited, so a scalar suffices. That is
what gets you to O(1) extra space.

## Cost

O(n) time — two passes, one multiply and one store each.

O(1) extra space beyond the output list, which the problem requires you to allocate
anyway. The scalar `prefix` and `suffix` are the only working memory.
