#!/usr/bin/env python3
"""
Chấm điểm bản nháp nhận xét trẻ do AI soạn (G94-200).

Đầu vào:
  - bộ case: src/test/resources/ai-eval/summary-cases.json (facts + kỳ vọng)
  - kết quả chạy thật: target/ai-eval/runs-<model>-<prompt>.json (sinh bởi AiLiveEvalTest)

3 lớp chấm:
  1. Guard (đã chạy trong Java): đúng schema, evidence/nguồn có thật, không số liệu bịa, không từ chẩn đoán.
  2. Luật cứng (file này, deterministic): bỏ sót ý bắt buộc, bịa điểm cần lưu ý, báo dữ liệu ít, cụm từ cấm,
     nhắc dữ liệu không có, định dạng.
  3. LLM giám khảo (tuỳ chọn, --judge): model KHÁC họ với model sinh, chấm từng ý SUPPORTED/PARTIAL/UNSUPPORTED.

Đầu ra (thư mục --out):
  - eval-results.json   : chi tiết từng lượt chạy
  - eval-metrics.json   : chỉ số tổng hợp + so với ngưỡng nghiệm thu
  - labeling-sheet.csv  : phiếu cho giáo viên đánh nhãn (mở bằng Excel/Google Sheets)

Chỉ dùng thư viện chuẩn. Ví dụ (Git Bash, thư mục repo):
  set -a && . ./.env && set +a
  python scripts/ai-eval/evaluate_summaries.py --runs target/ai-eval/runs-openai_gpt-oss-120b-summary_draft_v2.json --judge
"""
import argparse
import csv
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from collections import defaultdict

# Ngưỡng nghiệm thu (đồng bộ với docs/quality/ai-summary-evaluation-report.md)
THRESHOLDS = {
    "guard_pass_rate": 0.95,
    "rule_pass_rate": 0.90,
    "forbidden_violations": 0,
    "diagnostic_flags": 0,
    "faithfulness": 0.90,
    "hallucination_rate": 0.10,
    "p95_latency_ms": 8000,
}

UNAVAILABLE_DATA_WORDS = [
    "điểm danh", "chuyên cần", "vắng mặt", "nghỉ học", "cân nặng", "chiều cao",
    "thực đơn", "khẩu phần", "sốt", "ốm", "bệnh",
]
LOW_DATA_WORDS = ["ít", "chưa đủ", "hạn chế", "chưa nhiều"]
ABSOLUTE_WORDS = ["luôn luôn", "lúc nào cũng", "mọi lúc", "không bao giờ"]
CODE_IN_TEXT = re.compile(r"\b(?:NOTE|CRITERIA)-[A-Za-z0-9_-]+")

JUDGE_PROMPT = """Bạn là giám khảo kiểm tra NHÁP nhận xét trẻ mầm non do một AI khác viết.
Nhiệm vụ: đối chiếu từng ý (claim) với FACTS. FACTS gồm số đếm theo tiêu chí và ghi chú của giáo viên.
Với mỗi claim, chọn verdict:
- SUPPORTED: nội dung suy ra trực tiếp từ FACTS (diễn đạt lại số đếm/ghi chú, khuyến nghị chung như "tiếp tục quan sát").
- PARTIAL: phần lớn đúng nhưng có chi tiết thêm thắt, khái quát quá mức (vd. một ghi chú thành "thường xuyên"), hoặc suy diễn tính cách.
- UNSUPPORTED: có sự việc, số liệu, nhận định không có trong FACTS hoặc trái với FACTS.
Đánh dấu "diagnostic": true nếu có bất kỳ nhận định mang tính chẩn đoán y khoa/tâm lý/phát triển.
"useful": 1-5, mức hữu ích cho giáo viên khi viết nhận xét chính thức.
"tone": 1-5, giọng văn phù hợp gửi nhà trường (ấm áp, khách quan, không gán nhãn).
Chỉ trả JSON: {"claims":[{"id":1,"verdict":"SUPPORTED","reason":"..."}],"diagnostic":false,"useful":4,"tone":5}"""


def load_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def all_texts(content):
    texts = [content.get("summary") or ""]
    for key in ("strengths", "areasForFollowUp"):
        texts += [point.get("text") or "" for point in content.get(key) or []]
    return texts


def evidence_of(content, key):
    ids = set()
    for point in content.get(key) or []:
        ids.update(point.get("evidenceIds") or [])
    return ids


def sentence_count(text):
    return len([part for part in re.split(r"[.!?]+", text) if part.strip()])


