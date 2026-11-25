"""
Reference engine for Speak-a-House (layout + cost). NOT production code.
Purpose: (1) generate the golden fixtures the frontend builds against,
         (2) show the backend the exact algorithm/shape to port to Java.
Coordinates: metres. Origin = plot front-left corner. x = across the width (left->right facing the house
from the street), y = depth from the street (y=0 is the street edge). 3D: X=x, Z=y, Y=up.
"""
import json, math, sys
from pathlib import Path

FT = 0.3048
SETBACK = {"front": 6.0, "rear": 3.0, "side": 1.5}          # SAMPLE values, not verified regulations
WALL_H, EXT_T, INT_T = 3.0, 0.225, 0.15
DOOR = (0.9, 2.1); WINDOW = (1.2, 1.2, 0.9)                   # width, height, sill
CORRIDOR_W, FRONT_BAND = 1.2, 4.5
DEPTH = {"master_bedroom": 4.2, "bedroom": 3.6, "kitchen": 3.6, "bathroom": 2.4, "store": 1.8, "dining": 3.0}
MIN = {"bedroom": (2.7, 9.0), "master_bedroom": (3.0, 12.0), "kitchen": (2.4, 7.0), "parlour": (3.6, 14.0)}
HABITABLE = {"bedroom", "master_bedroom", "parlour", "kitchen", "dining", "shop", "bq"}
R = lambda v: round(v, 3)

# ---------------- PRICES (SAMPLE, verified=false) ----------------
PRICES = {  # key: (unit, {finish: rate} or rate, group, description)
  "concrete_m3":      ("m3", 180000, "substructure", "Concrete 1:2:4 (cement, sand, granite)"),
  "block_9in":        ("each", 850, "blockwork", "9-inch hollow sandcrete block"),
  "block_6in":        ("each", 700, "blockwork", "6-inch hollow sandcrete block"),
  "cement_bag":       ("bag", 10500, "blockwork", "Cement 50 kg (mortar and plaster)"),
  "roof_timber_m2":   ("m2", 8000, "roofing", "Roof timber and carpentry"),
  "roof_cover_m2":    ("m2", {"basic": 9000, "standard": 12000, "premium": 22000}, "roofing", "Roof covering"),
  "door_each":        ("each", {"basic": 45000, "standard": 85000, "premium": 250000}, "openings", "Door with frame"),
  "window_each":      ("each", {"basic": 60000, "standard": 90000, "premium": 180000}, "openings", "Window 1.2 x 1.2 m"),
  "floor_finish_m2":  ("m2", {"basic": 7000, "standard": 12000, "premium": 25000}, "finishes", "Floor tiles"),
  "ceiling_m2":       ("m2", {"basic": 5000, "standard": 7000, "premium": 14000}, "finishes", "Ceiling"),
  "paint_m2":         ("m2", {"basic": 1500, "standard": 2000, "premium": 3500}, "finishes", "Painting"),
  "services_m2":      ("m2", {"basic": 25000, "standard": 35000, "premium": 60000}, "services", "Plumbing and electrical"),
  "fence_m":          ("m", 45000, "external", "Perimeter fence"),
}
LABOUR_SHARE = 0.30
ROOF_FACTOR = {"flat": 1.05, "gable": 1.18, "hip": 1.22}
COVER_LABEL = {"basic": "aluminium long-span (basic)", "standard": "aluminium long-span", "premium": "stone-coated tiles"}
PRICE_AS_OF = "2026-11-01"
PRICE_SOURCE = "Sample baseline (team estimate), not supplier-verified"

def rate(key, finish):
    r = PRICES[key][1]
    return r[finish] if isinstance(r, dict) else r

# ---------------- LAYOUT ----------------
class Infeasible(Exception):
    def __init__(self, violations): self.violations = violations

