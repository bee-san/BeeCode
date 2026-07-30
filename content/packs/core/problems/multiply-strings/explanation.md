## The insight

Digit `i` of `first` (counting from the left, so worth `10^(len(first)-1-i)`) times digit `j` of
`second` contributes to the column at index `i + j + 1` of a result array of length
`len(first) + len(second)`. That index is the whole trick: it follows from the place values
adding, and it means every partial product lands in the right column with no shifting.

Accumulate all the products first, letting columns hold values well above `9`, then normalise the
carries in one right-to-left pass, then strip leading zeroes.

## Why the product array has that length

`len(first) + len(second)` digits always suffice: the product is less than
`10^len(first) * 10^len(second)`. It may need one fewer — `2 * 3 = 6` uses one of two — which is
exactly what the leading-zero strip removes.

## Deferring the carries

Each column can accumulate at most `min(len(first), len(second))` products of at most `81`, so
around 16200 for the largest inputs. That fits comfortably, so there is no need to normalise
inside the loop. Deferring keeps the inner loop to one multiply and one add.

## The zero cases

`"0" * anything` is `"0"`, handled up front. Without that, the general path produces all-zero
columns, the strip removes everything, and you are left with an empty string. The `if not digits`
guard covers it too — belt and braces, and cheap.

## Why not convert to integers

`int(first) * int(second)` works in Python and defeats the exercise. In a fixed-width language a
200-digit number does not fit at all, which is precisely why long multiplication is worth being
able to write.

## Pitfalls

**The wrong column index.** `i + j` instead of `i + j + 1` shifts everything by a power of ten.

**Not stripping leading zeroes.** `"06"` instead of `"6"`.

**Stripping every zero.** `"100"` must keep its trailing zeroes; only *leading* ones go.

**An empty result for zero.** Guard it.

**Carrying left to right.** Carries propagate towards the more significant end.

## Cost

O(len(first) * len(second)) time, O(len(first) + len(second)) space.