def rule_checks(case, content):
    """Trả danh sách (tên luật, đạt?, chi tiết). Luật chỉ áp khi case có kỳ vọng tương ứng."""
    expect = case.get("expect") or {}
    facts = case["input"]
    text = "\n".join(all_texts(content))
    lower = text.lower()
    notes_lower = " ".join(note["text"].lower() for note in facts.get("notes") or [])
    follow_ids = evidence_of(content, "areasForFollowUp")
    all_ids = follow_ids | evidence_of(content, "strengths")
    checks = []

    checks.append(("format_summary_max_4_sentences", sentence_count(content.get("summary") or "") <= 4, ""))
    too_many = [k for k in ("strengths", "areasForFollowUp") if len(content.get(k) or []) > 3]
    checks.append(("format_max_3_points", not too_many, ",".join(too_many)))

    if expect.get("followUpMustCite"):
        missing = [ref for ref in expect["followUpMustCite"] if ref not in follow_ids]
        checks.append(("followup_covers_required", not missing, "thiếu " + ",".join(missing) if missing else ""))
    if expect.get("noFollowUp"):
        count = len(content.get("areasForFollowUp") or [])
        checks.append(("no_invented_followup", count == 0, f"{count} ý cần lưu ý dù dữ liệu toàn Tốt"))
    if expect.get("lowDataNotice"):
        summary = (content.get("summary") or "").lower()
        checks.append(("low_data_notice", any(word in summary for word in LOW_DATA_WORDS), ""))
    if expect.get("forbiddenPhrases"):
        hits = [p for p in expect["forbiddenPhrases"] if p.lower() in lower]
        checks.append(("forbidden_phrases", not hits, ",".join(hits)))
    if expect.get("mustCiteAny"):
        checks.append(("must_cite_any", bool(set(expect["mustCiteAny"]) & all_ids), ""))
    if expect.get("sourcesShouldBeEmpty"):
        checks.append(("irrelevant_source_not_used", not (content.get("sources") or []), str(content.get("sources"))))
    if expect.get("trendWordsAny"):
        checks.append(("mentions_trend", any(w in lower for w in expect["trendWordsAny"]), ""))

    leaked = [w for w in UNAVAILABLE_DATA_WORDS if re.search(r"(?<!\w)" + re.escape(w) + r"(?!\w)", lower)
              and w not in notes_lower]
    checks.append(("no_unavailable_data", not leaked, ",".join(leaked)))
    return checks


def warnings_of(content):
    text = "\n".join(all_texts(content))
    warnings = []
    if CODE_IN_TEXT.search(text):
        warnings.append("mã evidence trong câu (renderer sẽ xoá)")
    absolute = [w for w in ABSOLUTE_WORDS if w in text.lower()]
    if absolute:
        warnings.append("từ tuyệt đối: " + ",".join(absolute))
    return warnings


def call_judge(case, content, base_url, model, api_key):
    claims = []
    if content.get("summary"):
        claims.append(content["summary"])
    for key in ("strengths", "areasForFollowUp"):
        claims += [point["text"] for point in content.get(key) or []]
    facts = dict(case["input"])
    facts.pop("references", None)
    user = ("FACTS:\n" + json.dumps(facts, ensure_ascii=False)
            + "\n\nCLAIMS:\n" + "\n".join(f"{i + 1}. {c}" for i, c in enumerate(claims)))
    body = json.dumps({
        "model": model,
        "temperature": 0,
        "response_format": {"type": "json_object"},
        "messages": [{"role": "system", "content": JUDGE_PROMPT}, {"role": "user", "content": user}],
    }).encode("utf-8")
    request = urllib.request.Request(
        base_url.rstrip("/") + "/chat/completions", data=body,
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + api_key})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                raw = json.load(response)["choices"][0]["message"]["content"]
            raw = re.sub(r"^```(?:json)?|```$", "", raw.strip(), flags=re.M)
            verdict = json.loads(raw)
            if isinstance(verdict, list):
                # Giám khảo đôi khi trả thẳng mảng claims thay vì object
                verdict = {"claims": verdict}
            if not isinstance(verdict, dict) or not isinstance(verdict.get("claims"), list):
                return {"error": "judge output not in expected shape"}
            verdict["claimCount"] = len(claims)
            return verdict
        except urllib.error.HTTPError as error:
            if error.code == 429 and attempt < 2:
                time.sleep(20)
                continue
            return {"error": f"HTTP {error.code}"}
        except (urllib.error.URLError, ValueError, KeyError, TimeoutError) as error:
            return {"error": type(error).__name__}
    return {"error": "retries exhausted"}


def percentile(values, ratio):
    if not values:
        return 0
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(round(ratio * (len(ordered) - 1))))]


