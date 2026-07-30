# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def count_prefixes(words, queries):
    root = {"passing": 0, "next": {}}
    for word in words:
        node = root
        node["passing"] += 1
        for character in word:
            child = node["next"].get(character)
            if child is None:
                child = {"passing": 0, "next": {}}
                node["next"][character] = child
            node = child
            node["passing"] += 1

    results = []
    for query in queries:
        node = root
        for character in query:
            node = node["next"].get(character)
            if node is None:
                break
        results.append(0 if node is None else node["passing"])
    return results
