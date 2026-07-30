## The insight

One digit, one choice, and the choices are independent — so the recursion has a branch
per letter of the current digit's group and no pruning at all:

```python
def build(position):
    if position == len(digits):
        found.append("".join(letters))
        return
    for letter in groups[digits[position]]:
        letters.append(letter)
        build(position + 1)
        letters.pop()
```

This is the backtracking skeleton at its plainest. Nothing is ever rejected, which makes
it a good place to learn the shape before meeting problems where the pruning obscures
it: mark, recurse, undo, and record a **copy** at the base case.

## The iterative form

Grow the answers one digit at a time:

```python
combinations = [""]
for digit in digits:
    combinations = [prefix + letter
                    for prefix in combinations
                    for letter in groups[digit]]
```

Same computation, no recursion, and it makes the Cartesian-product structure obvious.
Note it depends on starting from `[""]` — and that is exactly why the empty-input case
needs handling separately, since the loop would otherwise return `[""]`, a list holding
one empty string.

## The empty input

`[]`, not `[""]`. Zero digits spell nothing at all, and the distinction is the most
commonly failed case in this Problem. The recursive version has the same trap from the
other direction: with no digits, `build(0)` immediately hits its base case and records
one empty string.

## Pitfalls

**Returning `[""]` for the empty input.** See above.

**Getting `7` and `9` wrong.** They carry four letters each, not three. Copy the mapping
carefully — an interviewer will notice, and the suite tests both.

**Building strings by concatenation in the recursion.** Correct, but each level copies
the prefix. A list plus one `join` at the leaf is the cheaper habit.

## Cost

O(3^a * 4^b) where `a` and `b` count the three- and four-letter digits, which is the
size of the output — unavoidable, since every combination must be produced.
