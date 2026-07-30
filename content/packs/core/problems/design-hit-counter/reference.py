# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def run_operations(operations):
    hits = []
    head = 0
    results = []
    for name, timestamp in operations:
        while head < len(hits) and hits[head] <= timestamp - 300:
            head += 1
        if name == "hit":
            hits.append(timestamp)
        else:
            results.append(len(hits) - head)
    return results
