Each node of a linked list holds a value, a `next` pointer, and a **random** pointer
that may aim at any node in the list or at nothing at all.

Build a deep copy: a brand new chain of the same length, in which every node's
`random` aims at the **corresponding new node**, never at a node of the original.

## How the list is given to you, and how the copy is read back

`nodes` is a list of `[value, random_index]` pairs in chain order, so `next` is
simply the following entry. `random_index` is an index into `nodes`, or `null`.

Return the copy as a list of `[value, random_value]` pairs, again in chain order,
where `random_value` is the value held by the node that copy's random pointer aims
at, or `null` if it aims at nothing.

The readout is by value because that is what JSON can carry. It cannot see whether
your random pointers cross back into the original list — only you can enforce that,
and it is the entire point of the exercise. Build the copy properly and the readout
follows; take the shortcut of reading values straight out of `nodes` and you will
pass these tests while having practised nothing.

## Constraints

- `0 <= len(nodes) <= 1000`
- `-10**4 <= value <= 10**4`; values are not necessarily distinct
- `random_index` is either `null` or a valid index into `nodes`, possibly a node's
  own index.
- Do not modify `nodes`.

## Follow-up

The natural solution keeps a dictionary from each original node to its copy. That is
O(n) extra space. There is a way to do it in O(1) extra space by temporarily
**weaving the copies into the original chain** — every copy placed directly after
the node it copies — so that each original node's copy is reachable from it without
a map. Work out the three passes that takes.
