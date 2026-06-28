#!/bin/bash
# Repair loop runner: runs detect with repair on all examples-extended/
# Usage: ./scripts/run-repair-loop.sh [--ollama-url URL] [--ollama-model MODEL]
# Requires: java -jar target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar

set -e

JAR="target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar"
OLLAMA_URL="${1:-http://localhost:11434}"
OLLAMA_MODEL="${2:-qwen2.5:3b}"
OUTPUT_DIR="target/repair-loop-results"
LOG_DIR="target/repair-loop-logs"
EXAMPLES_DIR="examples-extended"

# Build if needed
if [ ! -f "$JAR" ]; then
    echo "Building project..."
    mvn -q clean package -DskipTests
fi

mkdir -p "$OUTPUT_DIR" "$LOG_DIR"

echo "============================================"
echo " Repair Loop Runner"
echo " LLM: Ollama ($OLLAMA_MODEL @ $OLLAMA_URL)"
echo " Output: $OUTPUT_DIR"
echo "============================================"

# Check if Ollama is reachable
echo ""
echo "Checking Ollama connectivity..."
if command -v curl &>/dev/null; then
    http_code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 "$OLLAMA_URL/api/tags" 2>/dev/null || echo "000")
    if [ "$http_code" = "000" ]; then
        echo "⚠️  Warning: Could not reach Ollama at $OLLAMA_URL"
        echo "   Make sure Ollama is running: ollama serve"
        echo "   Continuing anyway (will use stub if Ollama is down)..."
    elif [ "$http_code" = "200" ]; then
        echo "✅ Ollama is reachable at $OLLAMA_URL"
        # Verify model is available
        if command -v python3 &>/dev/null; then
            model_found=$(curl -s "$OLLAMA_URL/api/tags" 2>/dev/null | python3 -c "
import json,sys
try:
    data = json.load(sys.stdin)
    models = [m['name'] for m in data.get('models', [])]
    for m in models:
        if '$OLLAMA_MODEL' in m:
            print('yes')
            sys.exit(0)
    print('no')
except: print('unknown')" 2>/dev/null || echo "unknown")
            if [ "$model_found" = "yes" ]; then
                echo "✅ Model '$OLLAMA_MODEL' is available"
            elif [ "$model_found" = "no" ]; then
                echo "⚠️  Model '$OLLAMA_MODEL' not found. Available models:"
                curl -s "$OLLAMA_URL/api/tags" 2>/dev/null | python3 -c "
import json,sys
data = json.load(sys.stdin)
for m in data.get('models', []):
    print(f'  - {m[\"name\"]}')" 2>/dev/null || true
                echo "   Run 'ollama pull $OLLAMA_MODEL' to download it."
            fi
        fi
    else
        echo "⚠️  Ollama returned HTTP $http_code (might need attention)"
    fi
else
    echo "   (curl not available, skipping connectivity check)"
fi
echo ""

total=0
valid_no_repair=0
valid_no_repair_mismatch=0
repaired_ok=0
repaired_mismatch=0
failed_after_repair=0
detection_failed=0

for file in "$EXAMPLES_DIR"/*; do
    [ -f "$file" ] || continue
    basename=$(basename "$file")
    stem="${basename%.*}"
    total=$((total + 1))

    echo ""
    echo "--- [$total] $basename ---"

    outfile="$OUTPUT_DIR/$stem-repair-result.json"
    logfile="$LOG_DIR/$stem-repair.log"
    set +e
    java -jar "$JAR" detect "$file" \
        --llm-provider ollama \
        --llm-url "$OLLAMA_URL" \
        --llm-model "$OLLAMA_MODEL" \
        --llm-temperature 0 \
        --repair-max 2 2> "$logfile" > "$outfile"
    exit_code=$?
    set -e

    if [ $exit_code -eq 0 ]; then
        # Check repair metadata and compare against expected config
        pyresult=$(python3 -c "
import json, os

outfile = '$outfile'
stem = '$stem'
expected_file = 'expected-configs/' + stem + '.config.json'

try:
    with open(outfile) as f:
        r = json.load(f)
except:
    print('?|?|?')
    exit(0)

was_repaired = str(r.get('repaired', '?')).lower()
attempts = str(r.get('repairAttempts', '?'))
matches = 'false'

if os.path.exists(expected_file):
    try:
        with open(expected_file) as f:
            expected = json.load(f)
        cfg = r.get('config', {})
        if cfg:
            e_delim = expected.get('delimiter')
            a_delim = cfg.get('delimiter')
            e_start = expected.get('dataStartRowIndex')
            a_start = cfg.get('dataStartRowIndex')
            e_dec = expected.get('decimalSeparator')
            a_dec = cfg.get('decimalSeparator')
            e_cols = set(expected.get('columns', {}).keys())
            a_cols = set(cfg.get('columns', {}).keys())
            if e_delim == a_delim and e_start == a_start and e_dec == a_dec and e_cols == a_cols:
                matches = 'true'
    except:
        pass

print(f'{was_repaired}|{attempts}|{matches}')
" 2>/dev/null || echo "?|?|?")
        
        was_repaired=$(echo "$pyresult" | cut -d'|' -f1)
        attempts=$(echo "$pyresult" | cut -d'|' -f2)
        matches=$(echo "$pyresult" | cut -d'|' -f3)
        
        if [ "$was_repaired" = "true" ]; then
            if [ "$matches" = "true" ]; then
                echo "  ✅ Repaired and matches expected (attempts: $attempts)"
                repaired_ok=$((repaired_ok + 1))
            else
                echo "  🔧 Repaired but differs from expected (attempts: $attempts)"
                repaired_mismatch=$((repaired_mismatch + 1))
            fi
        elif [ "$was_repaired" = "false" ] && [ "$attempts" = "0" ]; then
            if [ "$matches" = "true" ]; then
                echo "  ✅ Valid immediately, matches expected"
                valid_no_repair=$((valid_no_repair + 1))
            else
                echo "  ⚠️  Valid immediately but differs from expected"
                valid_no_repair_mismatch=$((valid_no_repair_mismatch + 1))
            fi
        elif [ "$was_repaired" = "false" ]; then
            echo "  ❌ Failed to repair (attempts: $attempts)"
            failed_after_repair=$((failed_after_repair + 1))
        else
            echo "  ❓ Unknown status (repaired=$was_repaired, attempts=$attempts)"
            failed_after_repair=$((failed_after_repair + 1))
        fi
    else
        echo "  ❌ Detection failed (exit code: $exit_code)"
        failed_after_repair=$((failed_after_repair + 1))
    fi
    echo "  wrote: $outfile"
    echo "  logs:  $logfile"
done

echo ""
echo "============================================"
echo " SUMMARY"
echo "============================================"
echo " Total files:               $total"
echo ""

if [ "$valid_no_repair" -gt 0 ] || [ "$valid_no_repair_mismatch" -gt 0 ]; then
    echo " Valid immediately:         $((valid_no_repair + valid_no_repair_mismatch))"
    echo "   - matches expected:     $valid_no_repair"
    echo "   - differs from expected: $valid_no_repair_mismatch"
    echo ""
fi

if [ "$repaired_ok" -gt 0 ] || [ "$repaired_mismatch" -gt 0 ]; then
    echo " Repaired successfully:    $((repaired_ok + repaired_mismatch))"
    echo "   - matches expected:     $repaired_ok"
    echo "   - differs from expected: $repaired_mismatch"
    echo ""
fi

if [ "$failed_after_repair" -gt 0 ]; then
    echo " Failed after repair:      $failed_after_repair"
    echo ""
fi

if [ "$detection_failed" -gt 0 ]; then
    echo " Detection failed:         $detection_failed"
    echo ""
fi

echo " Results:   $OUTPUT_DIR/"
echo " Logs:      $LOG_DIR/"
echo "============================================"
