# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def run_operations(operations):
    values = []
    minima = []
    answers = []
    for name, argument in operations:
        if name == "push":
            values.append(argument)
            if minima and minima[-1] < argument:
                minima.append(minima[-1])
            else:
                minima.append(argument)
        elif name == "pop":
            values.pop()
            minima.pop()
        elif name == "top":
            answers.append(values[-1])
        elif name == "min":
            answers.append(minima[-1])
        else:
            raise ValueError("unknown operation: %s" % name)
    return answers
