# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def run_operations(operations):
    history = {}
    answers = []
    for operation in operations:
        if operation[0] == "set":
            _, key, value, timestamp = operation
            if key not in history:
                history[key] = ([], [])
            times, values = history[key]
            times.append(timestamp)
            values.append(value)
        else:
            _, key, timestamp = operation
            if key not in history:
                answers.append("")
                continue
            times, values = history[key]
            low = 0
            high = len(times) - 1
            best = -1
            while low <= high:
                middle = (low + high) // 2
                if times[middle] <= timestamp:
                    best = middle
                    low = middle + 1
                else:
                    high = middle - 1
            if best == -1:
                answers.append("")
            else:
                answers.append(values[best])
    return answers
