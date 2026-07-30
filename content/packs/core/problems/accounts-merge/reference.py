# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

import collections


def merge_accounts(accounts):
    parent = {}

    def find(item):
        parent.setdefault(item, item)
        while parent[item] != item:
            # Path compression: point straight at the grandparent as we climb.
            parent[item] = parent[parent[item]]
            item = parent[item]
        return item

    def union(left, right):
        left_root, right_root = find(left), find(right)
        if left_root != right_root:
            parent[left_root] = right_root

    owner = {}
    for account in accounts:
        name = account[0]
        emails = account[1:]
        for email in emails:
            owner[email] = name
            # Tie every email to the first one, which links the whole account.
            union(email, emails[0])

    groups = collections.defaultdict(list)
    for email in owner:
        groups[find(email)].append(email)

    merged = []
    for emails in groups.values():
        emails.sort()
        merged.append([owner[emails[0]]] + emails)
    merged.sort()
    return merged
