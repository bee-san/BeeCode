def run_operations(operations):
    """Replay set and get operations against a time-versioned store.

    Args:
        operations: list of operations. A set is ["set", key, value, timestamp];
            a get is ["get", key, timestamp].

    Returns:
        A list with one entry per get, in order. A get with no value at or before
        its timestamp yields "".
    """
    pass
