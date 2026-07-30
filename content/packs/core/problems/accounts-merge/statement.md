Each account is a list `[name, email1, email2, ...]` with at least one email. Two
accounts belong to the same person **if and only if they share at least one email
address**.

Merge the accounts of each person and return one entry per person: their name
followed by all of their emails, **sorted**. Return the list of entries sorted as
well, so the answer is unique.

The same name may belong to different people, and merging is transitive: if A
shares an email with B, and B with C, then all three are one person even if A and C
share nothing directly.

## Constraints

- `0 <= len(accounts) <= 1000`
- Each account has a name and between 1 and 10 emails
- Emails are lowercase and contain no duplicates within a single account
- Every account for one person carries the same name

## Follow-up

Names cannot be used to group by — two people may share one. The emails are what
connect accounts, and the connection is transitive. Which structure answers "are
these two in the same group yet?" while groups are still being discovered?
