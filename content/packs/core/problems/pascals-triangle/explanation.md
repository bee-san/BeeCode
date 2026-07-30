## The insight

Start each row as all ones of the right length, then overwrite the interior from the row above:

```python
result = []
for index in range(rows):
    row = [1] * (index + 1)
    for position in range(1, index):
        row[position] = result[-1][position - 1] + result[-1][position]
    result.append(row)
return result
```

Filling with ones first means the two ends need no special handling, and `range(1, index)` is empty
for the first two rows — so `[1]` and `[1, 1]` come out right with no branch.

## Why the recurrence beats the closed form

`n choose k` is `n! / (k! * (n-k)!)`, and computing it independently for every entry recomputes the
same factorials over and over. Building the triangle needs all `O(rows^2)` entries, and the
recurrence produces each in one addition. The closed form is the right tool for a *single* entry
deep in the triangle, where you do not want the rows above it.

Pascal's rule — `C(n, k) = C(n-1, k-1) + C(n-1, k)` — is the recurrence, and it says a committee of
`k` from `n` people either includes a particular person or does not. Which is a nicer proof than
manipulating factorials.

## Reading `result[-1]`

The previous row is always the last one appended. `result[index - 1]` is the same thing; for
`index == 0` neither is reached, because the interior loop does not run.

## Pitfalls

**Not filling the ends with ones.** The interior loop leaves them at zero.

**An interior loop over `range(1, index + 1)`.** Overwrites the trailing `1` by reading past the
previous row's end.

**Building each row from the closed form.** Correct and needlessly expensive for the whole
triangle.

**`rows = 1`.** One row, `[[1]]`, and the interior loop never runs.

## Cost

O(rows^2) time and space, which is the size of the output.