def jaccard(a, b):
    return 1.0 if not a and not b else len(a & b) / len(a | b)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--cases", default="src/test/resources/ai-eval/summary-cases.json")
    parser.add_argument("--runs", required=True)
    parser.add_argument("--out", default="target/ai-eval/evaluation")
    parser.add_argument("--judge", action="store_true", help="bật LLM giám khảo (cần AI_JUDGE_* hoặc AI_EMBEDDING_*)")
    parser.add_argument("--judge-pause", type=float, default=4.0)
    args = parser.parse_args()

    cases = {case["name"]: case for case in load_json(args.cases)}
    records = load_json(args.runs)
    judge_base = os.environ.get("AI_JUDGE_BASE_URL") or os.environ.get("AI_EMBEDDING_BASE_URL")
    judge_key = os.environ.get("AI_JUDGE_API_KEY") or os.environ.get("AI_EMBEDDING_API_KEY")
    judge_model = os.environ.get("AI_JUDGE_MODEL", "gemini-3.5-flash-lite")
    if args.judge and not (judge_base and judge_key):
        sys.exit("Thiếu AI_JUDGE_BASE_URL/AI_JUDGE_API_KEY (hoặc AI_EMBEDDING_*) cho giám khảo")

    os.makedirs(args.out, exist_ok=True)
    # Cache kết quả giám khảo theo case#run: chạy lại không tốn thêm quota cho lượt đã chấm
    cache_path = os.path.join(args.out, "judge-cache.json")
    cache = load_json(cache_path) if os.path.exists(cache_path) else {}

    results = []
    for index, record in enumerate(records):
        case = cases[record["case"]]
        result = {"case": record["case"], "category": case.get("category"), "run": record["run"],
                  "status": record["status"], "latencyMs": record.get("latencyMs"),
                  "reason": record.get("reason")}
        if record["status"] == "GUARD_PASS":
            content = record["content"]
            result["content"] = content
            result["rules"] = [{"rule": n, "pass": ok, "detail": d} for n, ok, d in rule_checks(case, content)]
            result["warnings"] = warnings_of(content)
            key = f"{record['case']}#{record['run']}"
            if args.judge and "claims" in cache.get(key, {}):
                result["judge"] = cache[key]
            elif args.judge:
                result["judge"] = call_judge(case, content, judge_base, judge_model, judge_key)
                cache[key] = result["judge"]
                with open(cache_path, "w", encoding="utf-8") as handle:
                    json.dump(cache, handle, ensure_ascii=False)
                print(f"[judge {index + 1}/{len(records)}] {key}", flush=True)
                time.sleep(args.judge_pause)
        results.append(result)

    with open(os.path.join(args.out, "eval-results.json"), "w", encoding="utf-8") as handle:
        json.dump(results, handle, ensure_ascii=False, indent=2)

    metrics = summarize(results, args.judge, judge_model)
    with open(os.path.join(args.out, "eval-metrics.json"), "w", encoding="utf-8") as handle:
        json.dump(metrics, handle, ensure_ascii=False, indent=2)
    write_labeling_sheet(results, cases, os.path.join(args.out, "labeling-sheet.csv"))
    print(json.dumps(metrics["overall"], ensure_ascii=False, indent=2))


