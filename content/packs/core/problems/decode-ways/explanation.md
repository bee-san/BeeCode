## The insight

Let `ways[i]` be the number of decodings of the first `i` digits. To extend to digit
`i - 1` you either take it alone or pair it with the digit before:

```text
ways[i] = (ways[i-1] if digits[i-1] is not "0" else 0)
        + (ways[i-2] if 10 <= int(digits[i-2:i]) <= 26 else 0)
```

Both terms are conditional, and that is the entire difference from
[Climbing Stairs](climbing-stairs). Two rolling variables suffice.

## The two conditions

**A single digit** decodes only when it is not `"0"` — there is no letter zero.

**A pair** decodes only when it is between `10` and `26`. The lower bound is the leading-zero
rule: `"06"` is not `6`. Writing `pair <= 26` alone lets `"06"` through and
`count_decodings("06")` returns `1` instead of `0`.

Both bounds are needed. Getting only one is the standard partial solution.

## Why zeroes propagate

A `"0"` that cannot pair with the digit before it makes `ways` zero at that position, and
since later values are built from it, the whole count collapses to `0`. That is correct —
`"100"` is `1` then `00`, undecodable, and `10` then `0`, also undecodable — so no special
casing is needed beyond the two conditions. `"100"` returning `0` while `"101"` returns `1`
is the pair of tests to check that with.

## The base cases

`ways[0] = 1`: the empty prefix has one decoding, the empty string. It is not zero, and
setting it to zero makes everything zero.

`ways[1]` is `0` if the first digit is `"0"`, else `1`.

## Pitfalls

**Only checking `pair <= 26`.** Accepts leading zeroes.

**`int()` on a slice that runs past the end.** Loop from index `1` so the pair is always two
characters.

**Initialising the empty prefix to `0`.** Kills every count.

**Recursion without memoisation.** Exponential; a `"1111111111..."`-style input times out.

## Cost

O(n) time, O(1) space.
