## The insight

Anchor a run at the current value, then advance while the next value is exactly one greater. When
you stop, the run is `start` to `end`, and the format depends only on whether they are equal.

```python
ranges, index = [], 0
while index < len(values):
    start = values[index]
    while index + 1 < len(values) and values[index + 1] == values[index] + 1:
        index += 1
    end = values[index]
    ranges.append(str(start) if start == end else "%d->%d" % (start, end))
    index += 1
return ranges
```

## Why "exactly one greater" and not "greater"

Any gap ends the run, however small. `[1, 3]` is two runs, not `"1->3"`, because `2` is not
present and the statement forbids a range covering an absent value. Testing `values[index + 1] ==
values[index] + 1` rather than `> values[index]` is that requirement made literal.

## Why the input's strict ascent matters

With duplicates allowed, `values[index + 1] == values[index] + 1` would be false for a repeat,
ending the run and emitting the same value twice. The strict-ascent guarantee is what lets a single
comparison do the whole job — and it is why the statement states it rather than leaving it as an
assumption.

## The empty input

An empty list gives an empty list of ranges. The outer loop simply does not run, which is the
right answer arrived at without a special case — the shape worth preferring when you can get it.

## Pitfalls

**Emitting `"7->7"` for a lone value.** The statement asks for `"7"`.

**Tracking only the start and formatting on the way out.** Doable, and the two-index form makes
the run boundaries obvious.

**Advancing the outer index inside the inner loop and again after it.** The trailing `index += 1`
steps past the run's last element, which the inner loop deliberately stopped on.

**Negative values.** `"-3->-1"` is correct and looks odd. Nothing to do about it, and the tests
say so.

## Cost

O(n) time — each element is visited once — and O(n) space for the output.
