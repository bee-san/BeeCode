Given a string `s` made up only of the characters `(`, `)`, `{`, `}`, `[` and `]`,
return `True` if the brackets are balanced and `False` otherwise.

The string is balanced when both of these hold:

- every opening bracket is closed by a closing bracket of the **same type**, and
- brackets close in the reverse of the order they were opened, so `([)]` is not
  balanced even though each type appears twice.

The empty string is balanced.

## Constraints

- `0 <= len(s) <= 10_000`
- `s` contains only the six bracket characters listed above.

## Follow-up

When you meet a closing bracket, only one of the still-open brackets can possibly
match it. Which one — and which data structure hands it to you in O(1)?
