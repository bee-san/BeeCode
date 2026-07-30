## The insight

Reverse Polish notation needs no parentheses and no precedence rules, because the
order of the tokens *is* the order of evaluation. That makes the evaluator almost
trivial: push numbers, and on an operator pop two, combine, push the result.

```python
OPERATORS = ("+", "-", "*", "/")

def evaluate(tokens):
    stack = []
    for token in tokens:
        if token not in OPERATORS:
            stack.append(int(token))
            continue
        right = stack.pop()
        left = stack.pop()
        ...
```

## Order and truncation

**The operands come off backwards.** The first pop is the *right*-hand operand,
because it was pushed last. Addition and multiplication forgive you; subtraction
and division do not, and `["3", "4", "-"]` is the shortest test that catches it.

**`//` is not truncation.** Python floors, so `-7 // 2 == -4` while this Problem
wants `-3`. Three ways to get truncation:

```python
int(left / right)                      # float division, loses precision on big values
abs(left) // abs(right) * sign         # exact, explicit
math.trunc(Fraction(left, right))      # exact, heavyweight
```

The middle one is what the reference does. `int(left / right)` is fine for 32-bit
inputs and is what most people reach for; know that it goes wrong once the
operands exceed what a float can hold exactly.

## Pitfalls

**Detecting operators with `isdigit()`.** `"-3"` is a negative literal, and
`"-3".isdigit()` is `False`, so the token is mistaken for the subtraction
operator. Test against the operator set instead.

**Assuming the tokens are single characters.** `"13"` is one token.

## Cost

O(n) time, O(n) space for the stack — its depth is the expression's nesting depth,
which can be linear.
