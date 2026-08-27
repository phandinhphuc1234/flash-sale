#!/usr/bin/env bash
set -euo pipefail

# Provision only Feature 044's approved reservation-command topic and consumer DLTs. The script is
# rerunnable: it creates missing topics, expands a topic below the approved partition target, and
# never deletes a topic or attempts an unsupported partition shrink.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${DOCKER_DIR}/compose.yml"
ENV_FILE="${COMPOSE_ENV_FILE:-${DOCKER_DIR}/.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
  ENV_FILE="${DOCKER_DIR}/.env.example"
fi

PARTITIONS="3"
REPLICATION_FACTOR="1"
TOPICS=(
  "flashsale.purchase.commands.v1"
  "flashsale.order.payment-result.dlt.v1"
  "flashsale.flash-sale.purchase-command.dlt.v1"
  "flashsale.order.purchase-reservation-result.dlt.v1"
)

compose_path() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$1"
  else
    printf '%s\n' "$1"
  fi
}

if command -v cygpath >/dev/null 2>&1; then
  export MSYS_NO_PATHCONV=1
fi

COMPOSE_FILE_ARG="$(compose_path "${COMPOSE_FILE}")"
ENV_FILE_ARG="$(compose_path "${ENV_FILE}")"
if [[ "$(cd -- "$(dirname -- "${ENV_FILE}")" && pwd)" == "${DOCKER_DIR}" ]]; then
  COMPOSE_FILE_ARG="compose.yml"
  ENV_FILE_ARG="$(basename -- "${ENV_FILE}")"
fi

pushd "${DOCKER_DIR}" >/dev/null
if [[ -x /opt/kafka/bin/kafka-topics.sh ]]; then
  TOPICS_COMMAND=(/opt/kafka/bin/kafka-topics.sh --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}")
else
  TOPICS_COMMAND=(docker compose --env-file "${ENV_FILE_ARG}" -f "${COMPOSE_FILE_ARG}" exec -T kafka \
    /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092)
fi

if ! TOPIC_LIST="$("${TOPICS_COMMAND[@]}" --list 2>&1)"; then
  echo "Kafka is not reachable. Start the local kafka service before provisioning Purchase Saga topics." >&2
  echo "${TOPIC_LIST}" >&2
  exit 1
fi

for topic in "${TOPICS[@]}"; do
  if ! grep -Fxq "${topic}" <<<"${TOPIC_LIST}"; then
    "${TOPICS_COMMAND[@]}" --create --topic "${topic}" \
      --partitions "${PARTITIONS}" --replication-factor "${REPLICATION_FACTOR}" >/dev/null
  fi

  description="$("${TOPICS_COMMAND[@]}" --describe --topic "${topic}")"
  if [[ ! "${description}" =~ PartitionCount:[[:space:]]*([0-9]+) ]]; then
    echo "Could not determine the partition count for ${topic}." >&2
    exit 1
  fi
  current_partitions="${BASH_REMATCH[1]}"
  if (( current_partitions < PARTITIONS )); then
    "${TOPICS_COMMAND[@]}" --alter --topic "${topic}" --partitions "${PARTITIONS}" >/dev/null
    description="$("${TOPICS_COMMAND[@]}" --describe --topic "${topic}")"
    current_partitions="${PARTITIONS}"
  fi

  if [[ ! "${description}" =~ ReplicationFactor:[[:space:]]*([0-9]+) ]]; then
    echo "Could not determine the replication factor for ${topic}." >&2
    exit 1
  fi
  current_replication_factor="${BASH_REMATCH[1]}"
  if (( current_replication_factor != REPLICATION_FACTOR )); then
    echo "${topic} has replication factor ${current_replication_factor}; expected ${REPLICATION_FACTOR}." >&2
    exit 1
  fi

  printf 'Verified %s (partitions=%s, replication-factor=%s).\n' \
    "${topic}" "${current_partitions}" "${current_replication_factor}"
done
popd >/dev/null

printf 'Feature 044 topic provisioning complete: %s\n' "${TOPICS[*]}"
