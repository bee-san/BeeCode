Split `text` into pieces so that **every** piece is a palindrome, and return every
possible such splitting.

Each splitting is a list of pieces in order, and concatenating them must give `text`
back. The order of the splittings themselves is not judged.

A single character is a palindrome, so a splitting always exists.

## Constraints

- `1 <= len(text) <= 16`
- `text` is lowercase `a`-`z`.

## Follow-up

At each position, decide where the next piece ends: try every prefix of what remains,
keep the ones that are palindromes, and recurse on the rest. The upper bound on the
number of splittings is `2^(n-1)` — one choice per gap between characters — which is why
the length limit is small.
