Design a message feed supporting four operations:

- `post(user, message)` — that user posts a message.
- `feed(user)` — the ten most recent messages from that user **and everyone they
  follow**, newest first. Fewer than ten if there are not enough.
- `follow(follower, followed)` — start following. Following yourself, or following
  someone already followed, changes nothing.
- `unfollow(follower, followed)` — stop following. Unfollowing someone not followed
  changes nothing.

A user always sees their own messages, whether or not they follow themselves.

Because BeeCode tests functions rather than classes, replay a list of operations. Each
is a list:

- `["post", user, message]`
- `["feed", user]`
- `["follow", follower, followed]`
- `["unfollow", follower, followed]`

Return a list holding the result of each `feed`, in order — each one a list of message
ids, newest first.

## Constraints

- `1 <= len(operations) <= 10_000`
- Users and messages are integers.
- A user may post the same message id more than once; treat each post as distinct in
  time, and report the id.

## Follow-up

The feed merges several time-ordered streams and wants only the newest ten. That is
[Merge K Sorted Lists](merge-k-sorted-lists) with an early exit — and it means you
never have to look at more than ten posts per followed user.
