# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def run_operations(operations):
    root = {}
    answers = []

    def matches(node, pattern, position):
        if position == len(pattern):
            return "$" in node
        character = pattern[position]
        if character == ".":
            for key, child in node.items():
                if key != "$" and matches(child, pattern, position + 1):
                    return True
            return False
        if character not in node:
            return False
        return matches(node[character], pattern, position + 1)

    for name, argument in operations:
        if name == "add":
            node = root
            for character in argument:
                node = node.setdefault(character, {})
            node["$"] = True
        else:
            answers.append(matches(root, argument, 0))
    return answers
