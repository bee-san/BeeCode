## The insight

Turn the question around. Instead of asking each day "when do I get my answer?", let
each day *give* answers to the days behind it.

Keep a stack of days that are still waiting. When today arrives, it resolves every
waiting day whose temperature it beats — pop each one and record the gap. Then today
joins the stack to wait its turn.

```python
waiting = []
answer = [0] * len(temperatures)
for day, temperature in enumerate(temperatures):
    while waiting and temperatures[waiting[-1]] < temperature:
        earlier = waiting.pop()
        answer[earlier] = day - earlier
    waiting.append(day)
return answer
```

## Why the stack stays sorted

The `while` loop pops everything today beats, so anything left is at least as warm as
today — and then today goes on top. The stack's temperatures are therefore always
**non-increasing from bottom to top**. That is what makes it a *monotonic* stack, and
it is why checking only the top is enough: if today cannot beat the top, it cannot beat
anything beneath it either.

## The details

**Store indices, not temperatures.** The answer is a distance, so you need to know
*which day* was waiting. A stack of temperatures cannot tell you.

**Strictly greater, so `<` in the pop test.** With `<=`, a repeated temperature would
resolve the earlier day, which is wrong: an equal day is not warmer.

**Days left on the stack keep their `0`.** Initialising the answer to all zeros means
the never-resolved days need no special handling at the end.

## Cost

O(n) time. Each day is pushed once and popped at most once, so despite the nested
`while` the total number of pops is bounded by `n`. O(n) space for the stack in the
worst case — a strictly decreasing list, where nothing is ever resolved.
