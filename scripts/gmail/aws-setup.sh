#!/usr/bin/env bash
# AWS side of the Gmail bank-balance sync. Safe to re-run.
#   1. POST /gmail-webhook on the REST API -> NetWorthAPIFunction (proxy), deployed to prod
#   2. Gmail env vars merged into both Lambdas (existing vars are kept)
#   3. Daily EventBridge rule that renews the Gmail watch via NetWorthSchedulerFunction
#
# Usage (values from gcp-setup.sh and get-refresh-token.py):
#   GMAIL_CLIENT_ID=... GMAIL_CLIENT_SECRET=... GMAIL_REFRESH_TOKEN=... \
#   GMAIL_PUBSUB_TOPIC=... GMAIL_PUSH_AUDIENCE=... GMAIL_PUSH_SA_EMAIL=... \
#   [GMAIL_SLACK_WEBHOOK_URL=...] [SBI_STATEMENT_PASSWORD=...] [ICICI_STATEMENT_PASSWORD=...] [NPS_STATEMENT_PASSWORD=...] scripts/gmail/aws-setup.sh
# Prereqs: aws CLI with credentials for account 287750673296, jq.
set -euo pipefail

: "${GMAIL_CLIENT_ID:?}" "${GMAIL_CLIENT_SECRET:?}" "${GMAIL_REFRESH_TOKEN:?}"
: "${GMAIL_PUBSUB_TOPIC:?}" "${GMAIL_PUSH_AUDIENCE:?}" "${GMAIL_PUSH_SA_EMAIL:?}"

REGION="ap-south-1"
ACCOUNT="287750673296"
API_ID="kfgx37r84g"
STAGE="prod"
API_FN="NetWorthAPIFunction"
SCHED_FN="NetWorthSchedulerFunction"
RULE="GmailWatchRenew"
export AWS_REGION="$REGION"

# --- 1. API route ---------------------------------------------------------------------------
ROOT_ID="$(aws apigateway get-resources --rest-api-id "$API_ID" --query "items[?path=='/'].id" --output text)"
RES_ID="$(aws apigateway get-resources --rest-api-id "$API_ID" --query "items[?path=='/gmail-webhook'].id" --output text)"
if [ -z "$RES_ID" ] || [ "$RES_ID" = "None" ]; then
  RES_ID="$(aws apigateway create-resource --rest-api-id "$API_ID" --parent-id "$ROOT_ID" \
    --path-part gmail-webhook --query id --output text)"
fi
# Auth is the Pub/Sub OIDC token, verified in the Lambda (API Gateway has no Google authorizer).
aws apigateway put-method --rest-api-id "$API_ID" --resource-id "$RES_ID" --http-method POST \
  --authorization-type NONE >/dev/null 2>&1 || true
aws apigateway put-integration --rest-api-id "$API_ID" --resource-id "$RES_ID" --http-method POST \
  --type AWS_PROXY --integration-http-method POST \
  --uri "arn:aws:apigateway:${REGION}:lambda:path/2015-03-31/functions/arn:aws:lambda:${REGION}:${ACCOUNT}:function:${API_FN}/invocations" >/dev/null
aws lambda add-permission --function-name "$API_FN" --statement-id apigw-gmail-webhook-post \
  --action lambda:InvokeFunction --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:${REGION}:${ACCOUNT}:${API_ID}/*/POST/gmail-webhook" >/dev/null 2>&1 || true
aws apigateway create-deployment --rest-api-id "$API_ID" --stage-name "$STAGE" \
  --description "Add POST /gmail-webhook" >/dev/null
echo "Route live: https://${API_ID}.execute-api.${REGION}.amazonaws.com/${STAGE}/gmail-webhook"

# --- 2. Env vars (merged, never replaced) ---------------------------------------------------
NEW_VARS="$(jq -n \
  --arg a "$GMAIL_CLIENT_ID" --arg b "$GMAIL_CLIENT_SECRET" --arg c "$GMAIL_REFRESH_TOKEN" \
  --arg d "$GMAIL_PUBSUB_TOPIC" --arg e "$GMAIL_PUSH_AUDIENCE" --arg f "$GMAIL_PUSH_SA_EMAIL" \
  --arg g "${GMAIL_SLACK_WEBHOOK_URL:-}" --arg h "${SBI_STATEMENT_PASSWORD:-}" \
  --arg i "${ICICI_STATEMENT_PASSWORD:-}" --arg j "${NPS_STATEMENT_PASSWORD:-}" \
  '{GMAIL_CLIENT_ID:$a, GMAIL_CLIENT_SECRET:$b, GMAIL_REFRESH_TOKEN:$c, GMAIL_PUBSUB_TOPIC:$d,
    GMAIL_PUSH_AUDIENCE:$e, GMAIL_PUSH_SA_EMAIL:$f} + (if $g == "" then {} else {GMAIL_SLACK_WEBHOOK_URL:$g} end)
    + (if $h == "" then {} else {SBI_STATEMENT_PASSWORD:$h} end)
    + (if $i == "" then {} else {ICICI_STATEMENT_PASSWORD:$i} end)
    + (if $j == "" then {} else {NPS_STATEMENT_PASSWORD:$j} end)')"
for FN in "$API_FN" "$SCHED_FN"; do
  CURRENT="$(aws lambda get-function-configuration --function-name "$FN" --query 'Environment.Variables' --output json)"
  [ "$CURRENT" = "null" ] && CURRENT="{}"
  MERGED="$(jq -n --argjson cur "$CURRENT" --argjson new "$NEW_VARS" '{Variables: ($cur + $new)}')"
  aws lambda update-function-configuration --function-name "$FN" --environment "$MERGED" >/dev/null
  aws lambda wait function-updated --function-name "$FN"
  echo "Env updated: $FN"
done

# --- 3. Daily watch renewal (Gmail expires a watch after 7 days) ---------------------------
RULE_ARN="$(aws events put-rule --name "$RULE" --schedule-expression "rate(1 day)" \
  --description "Renew Gmail push watch for bank-balance sync" --query RuleArn --output text)"
aws events put-targets --rule "$RULE" --targets "$(jq -n \
  --arg arn "arn:aws:lambda:${REGION}:${ACCOUNT}:function:${SCHED_FN}" \
  '[{Id: "1", Arn: $arn, Input: "{\"job\":\"gmail-watch\"}"}]')" >/dev/null
aws lambda add-permission --function-name "$SCHED_FN" --statement-id events-gmail-watch \
  --action lambda:InvokeFunction --principal events.amazonaws.com --source-arn "$RULE_ARN" >/dev/null 2>&1 || true

# Start the watch now instead of waiting a day.
aws lambda invoke --function-name "$SCHED_FN" --cli-binary-format raw-in-base64-out \
  --payload '{"job":"gmail-watch"}' /dev/stdout
echo
echo "AWS setup done."
