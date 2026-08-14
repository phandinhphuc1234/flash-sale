#!/usr/bin/env bash
set -euo pipefail

# Provision only the approved Feature 019 output topic. Broker lifecycle and topic administration
# are root-owned infrastructure responsibilities, not a flashsale-service startup concern.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${DOCKER_DIR}/compose.yml"
ENV_FILE="${COMPOSE_ENV_FILE:-${DOCKER_DIR}/.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
  ENV_FILE="${DOCKER_DIR}/.env.example"
fi

TOPIC="flashsale.purchase.events.v1"
PARTITIONS="3"
REPLICATION_FACTOR="1"

# Git Bash on Windows exposes paths as /c/... while Docker Desktop expects a native path for
# --env-file and -f. Linux/macOS shells keep their absolute paths unchanged.
compose_path() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$1"
  else
    printf '%s\n' "$1"
  fi
}

if command -v cygpath >/dev/null 2>&1; then
  # Prevent MSYS from converting native paths a second time when invoking Docker Desktop.
  export MSYS_NO_PATHCONV=1
fi

COMPOSE_FILE_ARG="$(compose_path "${COMPOSE_FILE}")"
ENV_FILE_ARG="$(compose_path "${ENV_FILE}")"

# Relative paths avoid an additional Windows path conversion when Git Bash launches Docker Desktop.
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
  echo "Kafka is not reachable. Start the local kafka service before provisioning topics." >&2
  echo "${TOPIC_LIST}" >&2
  exit 1
fi

"${TOPICS_COMMAND[@]}" --create \
  --if-not-exists \
  --topic "${TOPIC}" \
  --partitions "${PARTITIONS}" \
  --replication-factor "${REPLICATION_FACTOR}" >/dev/null

DESCRIPTION="$("${TOPICS_COMMAND[@]}" --describe --topic "${TOPIC}")"
if ! grep -Eq "PartitionCount:[[:space:]]*${PARTITIONS}([[:space:]]|$)" <<<"${DESCRIPTION}"; then
  echo "${TOPIC} has an unexpected partition count; expected ${PARTITIONS}." >&2
  exit 1
fi
if ! grep -Eq "ReplicationFactor:[[:space:]]*${REPLICATION_FACTOR}([[:space:]]|$)" <<<"${DESCRIPTION}"; then
  echo "${TOPIC} has an unexpected replication factor; expected ${REPLICATION_FACTOR}." >&2
  exit 1
fi

popd >/dev/null
echo "Provisioned ${TOPIC} (partitions=${PARTITIONS}, replication-factor=${REPLICATION_FACTOR})."
