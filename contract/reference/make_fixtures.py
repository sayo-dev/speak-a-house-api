"""Builds contract/fixtures/*.json from the reference engine. Run: python3 make_fixtures.py"""
import json, copy
from pathlib import Path
from reference_engine import layout, estimate, diff, Infeasible

OUT = Path(__file__).resolve().parent.parent / "fixtures"
SID = "ses_demo0001"
T0 = "2026-11-25T10:00:00Z"

base = {"plot": {"width": 50, "depth": 100, "unit": "ft", "unitConfirmed": False},
        "houseType": "bungalow", "rooms": {"bedrooms": 3, "ensuites": 0, "dining": False},
        "features": {"shop": True, "bq": False, "garage": False, "fence": True},
        "roofing": "hip", "finish": "standard",
        "budget": {"amount": 50000000, "currency": "NGN"}, "region": "abeokuta"}

def version(seq, spec, text, parent, explanation):
    plan = layout(spec); est = estimate(spec, plan)
    v = {"id": f"ver_{seq:04d}", "sessionId": SID, "seq": seq, "parentId": parent["id"] if parent else None,
         "inputText": text, "createdAt": f"2026-11-25T10:0{seq}:00Z", "spec": spec, "plan": plan, "estimate": est,
         "diff": None, "explanation": explanation}
    v["diff"] = diff(parent, v)
    return v

def w(name, obj): (OUT / name).write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n")

OUT.mkdir(parents=True, exist_ok=True)
U1 = "Three-bedroom bungalow on a 50 by 100 plot in Abeokuta, budget 50 million naira, shop at the front"
w("health.json", {"status": "UP", "version": "0.1.0", "contractVersion": "1.0.0", "aiEnabled": False, "priceDataVerified": False, "time": T0})
w("session.create.json", {"sessionId": SID, "token": "tok_demo_not_secret", "createdAt": T0})
w("interpret.01.spec.json", {"kind": "spec", "source": "rules", "spec": base,
   "summary": ["3 bedrooms", "Bungalow", "Plot 50 × 100 (feet?)", "Shop at the front", "Budget ₦50,000,000", "Abeokuta"],
   "needsConfirm": True,
   "confirmations": [{"field": "plot.unit", "question": "Is your plot 50 × 100 feet or 50 × 100 metres?", "options": ["ft", "m"], "default": "ft"}]})
s1 = copy.deepcopy(base); s1["plot"]["unitConfirmed"] = True
v1 = version(1, s1, U1, None, ["3-bedroom bungalow with a shop at the front on a 50 × 100 ft plot."])
s2 = copy.deepcopy(s1); s2["features"]["bq"] = True
w("interpret.02.patch.json", {"kind": "patch", "source": "rules", "baseVersionId": v1["id"],
   "patch": [{"op": "set", "path": "/features/bq", "value": True}], "spec": s2, "summary": ["Add boys' quarters"]})
v2 = version(2, s2, "Add a boys' quarters at the back", v1, ["Added a boys' quarters with its own toilet at the rear of the plot."])
s3 = copy.deepcopy(s2); s3["finish"] = "premium"
w("interpret.03.patch.json", {"kind": "patch", "source": "rules", "baseVersionId": v2["id"],
   "patch": [{"op": "set", "path": "/finish", "value": "premium"}], "spec": s3, "summary": ["Finish: standard → premium"]})
v3 = version(3, s3, "Make it premium finish", v2, ["Upgraded finishes to premium (stone-coated roof, better tiles, doors and windows)."])
s4 = copy.deepcopy(s3); s4["finish"] = "standard"
w("interpret.04.patch.json", {"kind": "patch", "source": "rules", "baseVersionId": v3["id"],
   "patch": [{"op": "set", "path": "/finish", "value": "standard"}], "spec": s4,
   "summary": ["Biggest saving: finish premium → standard"], "reason": "make_cheaper"})
v4 = version(4, s4, "Make it cheaper", v3, ["Lowered the finish level, the largest cost driver you can change without losing rooms."])
w("interpret.05.rejected.json", {"kind": "rejected", "source": "rules", "code": "INJECTION",
   "reason": "Prices and building rules can't be changed from the chat. I can change the house design."})
w("interpret.06.clarify.json", {"kind": "clarify", "source": "rules", "field": "budget",
   "question": "By \"30 bar\", do you mean ₦30,000,000?", "options": ["₦30,000,000", "Let me type the amount"]})
w("interpret.07.rejected.out-of-scope.json", {"kind": "rejected", "source": "rules", "code": "OUT_OF_SCOPE",
   "reason": "Right now I can design single-storey bungalows only. Duplexes are coming next."})
for v in (v1, v2, v3, v4): w(f"version.{v['seq']}.json", v)
summ = lambda v: {"id": v["id"], "seq": v["seq"], "parentId": v["parentId"], "inputText": v["inputText"], "createdAt": v["createdAt"],
                  "expected": v["estimate"]["expected"], "budgetStatus": (v["estimate"]["budget"] or {}).get("status")}
w("versions.list.json", {"sessionId": SID, "headVersionId": v4["id"], "versions": [summ(v) for v in (v1, v2, v3, v4)]})
# infeasible example: 6 bedrooms + BQ on a small plot
bad = copy.deepcopy(s2); bad["plot"] = {"width": 40, "depth": 60, "unit": "ft", "unitConfirmed": True}; bad["rooms"]["bedrooms"] = 6
try:
    layout(bad); raise SystemExit("expected infeasible")
except Infeasible as e:
    w("error.422.layout-infeasible.json", {"type": "https://speakahouse.ng/problems/layout-infeasible", "title": "This house doesn't fit the plot",
       "status": 422, "code": "LAYOUT_INFEASIBLE", "detail": "The requested rooms don't fit inside the setbacks of a 40 × 60 ft plot.",
       "instance": f"/api/v1/sessions/{SID}/versions", "violations": e.violations})
w("error.400.validation.json", {"type": "https://speakahouse.ng/problems/validation", "title": "Invalid request", "status": 400,
   "code": "VALIDATION_FAILED", "detail": "rooms.bedrooms must be between 1 and 6", "instance": f"/api/v1/sessions/{SID}/versions",
   "errors": [{"field": "rooms.bedrooms", "message": "must be between 1 and 6"}]})
for v in (v1, v2, v3, v4):
    e = v["estimate"]; print(v["seq"], v["inputText"][:30], "rooms", len(v["plan"]["rooms"]), "walls", len(v["plan"]["walls"]),
          "open", len(v["plan"]["openings"]), "exp", f"{e['expected']:,}", "low", f"{e['low']:,}", "high", f"{e['high']:,}", e["budget"]["status"])
