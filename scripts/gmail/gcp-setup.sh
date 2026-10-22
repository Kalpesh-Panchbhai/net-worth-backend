#!/usr/bin/env bash
# One-time GCP side of the Gmail bank-balance sync: Pub/Sub topic Gmail publishes to, and an
# authenticated push subscription that calls the AWS webhook.
#
# Usage: scripts/gmail/gcp-setup.sh <GCP_PROJECT_ID>
# Prereqs: gcloud installed and `gcloud auth login` done. Safe to re-run.
#
# Prints the env values to feed into scripts/gmail/aws-setup.sh.
set -euo pipefail

PROJECT="${1:?usage: $0 <GCP_PROJECT_ID>}"
TOPIC="gmail-bank-sync"
SUB="gmail-bank-sync-push"
SA_NAME="gmail-push"
SA_EMAIL="${SA_NAME}@${PROJECT}.iam.gserviceaccount.com"
WEBHOOK="https://kfgx37r84g.execute-api.ap-south-1.amazonaws.com/prod/gmail-webhook"

gcloud config set project "$PROJECT" >/dev/null
gcloud services enable gmail.googleapis.com pubsub.googleapis.com

gcloud pubsub topics describe "$TOPIC" >/dev/null 2>&1 || gcloud pubsub topics create "$TOPIC"

# Gmail's own service account must be allowed to publish mailbox changes to the topic.
gcloud pubsub topics add-iam-policy-binding "$TOPIC" \
  --member="serviceAccount:gmail-api-push@system.gserviceaccount.com" \
  --role="roles/pubsub.publisher" >/dev/null

# Identity Pub/Sub signs the push OIDC token as; the webhook only accepts this email.
gcloud iam service-accounts describe "$SA_EMAIL" >/dev/null 2>&1 \
  || gcloud iam service-accounts create "$SA_NAME" --display-name="Gmail push to AWS"

PROJECT_NUMBER="$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')"
gcloud iam service-accounts add-iam-policy-binding "$SA_EMAIL" \
  --member="serviceAccount:service-${PROJECT_NUMBER}@gcp-sa-pubsub.iam.gserviceaccount.com" \
  --role="roles/iam.serviceAccountTokenCreator" >/dev/null

if gcloud pubsub subscriptions describe "$SUB" >/dev/null 2>&1; then
  gcloud pubsub subscriptions update "$SUB" --push-endpoint="$WEBHOOK" \
    --push-auth-service-account="$SA_EMAIL" --push-auth-token-audience="$WEBHOOK" --ack-deadline=60
else
  gcloud pubsub subscriptions create "$SUB" --topic="$TOPIC" --push-endpoint="$WEBHOOK" \
    --push-auth-service-account="$SA_EMAIL" --push-auth-token-audience="$WEBHOOK" \
    --ack-deadline=60 --message-retention-duration=1d \
    --min-retry-delay=10s --max-retry-delay=600s
fi

cat <<EOF

GCP setup done. Values for aws-setup.sh:
  GMAIL_PUBSUB_TOPIC=projects/${PROJECT}/topics/${TOPIC}
  GMAIL_PUSH_AUDIENCE=${WEBHOOK}
  GMAIL_PUSH_SA_EMAIL=${SA_EMAIL}

Still manual (Cloud Console, project ${PROJECT}):
  1. APIs & Services > OAuth consent screen: External, add yourself, PUBLISH to Production.
  2. Credentials > Create OAuth client ID > Desktop app. Note client id + secret.
  3. python3 scripts/gmail/get-refresh-token.py <CLIENT_ID> <CLIENT_SECRET>
EOF
