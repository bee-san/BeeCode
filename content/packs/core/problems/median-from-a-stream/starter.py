def run_operations(operations):
    """Report the running median of a stream of values.

    Args:
        operations: list of [name, argument] pairs. An add is ["add", value]; a
            median query is ["median", None].

    Returns:
        A list with one entry per median query, in order. With an even count the
        median is the mean of the two middle values.
    """
    pass
