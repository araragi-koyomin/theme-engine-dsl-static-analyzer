#!/usr/bin/env bash
# 改动 canary: 构建 fat jar + 真跑真实 DSL 语料, 输出聚合诊断 JSON.
# 用法: bash scripts/canary-real-run.sh > canary.out
# 改动前/后各跑一次, diff 两个 .out. diff 空 = 改动对真实分析行为零影响.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
./gradlew --no-daemon :feature:analysis:buildFatJar --console=plain >/dev/null
JAR="feature/analysis/build/cli/dsl-analyzer.jar"
CORPUS=(
  "feature/analysis/src/test/resources/fixtures/complex/type_inference_edge_cases.xml"
  "feature/analysis/src/test/resources/fixtures/e2e-pipeline/widget_multi_violation.xml"
  "feature/analysis/src/test/resources/fixtures/e2e-pipeline/wallpaper_constraint_enum.xml"
  "feature/analysis/src/test/resources/fixtures/e2e-pipeline/lockscreen_type_and_ref.xml"
  "feature/analysis/src/test/resources/fixtures/e2e-pipeline/clean/lockscreen_valid.xml"
)
for f in "${CORPUS[@]}"; do
  echo "=== $f ==="
  java -jar "$JAR" --format json "$f" || true
done
