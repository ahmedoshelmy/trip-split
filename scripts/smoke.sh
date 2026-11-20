#!/usr/bin/env bash
# End-to-end check against a running stack (default: the compose web proxy on :8090).
# Creates a trip, adds three expenses, checks balances net to zero, voids one, checks they change.
set -euo pipefail
BASE="${1:-http://localhost:8090}"
command -v jq >/dev/null || { echo "jq is required"; exit 2; }

post() { curl -fsS -X POST "$BASE$1" -H 'Content-Type: application/json' ${3:+-H "If-Match: $3"} -d "$2"; }
sum() { jq '[.balances[].balanceMinor] | add'; }

trip=$(post /api/trips '{"name":"Smoke test trip","baseCurrency":"EGP","members":["Ana","Ben","Cy"]}')
id=$(jq -r .id <<<"$trip")
a=$(jq -r '.members[0].id' <<<"$trip"); b=$(jq -r '.members[1].id' <<<"$trip"); c=$(jq -r '.members[2].id' <<<"$trip")
echo "trip $id"

equal() { printf '{"description":"%s","paidBy":"%s","amount":"%s","currency":"EGP","split":{"kind":"EQUAL","participants":["%s","%s","%s"]}}' "$1" "$2" "$3" "$a" "$b" "$c"; }
e1=$(post "/api/trips/$id/expenses" "$(equal Dinner "$a" 600.00)" | jq -r .id)
post "/api/trips/$id/expenses" "$(equal Fuel "$b" 100.00)" >/dev/null
post "/api/trips/$id/expenses" "{\"description\":\"Boat\",\"paidBy\":\"$c\",\"amount\":\"20\",\"currency\":\"USD\",\"rate\":\"48.75\",\"split\":{\"kind\":\"SHARES\",\"shares\":{\"$a\":1,\"$c\":3}}}" >/dev/null

before=$(curl -fsS "$BASE/api/trips/$id/balances")
echo "balances: $(jq -c '[.balances[] | {name, balanceMinor}]' <<<"$before")"
[ "$(sum <<<"$before")" = "0" ] || { echo "FAIL balances do not sum to zero"; exit 1; }

status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/trips/$id/members" -H 'Content-Type: application/json' -H 'If-Match: "1"' -d '{"name":"Late"}')
[ "$status" = "412" ] || { echo "FAIL stale If-Match gave $status, expected 412"; exit 1; }

post "/api/trips/$id/expenses/$e1/void" '{"reason":"smoke"}' >/dev/null
after=$(curl -fsS "$BASE/api/trips/$id/balances")
echo "after void: $(jq -c '[.balances[] | {name, balanceMinor}]' <<<"$after")"
[ "$(sum <<<"$after")" = "0" ] || { echo "FAIL balances after void do not sum to zero"; exit 1; }
[ "$(jq -c '.balances' <<<"$before")" != "$(jq -c '.balances' <<<"$after")" ] || { echo "FAIL void did not change balances"; exit 1; }

echo "settle-up: $(curl -fsS "$BASE/api/trips/$id/settle-up" | jq -c '[.transfers[] | "\(.fromName) -> \(.toName) \(.amountMinor)"]')"
for i in $(seq 1 20); do
  n=$(curl -fsS "$BASE/api/trips/$id/timeline" | jq length)
  [ "$n" -ge 8 ] && break
  sleep 0.5
done
echo "timeline entries: $n"
[ "$n" -ge 8 ] || { echo "FAIL timeline did not catch up"; exit 1; }
draft=$(post "/api/trips/$id/quick-entry" '{"text":"Ben paid 40 usd for the boat with Cy"}')
[ "$(jq -r .currency <<<"$draft")" = "USD" ] && [ "$(jq -r .paidBy <<<"$draft")" = "$b" ] || { echo "FAIL quick entry: $draft"; exit 1; }
echo "quick entry: $(jq -c '{description, amount, currency}' <<<"$draft")"
rows=$(curl -fsS "$BASE/api/trips/$id/expenses.csv" | wc -l)
[ "$rows" -eq 4 ] || { echo "FAIL csv export has $rows lines"; exit 1; }
echo "csv export: header + 3 expenses"
echo "SMOKE PASS"
