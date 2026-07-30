# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

import heapq


def alphabet_order(words):
    later = {}
    outstanding = {}
    for word in words:
        for letter in word:
            if letter not in later:
                later[letter] = set()
                outstanding[letter] = 0

    for index in range(len(words) - 1):
        first = words[index]
        second = words[index + 1]
        shortest = len(first)
        if len(second) < shortest:
            shortest = len(second)
        differed = False
        for position in range(shortest):
            if first[position] != second[position]:
                if second[position] not in later[first[position]]:
                    later[first[position]].add(second[position])
                    outstanding[second[position]] += 1
                differed = True
                break
        if not differed and len(first) > len(second):
            return ""

    ready = []
    for letter in outstanding:
        if outstanding[letter] == 0:
            heapq.heappush(ready, letter)

    order = []
    while ready:
        letter = heapq.heappop(ready)
        order.append(letter)
        for following in later[letter]:
            outstanding[following] -= 1
            if outstanding[following] == 0:
                heapq.heappush(ready, following)

    if len(order) != len(outstanding):
        return ""
    return "".join(order)
