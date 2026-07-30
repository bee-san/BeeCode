# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def run_operations(operations):
    held = []
    total = 0
    results = []
    for operation in operations:
        name = operation[0]
        if name == "push":
            value = operation[1]
            held.append(value)
            total += value
        elif name == "pop":
            if not held:
                results.append(None)
            else:
                value = held.pop()
                total -= value
                results.append(value)
        elif name == "sum":
            results.append(total)
        else:
            results.append(len(held))
    return results