def layout(spec):
    k = FT if spec["plot"]["unit"] == "ft" else 1.0
    PW, PD = spec["plot"]["width"] * k, spec["plot"]["depth"] * k
    bx0, by0 = SETBACK["side"], SETBACK["front"]
    bw, bd = PW - 2 * SETBACK["side"], PD - SETBACK["front"] - SETBACK["rear"]
    W = min(bw, 12.0); x0 = bx0 + (bw - W) / 2; y0 = by0
    wing = (W - CORRIDOR_W) / 2
    rooms = []
    def add(t, label, x, y, w, d, zone="main"):
        rooms.append({"id": f"r{len(rooms)+1}", "type": t, "label": label, "x": R(x), "y": R(y),
                      "width": R(w), "depth": R(d), "area": R(w * d), "zone": zone})
    # front band
    f = spec["features"]
    if f["shop"]:
        add("shop", "Shop", x0, y0, 3.6, FRONT_BAND)
        add("parlour", "Parlour", x0 + 3.6, y0, W - 3.6, FRONT_BAND)
    else:
        add("parlour", "Parlour", x0, y0, W, FRONT_BAND)
    # wings
    queue = [("kitchen", "Kitchen")]
    n = spec["rooms"]["bedrooms"]
    for i in range(n):
        queue.append(("master_bedroom", "Master bedroom") if i == 0 else ("bedroom", f"Bedroom {i+1}"))
    if spec["rooms"].get("dining"): queue.append(("dining", "Dining"))
    queue += [("bathroom", "Bathroom"), ("store", "Store")]
    wings = {"L": [], "R": []}; depth = {"L": 0.0, "R": 0.0}
    for t, label in queue:
        side = "R" if t == "kitchen" else min(depth, key=lambda s: (depth[s], s))
        wings[side].append([t, label, DEPTH[t]]); depth[side] += DEPTH[t]
    D = max(depth.values())
    for s in "LR":                       # pad the shorter wing's last room
        if wings[s]: wings[s][-1][2] += D - depth[s]
    yb = y0 + FRONT_BAND
    for s, xs in (("L", x0), ("R", x0 + wing + CORRIDOR_W)):
        y = yb
        for t, label, d in wings[s]:
            add(t, label, xs, y, wing, d); y += d
    add("corridor", "Corridor", x0 + wing, yb, CORRIDOR_W, D)
    main_depth = FRONT_BAND + D
    if f["bq"]:
        add("bq", "Boys' quarters", bx0, by0 + bd - 3.6, 3.6, 3.6, "annex")
        add("bathroom", "BQ toilet", bx0 + 3.6, by0 + bd - 3.6, 2.4, 3.6, "annex")
    # ---- validation (the engine refuses, never draws a broken plan)
    v = []
    for r in rooms:
        if r["x"] < bx0 - 1e-6 or r["y"] < by0 - 1e-6 or r["x"] + r["width"] > bx0 + bw + 1e-6 or r["y"] + r["depth"] > by0 + bd + 1e-6:
            v.append({"rule": "inside_buildable_area", "room": r["label"], "message": f"{r['label']} falls outside the area allowed by setbacks"})
        if r["type"] in MIN:
            mn, ma = MIN[r["type"]]
            if min(r["width"], r["depth"]) < mn or r["area"] < ma:
                v.append({"rule": "min_room_size", "room": r["label"], "required": ma, "actual": r["area"], "message": f"{r['label']} is below the minimum size"})
    for i, a in enumerate(rooms):
        for b in rooms[i+1:]:
            if a["x"] < b["x"] + b["width"] - 1e-6 and b["x"] < a["x"] + a["width"] - 1e-6 and a["y"] < b["y"] + b["depth"] - 1e-6 and b["y"] < a["y"] + a["depth"] - 1e-6:
                v.append({"rule": "no_overlap", "room": f"{a['label']} / {b['label']}", "message": "Rooms overlap"})
    annex_y = by0 + bd - 3.6
    if f["bq"] and y0 + main_depth + 1.5 > annex_y:
        v.append({"rule": "annex_gap", "room": "Boys' quarters", "required": 1.5, "actual": R(annex_y - y0 - main_depth),
                  "message": "Not enough space behind the house for a boys' quarters", "suggestion": "Remove a bedroom or choose a deeper plot"})
    if v: raise Infeasible(v)

    walls, openings = build_walls(rooms, x0, y0, W, main_depth, f, spec)
    fence = {"x": 0, "y": 0, "width": R(PW), "depth": R(PD), "gateOffset": R(PW / 2), "gateWidth": 3.6} if f["fence"] else None
    return {"units": "m", "wallHeight": WALL_H, "plot": {"width": R(PW), "depth": R(PD)},
            "setbacks": SETBACK, "buildable": {"x": R(bx0), "y": R(by0), "width": R(bw), "depth": R(bd)},
            "footprint": {"x": R(x0), "y": R(y0), "width": R(W), "depth": R(main_depth)},
            "rooms": rooms, "walls": walls, "openings": openings, "fence": fence,
            "roofing": spec["roofing"], "frontEdge": "y0"}

