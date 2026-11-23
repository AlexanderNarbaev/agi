# MiniMax subscription route recovery

The same MINIMAX_API_KEY environment binding was used for the official quota
query and isolated Harness preflight. The installed adapter specifies that key
through apiKeyEnv and pins minimax / MiniMax-M3.1-Flash. The isolated profile
disables DeepSeek fallback and both telemetry upload paths.

Quota endpoint: HTTP 200, status code 0, current interval 96% and weekly 59%
remaining. This verifies that the bound credential can access subscription
quota. The model was selected in native DSH 0.2.2-rc.5 session metadata.
Generation endpoint: HTTP 402, insufficient_balance_error, message
"insufficient balance (1008)", provider request ID
0752519c0bf962f119dc9c3ad858b428. The sole fresh synthetic probe produced
zero tokens, took 2.34 s wall and recorded peak RSS of 363748 KiB. No fallback,
repository source or milestone prompt was transmitted.

The owner should ask MiniMax to reconcile the subscription entitlement for
that request ID against the same key's successful quota response. Do not
send them the key or add pay-as-you-go balance based on this result.
