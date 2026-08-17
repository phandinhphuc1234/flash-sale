#!/usr/bin/env bash
set -euo pipefail

# Payment topic administration is root-owned infrastructure. The service never creates topics
# during startup, which keeps deployment order and schema registration explicit and repeatable.
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
  "flashsale.payment.commands.v1"
  "flashsale.payment.events.v1"
  "flashsale.payment.payment-requested.dlt.v1"
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
  echo "Kafka is not reachable. Start the local kafka service before provisioning Payment topics." >&2
  echo "${TOPIC_LIST}" >&2
  exit 1
fi

for topic in "${TOPICS[@]}"; do
  "${TOPICS_COMMAND[@]}" --create --if-not-exists --topic "${topic}" \
    --partitions "${PARTITIONS}" --replication-factor "${REPLICATION_FACTOR}" >/dev/null
  description="$("${TOPICS_COMMAND[@]}" --describe --topic "${topic}")"
  if ! grep -Eq "PartitionCount:[[:space:]]*${PARTITIONS}([[:space:]]|$)" <<<"${description}"; then
    echo "${topic} has an unexpected partition count; expected ${PARTITIONS}." >&2
    exit 1
  fi
  if ! grep -Eq "ReplicationFactor:[[:space:]]*${REPLICATION_FACTOR}([[:space:]]|$)" <<<"${description}"; then
    echo "${topic} has an unexpected replication factor; expected ${REPLICATION_FACTOR}." >&2
    exit 1
  fi
done
popd >/dev/null

printf 'Provisioned Payment topics: %s (partitions=%s, replication-factor=%s).\n' \
  "${TOPICS[*]}" "${PARTITIONS}" "${REPLICATION_FACTOR}"
