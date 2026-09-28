# Working autonomously

`mode` is a peer dimension to status and priority: `afk` means an agent can run the ticket alone, `hitl` means a human
is in the loop (the default for new tickets). Treat it as a contract — pick up a `hitl` ticket only when the user
authorizes that specific ticket.

Handed autonomy, the loop is `knot prime --mode afk` — run it unless a `SessionStart` reminder already put it in the
conversation. It prints the sequence (enumerate → confirm → claim → note → update → close) and is the single source of
truth for that sequence; this skill deliberately keeps no second copy to drift against it.
