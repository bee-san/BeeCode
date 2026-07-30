# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def run_operations(operations):
    posts = {}
    following = {}
    clock = [0]
    answers = []

    for operation in operations:
        name = operation[0]
        if name == "post":
            _, user, message = operation
            clock[0] += 1
            posts.setdefault(user, []).append((clock[0], message))
        elif name == "follow":
            _, follower, followed = operation
            if follower != followed:
                following.setdefault(follower, set()).add(followed)
        elif name == "unfollow":
            _, follower, followed = operation
            if follower in following:
                following[follower].discard(followed)
        else:
            _, user = operation
            sources = set(following.get(user, ()))
            sources.add(user)
            recent = []
            for source in sources:
                recent.extend(posts.get(source, [])[-10:])
            recent.sort(reverse=True)
            answers.append([message for _, message in recent[:10]])
    return answers
