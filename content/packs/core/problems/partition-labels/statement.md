Split `text` into as many parts as possible so that **every letter appears in at most one
part**. The parts must join back into `text` in order.

Return the sizes of the parts, in order.

## Constraints

- `1 <= len(text) <= 500`
- `text` is lowercase `a`-`z`.

## Follow-up

A part cannot end before the last occurrence of any letter it contains. That turns the whole
Problem into a scan where you carry one index. What do you need to know before the scan starts?
