## The insight

Three pieces of state, and one of them is easy to forget:

- **posts** — per user, a list of `(time, message)` in the order posted
- **following** — per user, a set of who they follow
- **a monotonic clock** — incremented on every post

The clock is what makes the merge possible. Posts by different users have to be
ordered against each other, and per-user list positions cannot do that. A global
counter timestamps every post on a single timeline; a wall clock would work too, if it
had enough resolution to never tie.

## The feed

Gather the sources — the user plus everyone they follow — and merge their post lists
by time, taking ten.

The bound that keeps this cheap: **only each source's last ten posts can matter**. A
user with 10,000 posts contributes at most ten candidates, so the merge is over
`10 * len(sources)` items no matter how long the streams are.

```python
recent = []
for source in following[user] | {user}:
    recent.extend(posts[source][-10:])
recent.sort(reverse=True)
return [message for _, message in recent[:10]]
```

Sorting `10 * k` items is fine. A `k`-way heap merge with an early exit after ten pops
is the version that scales — `O(k + 10 log k)` instead of `O(k log k)` — and it is the
same structure as [Merge K Sorted Lists](merge-k-sorted-lists).

## The rules that hide bugs

**A user always sees their own posts.** They are not in their own follow set, so add
themselves at feed time. Forgetting it is the most common failure, and it only shows
for a user who follows nobody.

**Following yourself is a no-op.** Guarded at follow time; adding yourself to the set
would be harmless here but the operation is specified as doing nothing.

**Unfollowing what is not followed.** `discard` rather than `remove`, or a
missing-key guard. Do not let it raise.

**Duplicate follows.** A set makes it idempotent for free, which is why it is a set
and not a list.

## Pitfalls

**Rebuilding the feed from every post ever.** Without the last-ten bound, a user with
a long history is scanned entirely on every query.

**Sorting by message id.** The order is by time. Ids repeat and carry no ordering.

**Returning oldest first.** Newest first.

## Cost

`post`, `follow`, `unfollow` are O(1). `feed` is O(k log k) as written, where `k` is
the number of sources, or O(k + 10 log k) with the heap.
