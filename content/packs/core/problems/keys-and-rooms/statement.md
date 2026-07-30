There are `len(rooms)` rooms, numbered from `0`. All are locked except room `0`. Room `i` contains the
keys listed in `rooms[i]`, each opening the room of that number.

Starting in room `0`, return whether you can eventually enter every room.

## Constraints

- `1 <= len(rooms) <= 1000`
- Keys are valid room numbers, may repeat, and a room may hold a key to itself.

## Follow-up

This is reachability from a single source in a directed graph, with the adjacency list handed to you
already. The only question is whether the traversal reaches everything — and repeated keys make a
visited set mandatory rather than merely helpful.
