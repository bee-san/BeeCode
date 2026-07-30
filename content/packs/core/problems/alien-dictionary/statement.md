`words` is sorted according to an unknown alphabet ordering of the lowercase letters
`a`-`z`. Deduce that ordering.

Return the letters appearing in `words`, ordered by the alien alphabet, as a string.
Return `""` if the input is inconsistent — that is, if no ordering could have produced it.

## Constraints

- `1 <= len(words) <= 100`
- `1 <= len(words[i]) <= 20`
- All letters are lowercase `a`-`z`.

## How your answer is judged

Several orderings are often consistent with the same input, and any of them would be a
correct answer to the question as usually asked. To keep the expected values unambiguous,
this suite uses inputs whose order is **fully forced**, or where the answer is `""`. Where
letters are genuinely unordered relative to each other, ties are broken by the ordinary
alphabet — so among equally valid orderings, return the one that is smallest in ordinary
dictionary order. Say aloud in an interview that the order is not generally unique.

## Follow-up

Each adjacent pair of words yields at most **one** fact: the first position where they
differ tells you which of those two letters comes first, and nothing after that position
tells you anything. Collect those facts as edges and topologically sort. One input shape is
inconsistent without any cycle at all — a word that is a prefix of the word before it.
