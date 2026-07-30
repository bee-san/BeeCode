# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

import collections


def run_operations(capacity, operations):
    entries = collections.OrderedDict()
    answers = []
    for operation in operations:
        if operation[0] == "put":
            _, key, value = operation
            if key in entries:
                entries.move_to_end(key)
            entries[key] = value
            if len(entries) > capacity:
                entries.popitem(last=False)
        else:
            _, key = operation
            if key in entries:
                entries.move_to_end(key)
                answers.append(entries[key])
            else:
                answers.append(-1)
    return answers
