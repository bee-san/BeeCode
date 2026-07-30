# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def run_operations(operations):
    travelling = {}
    totals = {}
    counts = {}
    results = []

    for name, arguments in operations:
        if name == "in":
            passenger = arguments[0]
            travelling[passenger] = (arguments[1], arguments[2])
        elif name == "out":
            passenger = arguments[0]
            start, started_at = travelling.pop(passenger)
            route = start + ">" + arguments[1]
            elapsed = arguments[2] - started_at
            if route in totals:
                totals[route] += elapsed
                counts[route] += 1
            else:
                totals[route] = elapsed
                counts[route] = 1
        else:
            route = arguments[0] + ">" + arguments[1]
            results.append(totals[route] / float(counts[route]))
    return results