def build_walls(rooms, x0, y0, W, Dm, f, spec):
    # collect edges per line, merge collinear intervals, mark exterior
    H, V = {}, {}
    for r in rooms:
        x1, y1, x2, y2 = r["x"], r["y"], R(r["x"] + r["width"]), R(r["y"] + r["depth"])
        H.setdefault(y1, []).append((x1, x2)); H.setdefault(y2, []).append((x1, x2))
        V.setdefault(x1, []).append((y1, y2)); V.setdefault(x2, []).append((y1, y2))
    def merge(iv):
        iv = sorted(iv); out = [list(iv[0])]
        for a, b in iv[1:]:
            if a <= out[-1][1] + 1e-6: out[-1][1] = max(out[-1][1], b)
            else: out.append([a, b])
        return out
    walls = []
    boxes = [(x0, y0, x0 + W, y0 + Dm)]
    annex = [r for r in rooms if r["zone"] == "annex"]
    if annex:
        boxes.append((min(r["x"] for r in annex), min(r["y"] for r in annex),
                      max(r["x"] + r["width"] for r in annex), max(r["y"] + r["depth"] for r in annex)))
    def is_ext(horizontal, c, a, b):
        mid = (a + b) / 2
        for bx1, by1, bx2, by2 in boxes:
            if horizontal and (abs(c - by1) < 1e-6 or abs(c - by2) < 1e-6) and bx1 - 1e-6 <= mid <= bx2 + 1e-6: return True
            if not horizontal and (abs(c - bx1) < 1e-6 or abs(c - bx2) < 1e-6) and by1 - 1e-6 <= mid <= by2 + 1e-6: return True
        return False
    for y, iv in sorted(H.items()):
        for a, b in merge(iv):
            ext = is_ext(True, y, a, b)
            walls.append({"id": f"w{len(walls)+1}", "x1": R(a), "y1": y, "x2": R(b), "y2": y, "thickness": EXT_T if ext else INT_T, "exterior": ext})
    for x, iv in sorted(V.items()):
        for a, b in merge(iv):
            ext = is_ext(False, x, a, b)
            walls.append({"id": f"w{len(walls)+1}", "x1": x, "y1": R(a), "x2": x, "y2": R(b), "thickness": EXT_T if ext else INT_T, "exterior": ext})
    openings = []
    def place(kind, px, py, horizontal_hint=None):
        for w in walls:
            hor = w["y1"] == w["y2"]
            if horizontal_hint is not None and hor != horizontal_hint: continue
            if hor and abs(py - w["y1"]) < 1e-6 and w["x1"] - 1e-6 <= px <= w["x2"] + 1e-6:
                off = px - w["x1"]
            elif not hor and abs(px - w["x1"]) < 1e-6 and w["y1"] - 1e-6 <= py <= w["y2"] + 1e-6:
                off = py - w["y1"]
            else: continue
            o = {"id": f"o{len(openings)+1}", "type": kind, "wallId": w["id"], "offset": R(off)}
            o.update({"width": DOOR[0], "height": DOOR[1], "sill": 0.0} if kind == "door" else
                     {"width": WINDOW[0], "height": WINDOW[1], "sill": WINDOW[2]})
            openings.append(o); return
        raise RuntimeError(f"no wall for {kind} at {px},{py}")
    corr = next(r for r in rooms if r["type"] == "corridor")
    cx_l, cx_r = corr["x"], R(corr["x"] + corr["width"])
    for r in rooms:
        cx, cy = R(r["x"] + r["width"] / 2), R(r["y"] + r["depth"] / 2)
        t = r["type"]
        if t == "parlour":
            place("door", cx, r["y"], True); place("window", R(cx - 2.2), r["y"], True)
            place("door", R(corr["x"] + corr["width"] / 2), R(r["y"] + r["depth"]), True)  # into corridor
        elif t == "shop":
            place("door", R(r["x"] + 0.9), r["y"], True); place("window", R(r["x"] + 2.5), r["y"], True)
        elif r["zone"] == "main" and t != "corridor":
            on_left = abs(R(r["x"] + r["width"]) - cx_l) < 1e-6
            place("door", cx_l if on_left else cx_r, cy, False)
            if t != "store":
                place("window", r["x"] if on_left else R(r["x"] + r["width"]), cy, False)
        elif t == "corridor":
            place("door", cx, R(r["y"] + r["depth"]), True)   # rear door
        elif r["zone"] == "annex":
            place("door", cx, r["y"], True)
            if t == "bq": place("window", r["x"], cy, False)
    return walls, openings

