# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def generate(n):
    found = []
    current = []

    def build(opened, closed):
        if len(current) == 2 * n:
            found.append("".join(current))
            return
        if opened < n:
            current.append("(")
            build(opened + 1, closed)
            current.pop()
        if closed < opened:
            current.append(")")
            build(opened, closed + 1)
            current.pop()

    build(0, 0)
    return found