def summarize(results, judged, judge_model):
    passed = [r for r in results if r["status"] == "GUARD_PASS"]
    rule_total = sum(len(r["rules"]) for r in passed)
    rule_ok = sum(1 for r in passed for rule in r["rules"] if rule["pass"])
    forbidden = sum(1 for r in passed for rule in r["rules"] if rule["rule"] == "forbidden_phrases" and not rule["pass"])
    failed_rules = defaultdict(list)
    for r in passed:
        for rule in r["rules"]:
            if not rule["pass"]:
                failed_rules[rule["rule"]].append(f"{r['case']}#{r['run']} {rule['detail']}".strip())

    overall = {
        "records": len(results),
        "guard_pass_rate": round(len(passed) / len(results), 3) if results else 0,
        "guard_fail_reasons": dict(_count(r.get("reason") for r in results if r["status"] != "GUARD_PASS")),
        "rule_pass_rate": round(rule_ok / rule_total, 3) if rule_total else 0,
        "forbidden_violations": forbidden,
        "p50_latency_ms": percentile([r["latencyMs"] for r in results], 0.5),
        "p95_latency_ms": percentile([r["latencyMs"] for r in results], 0.95),
        "warnings": dict(_count(w.split(":")[0] for r in passed for w in r["warnings"])),
    }
    if judged:
        verdicts = [c for r in passed if "claims" in r.get("judge", {}) for c in r["judge"]["claims"]]
        supported = sum(1 for c in verdicts if c.get("verdict") == "SUPPORTED")
        partial = sum(1 for c in verdicts if c.get("verdict") == "PARTIAL")
        unsupported = [c for c in verdicts if c.get("verdict") == "UNSUPPORTED"]
        judged_records = [r for r in passed if "claims" in r.get("judge", {})]
        overall.update({
            "judge_model": judge_model,
            "judged_records": len(judged_records),
            "claims": len(verdicts),
            "faithfulness": round((supported + 0.5 * partial) / len(verdicts), 3) if verdicts else 0,
            "claims_supported": supported,
            "claims_partial": partial,
            "claims_unsupported": len(unsupported),
            "hallucination_rate": round(
                sum(1 for r in judged_records if any(c.get("verdict") == "UNSUPPORTED" for c in r["judge"]["claims"]))
                / len(judged_records), 3) if judged_records else 0,
            "diagnostic_flags": sum(1 for r in judged_records if r["judge"].get("diagnostic")),
            "avg_useful": _avg(r["judge"].get("useful") for r in judged_records),
            "avg_tone": _avg(r["judge"].get("tone") for r in judged_records),
            "judge_errors": sum(1 for r in passed if "error" in r.get("judge", {})),
        })

    by_case = defaultdict(list)
    for r in results:
        by_case[r["case"]].append(r)
    stability = {}
    for name, runs in by_case.items():
        ok = [r for r in runs if r["status"] == "GUARD_PASS"]
        sets = [evidence_of(r["content"], "areasForFollowUp") for r in ok]
        pairs = [jaccard(a, b) for i, a in enumerate(sets) for b in sets[i + 1:]]
        stability[name] = {
            "guard_pass": f"{len(ok)}/{len(runs)}",
            "rules_failed": sorted({rule["rule"] for r in ok for rule in r["rules"] if not rule["pass"]}),
            "followup_evidence_jaccard": round(sum(pairs) / len(pairs), 2) if pairs else None,
        }
    overall["followup_stability_avg_jaccard"] = _avg(v["followup_evidence_jaccard"] for v in stability.values())

    acceptance = {}
    for key, threshold in THRESHOLDS.items():
        if key not in overall:
            continue
        value = overall[key]
        lower_is_better = key in ("forbidden_violations", "diagnostic_flags", "hallucination_rate", "p95_latency_ms")
        acceptance[key] = {"value": value, "threshold": threshold,
                           "pass": value <= threshold if lower_is_better else value >= threshold}
    return {"overall": overall, "acceptance": acceptance, "failed_rules": failed_rules, "per_case": stability}


def write_labeling_sheet(results, cases, path):
    columns = ["case", "run", "category", "facts_tom_tat", "ban_nhap_ai", "evidence", "auto_luat_truot",
               "auto_giam_khao_unsupported",
               "GV_dung_su_that_1_5", "GV_huu_ich_1_5", "GV_giong_van_1_5", "GV_can_sua_Y_N", "GV_loi_cu_the"]
    with open(path, "w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(columns)
        for r in results:
            if r["status"] != "GUARD_PASS":
                continue
            facts = cases[r["case"]]["input"]
            facts_short = "; ".join(f"{c['name']}: " + ", ".join(f"{k} {v}" for k, v in c["optionCounts"].items() if v)
                                    for c in facts["criteria"])
            facts_short += " | ghi chú: " + " / ".join(n["text"] for n in facts.get("notes") or [])[:400]
            content = r["content"]
            draft = content["summary"] + "\n" + "\n".join(
                f"[{k}] {p['text']}" for k in ("strengths", "areasForFollowUp") for p in content.get(k) or [])
            evidence = "; ".join(",".join(p.get("evidenceIds") or []) for k in ("strengths", "areasForFollowUp")
                                 for p in content.get(k) or [])
            failed = ",".join(rule["rule"] for rule in r["rules"] if not rule["pass"])
            unsupported = " | ".join(c.get("reason", "") for c in r.get("judge", {}).get("claims", [])
                                     if c.get("verdict") == "UNSUPPORTED")
            writer.writerow([r["case"], r["run"], r["category"], facts_short, draft, evidence, failed, unsupported,
                             "", "", "", "", ""])


def _count(items):
    counts = defaultdict(int)
    for item in items:
        counts[item] += 1
    return counts


def _avg(values):
    values = [v for v in values if isinstance(v, (int, float))]
    return round(sum(values) / len(values), 2) if values else None


if __name__ == "__main__":
    main()
