Make a **deep copy** of a connected undirected graph, then return the copy's adjacency
list.

## How the graph is given to you

`adjacency` is a list of lists: `adjacency[i]` holds the labels of the neighbours of node
`i`, and labels are `0` through `n - 1`. The graph is undirected, so if `j` appears in
`adjacency[i]` then `i` appears in `adjacency[j]`.

Return the adjacency list of your copy, in the same shape: entry `i` lists the
neighbours of the copy of node `i`, **sorted ascending**.

BeeCode passes test inputs as JSON, which cannot carry node objects, so the graph
arrives as an adjacency list rather than as a web of `Node`s. That is an honest
simplification, not a disguise — but it is worth being blunt about what it costs. Read
back as labels, a genuine deep copy and a function that returns `adjacency` unchanged
look identical. The tests cannot tell them apart. Only you can, and building the copy
node by node — never letting a copy's neighbour list point at an original — is the
entire exercise. Write it as though the readout could see the difference, because in an
interview it can.

## Constraints

- `0 <= n <= 100`
- The graph is connected when `n > 0`, has no self-loops, and no repeated edges.

## Follow-up

Recursing into a neighbour that is already being copied would loop forever. A map from
original to copy solves it, and the placement of the insertion matters: register the new
copy **before** recursing into its neighbours, not after.