# ---------------- COST ----------------
def estimate(spec, plan):
    fin = spec["finish"]
    op_by_wall = {}
    for o in plan["openings"]: op_by_wall.setdefault(o["wallId"], []).append(o)
    ext_area = int_area = wall_len = 0.0
    for w in plan["walls"]:
        L = math.hypot(w["x2"] - w["x1"], w["y2"] - w["y1"]); wall_len += L
        a = L * WALL_H - sum(o["width"] * o["height"] for o in op_by_wall.get(w["id"], []))
        if w["exterior"]: ext_area += a
        else: int_area += a
    floor_area = sum(r["area"] for r in plan["rooms"])
    roof_plan = plan["footprint"]["width"] * plan["footprint"]["depth"] + sum(r["area"] for r in plan["rooms"] if r["zone"] == "annex")
    roof_area = roof_plan * ROOF_FACTOR[spec["roofing"]]
    doors = sum(1 for o in plan["openings"] if o["type"] == "door")
    windows = sum(1 for o in plan["openings"] if o["type"] == "window")
    blocks9 = math.ceil(ext_area * 10 + wall_len * 0.6 * 10 * 0.5)   # walls + foundation courses (ext share)
    blocks6 = math.ceil(int_area * 10)
    cement = math.ceil((blocks9 + blocks6) / 30 + (ext_area + int_area) * 2 / 10)
    concrete = wall_len * 0.675 * 0.225 + floor_area * 0.15
    q = [
        ("concrete_m3", concrete), ("block_9in", blocks9), ("block_6in", blocks6), ("cement_bag", cement),
        ("roof_timber_m2", roof_area), ("roof_cover_m2", roof_area),
        ("door_each", doors), ("window_each", windows),
        ("floor_finish_m2", floor_area), ("ceiling_m2", floor_area), ("paint_m2", (ext_area + int_area) * 2),
        ("services_m2", floor_area),
    ]
    if plan["fence"]: q.append(("fence_m", 2 * (plan["plot"]["width"] + plan["plot"]["depth"]) - plan["fence"]["gateWidth"]))
    lines = []
    for key, qty in q:
        unit, _, group, desc = PRICES[key]
        if key == "roof_cover_m2": desc = f"Roof covering, {COVER_LABEL[fin]}"
        rt = rate(key, fin); qty = R(qty)
        lines.append({"key": key, "group": group, "description": desc, "quantity": qty, "unit": unit,
                      "rate": rt, "amount": round(qty * rt), "priceSource": PRICE_SOURCE, "observedOn": PRICE_AS_OF, "verified": False})
    materials = sum(l["amount"] for l in lines)
    lines.append({"key": "labour", "group": "labour", "description": f"Labour ({int(LABOUR_SHARE*100)}% of materials, sample assumption)",
                  "quantity": 1, "unit": "lot", "rate": round(materials * LABOUR_SHARE), "amount": round(materials * LABOUR_SHARE),
                  "priceSource": PRICE_SOURCE, "observedOn": PRICE_AS_OF, "verified": False})
    groups = {}
    for l in lines: groups[l["group"]] = groups.get(l["group"], 0) + l["amount"]
    expected = sum(groups.values())
    low, high = round(expected * 0.90), round(expected * 1.20)
    budget = None
    if spec.get("budget"):
        b = spec["budget"]["amount"]
        status = "over" if expected > b else ("near" if high > b else "within")
        budget = {"amount": b, "status": status, "delta": b - expected}
    drivers = sorted(({"group": g, "amount": a, "share": round(a / expected, 3)} for g, a in groups.items()), key=lambda d: -d["amount"])[:3]
    return {"currency": "NGN", "region": spec["region"], "finish": fin, "priceDataVerified": False, "priceAsOf": PRICE_AS_OF,
            "lines": lines, "groups": [{"group": g, "amount": a} for g, a in groups.items()],
            "low": low, "expected": expected, "high": high, "range": {"lowFactor": 0.9, "highFactor": 1.2},
            "budget": budget, "drivers": drivers, "missingPrices": [],
            "quantities": {"floorAreaM2": R(floor_area), "roofAreaM2": R(roof_area), "externalWallAreaM2": R(ext_area), "internalWallAreaM2": R(int_area), "doors": doors, "windows": windows}}

def diff(prev, cur):
    if prev is None: return None
    changes = []
    def walk(a, b, path):
        if isinstance(a, dict) and isinstance(b, dict):
            for k in sorted(set(a) | set(b)): walk(a.get(k), b.get(k), f"{path}/{k}")
        elif a != b: changes.append({"path": path, "from": a, "to": b})
    walk(prev["spec"], cur["spec"], "")
    pg = {g["group"]: g["amount"] for g in prev["estimate"]["groups"]}
    cg = {g["group"]: g["amount"] for g in cur["estimate"]["groups"]}
    by = [{"group": g, "delta": cg.get(g, 0) - pg.get(g, 0)} for g in sorted(set(pg) | set(cg)) if cg.get(g, 0) != pg.get(g, 0)]
    pr = {r["label"] for r in prev["plan"]["rooms"]}; cr = {r["label"] for r in cur["plan"]["rooms"]}
    return {"changes": changes, "roomsAdded": sorted(cr - pr), "roomsRemoved": sorted(pr - cr),
            "costDelta": {"expected": cur["estimate"]["expected"] - prev["estimate"]["expected"], "byGroup": sorted(by, key=lambda d: -abs(d["delta"]))}}
