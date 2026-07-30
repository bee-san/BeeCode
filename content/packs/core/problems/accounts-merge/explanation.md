## The insight

The names are a distraction — two people can share one, so they cannot be the
grouping key. The **emails** are what link accounts, and the linking is transitive:
share an email with someone who shares an email with a third account, and all three
are one person.

Transitive grouping over items discovered incrementally is exactly what disjoint-set
union (union-find) is for. Treat every email as a node, and every account as an
instruction: *all of these emails belong together*.

You do not need to link every pair inside an account. Tying each email to the
account's **first** email is enough — that alone makes them one component, with `k`
unions instead of `k²`.

## The solution

```python
import collections

def merge_accounts(accounts):
    parent = {}

    def find(item):
        parent.setdefault(item, item)
        while parent[item] != item:
            parent[item] = parent[parent[item]]
            item = parent[item]
        return item

    def union(left, right):
        left_root, right_root = find(left), find(right)
        if left_root != right_root:
            parent[left_root] = right_root

    owner = {}
    for account in accounts:
        name, emails = account[0], account[1:]
        for email in emails:
            owner[email] = name
            union(email, emails[0])

    groups = collections.defaultdict(list)
    for email in owner:
        groups[find(email)].append(email)

    merged = []
    for emails in groups.values():
        emails.sort()
        merged.append([owner[emails[0]]] + emails)
    merged.sort()
    return merged
```

What makes or breaks it:

**Group by root at the end, never as you go.** A component's identity changes as
unions happen, so an account's group is only final once every account has been
read. The test where the *linking* account comes last is the one that catches
grouping too early — the first two accounts look separate at the time they are
processed.

**`owner` maps email to name.** Every account of one person carries the same name,
so any email in the component recovers it. This is also why the name never
participates in the union structure at all.

**Sort twice, for two different reasons.** The emails within a person are sorted
because the problem asks for it; the list of people is sorted so the answer is
unique rather than dependent on dictionary iteration order.

**Path compression in `find`.** Repointing at the grandparent while climbing keeps
the trees nearly flat, which is what makes the near-constant amortised cost real
rather than theoretical.

## Cost

Roughly O(m log m) where `m` is the total number of emails, dominated by the
sorting. The union-find work itself is effectively linear — inverse-Ackermann
amortised per operation, which is under 5 for any input that fits in memory.

A DFS or BFS over a graph of emails works equally well and is a fine answer. Union
find is worth reaching for when merges arrive *incrementally* and you must answer
"same group?" before all the edges are known — which is precisely the shape of this
Problem.
