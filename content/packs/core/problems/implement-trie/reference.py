# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def run_operations(operations):
    root = {}
    answers = []
    for name, argument in operations:
        if name == "insert":
            node = root
            for character in argument:
                node = node.setdefault(character, {})
            node["$"] = True
        else:
            node = root
            found = True
            for character in argument:
                if character not in node:
                    found = False
                    break
                node = node[character]
            if not found:
                answers.append(False)
            elif name == "search":
                answers.append("$" in node)
            else:
                answers.append(True)
    return answers
