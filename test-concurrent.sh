#!/bin/bash

# Script to simulate concurrent requests to test Redlock functionality
# Usage: ./test-concurrent.sh <ticket-id> <num-requests>

TICKET_ID=${1:-1}  # Default to ticket ID 1
NUM_REQUESTS=${2:-10}  # Default to 10 concurrent requests
URL="http://localhost/tickets/reserve/${TICKET_ID}"

echo "Sending ${NUM_REQUESTS} concurrent requests to reserve ticket ${TICKET_ID}"

# Function to send a single request
send_request() {
  local id=$1
  local response=$(curl -s -X POST "${URL}")
  echo "[Request $id] Response: $response"
}

# Send requests in parallel
for i in $(seq 1 $NUM_REQUESTS); do
  send_request $i &
done

# Wait for all background processes to complete
wait

echo "All requests completed"