# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def run_operations(operations):
    counts = {}
    results = []
    for name, point in operations:
        x = point[0]
        y = point[1]
        if name == "add":
            key = (x, y)
            if key in counts:
                counts[key] += 1
            else:
                counts[key] = 1
        else:
            total = 0
            for (other_x, other_y), quantity in counts.items():
                if other_x == x or other_y == y:
                    continue
                if abs(other_x - x) != abs(other_y - y):
                    continue
                first = counts.get((x, other_y), 0)
                second = counts.get((other_x, y), 0)
                total += quantity * first * second
            results.append(total)
    return results
