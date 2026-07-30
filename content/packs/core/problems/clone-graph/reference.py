# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def clone_graph(adjacency):
    if not adjacency:
        return []

    copies = {}

    def copy_of(label):
        if label in copies:
            return copies[label]
        made = {"label": label, "neighbours": []}
        copies[label] = made
        for neighbour in adjacency[label]:
            made["neighbours"].append(copy_of(neighbour))
        return made

    for label in range(len(adjacency)):
        if label not in copies:
            copy_of(label)

    result = []
    for label in range(len(adjacency)):
        joined = sorted(node["label"] for node in copies[label]["neighbours"])
        result.append(joined)
    return result
