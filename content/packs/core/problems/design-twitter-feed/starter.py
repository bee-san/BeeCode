def run_operations(operations):
    """Replay post, feed, follow and unfollow operations against a message feed.

    Args:
        operations: list of operations. post is ["post", user, message]; feed is
            ["feed", user]; follow and unfollow are [name, follower, followed].

    Returns:
        A list with one entry per feed: the ten most recent message ids visible to
        that user, newest first.
    """
    pass
