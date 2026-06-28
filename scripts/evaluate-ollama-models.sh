#!/bin/bash
# Model comparison script for 4 GB VRAM
# Evaluates 4 small Ollama models in sequence using the existing evaluation harness.
#
# Usage:
#   ./scripts/evaluate-ollama-models.sh
#
# Prerequisites:
#   - Ollama running at http://localhost:11434
#   - Models pulled: qwen2.5-coder:3b, llama3.2:3b, phi4-mini, gemma3:4b
#   - Project built (mvn -q clean package -DskipTests)
#
# Outputs:
#   target/evaluation-reports/evaluation-<model>.json  — per-model reports

set -e

OLLAMA_URL="${1:-http://localhost:11434}"
REPORT_DIR="target/evaluation-reports"
EVAL_SCRIPT="scripts/run-evaluation.sh"
JAR="target/pnp-csv-sampler-0.1.0-SNAPSHOT.jar"

# Models in recommended test order
MODELS=(
  "qwen2.5-coder:3b"
  "llama3.2:3b"
  "phi4-mini"
  "gemma3:4b"
)

# Build if needed
if [ ! -f "$JAR" ]; then
    echo "Building project..."
    mvn -q clean package -DskipTests
fi

mkdir -p "$REPORT_DIR"

echo "============================================"
echo " Model Comparison — 4 GB VRAM"
echo "============================================"
echo " Ollama URL: $OLLAMA_URL"
echo " Models:     ${MODELS[*]}"
echo " Output:     $REPORT_DIR/"
echo "============================================"
echo ""

# Check connectivity
if command -v curl &>/dev/null; then
    http_code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 "$OLLAMA_URL/api/tags" 2>/dev/null || echo "000")
    if [ "$http_code" != "200" ]; then
        echo " ERROR: Ollama not reachable at $OLLAMA_URL"
        echo " Start Ollama: ollama serve"
        exit 1
    fi
    echo " ✅ Ollama reachable"
    echo ""
fi

# Check which models are available
echo " Checking available models..."
for model in "${MODELS[@]}"; do
    if curl -s "$OLLAMA_URL/api/tags" 2>/dev/null | python3 -c "import json,sys; tags=json.load(sys.stdin).get('models',[]); print(any(m['name']==model for m in tags))" 2>/dev/null | grep -q "True"; then
        echo "   ✅ $model — available"
    else
        echo "   ⚠️  $model — NOT available (run: ollama pull $model)"
    fi
done
echo ""

# Track results
declare -a results
start_time=$(date +%s)

for model in "${MODELS[@]}"; do
    echo "============================================"
    echo " Evaluating: $model"
    echo "============================================"

    # Sanitize model name for filename (replace : and / with -)
    safe_name=$(echo "$model" | tr ':/' '-')
    report_file="$REPORT_DIR/evaluation-$safe_name.json"
    md_report_file="$REPORT_DIR/evaluation-$safe_name.md"

    # Run evaluation
    model_start=$(date +%s)
    if $EVAL_SCRIPT "$OLLAMA_URL" "$model"; then
        model_end=$(date +%s)
        runtime=$((model_end - model_start))
        echo " ✅ $model completed in ${runtime}s"

        # Copy reports
        if [ -f target/evaluation-report.json ]; then
            cp target/evaluation-report.json "$report_file"
            # Add runtime to the JSON report
            python3 -c "
import json
with open('$report_file') as f: r = json.load(f)
r['runtimeSeconds'] = $runtime
r['model'] = '$model'
with open('$report_file', 'w') as f: json.dump(r, f, indent=2)
" 2>/dev/null || true
            echo "   Report: $report_file"
        fi
        if [ -f target/evaluation-report.md ]; then
            cp target/evaluation-report.md "$md_report_file"
        fi

        results+=("  ✅ $model — ${runtime}s — report: $report_file")
    else
        model_end=$(date +%s)
        runtime=$((model_end - model_start))
        echo " ⚠️  $model failed after ${runtime}s (continuing)"
        results+=("  ⚠️  $model — FAILED after ${runtime}s")
    fi
    echo ""
done

end_time=$(date +%s)
total_time=$((end_time - start_time))

echo "============================================"
echo " COMPARISON COMPLETE"
echo "============================================"
echo " Total time: ${total_time}s"
echo ""
echo " Results:"
for r in "${results[@]}"; do
    echo "$r"
done
echo ""
echo " Reports: $REPORT_DIR/"
echo "============================================"
