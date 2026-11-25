package ng.speakahouse.api.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import ng.speakahouse.api.common.ApiException;
import ng.speakahouse.api.common.ErrorCode;
import ng.speakahouse.api.spec.HouseSpec;
import org.springframework.stereotype.Component;

@Component
public class LayoutEngine {

    public static final double FT = 0.3048;
    public static final Plan.Setbacks SETBACK = new Plan.Setbacks(6.0, 3.0, 1.5);
    public static final double WALL_H = 3.0;
    public static final double EXT_T = 0.225;
    public static final double INT_T = 0.15;
    public static final double DOOR_W = 0.9;
    public static final double DOOR_H = 2.1;
    public static final double WINDOW_W = 1.2;
    public static final double WINDOW_H = 1.2;
    public static final double WINDOW_SILL = 0.9;
    public static final double CORRIDOR_W = 1.2;
    public static final double FRONT_BAND = 4.5;

    private static final Map<String, Double> DEPTH = Map.of(
            "master_bedroom", 4.2,
            "bedroom", 3.6,
            "kitchen", 3.6,
            "bathroom", 2.4,
            "store", 1.8,
            "dining", 3.0
    );

    private static final Map<String, double[]> MIN_SIZE = Map.of(
            "bedroom", new double[]{2.7, 9.0},
            "master_bedroom", new double[]{3.0, 12.0},
            "kitchen", new double[]{2.4, 7.0},
            "parlour", new double[]{3.6, 14.0}
    );

    public record Violation(
            String rule,
            String room,
            String message,
            Double required,
            Double actual,
            String suggestion) {
        public static Violation of(String rule, String room, String message) {
            return new Violation(rule, room, message, null, null, null);
        }
    }

    private static final class WingItem {
        final String type;
        final String label;
        double depth;

        WingItem(String type, String label, double depth) {
            this.type = type;
            this.label = label;
            this.depth = depth;
        }
    }

    private record Interval(double a, double b) {}

    private record Box(double x1, double y1, double x2, double y2) {}

    public Plan layout(HouseSpec spec) {
        double k = spec.plot().unit() == HouseSpec.PlotUnit.ft ? FT : 1.0;
        double pw = spec.plot().width() * k;
        double pd = spec.plot().depth() * k;
        double bx0 = SETBACK.side();
        double by0 = SETBACK.front();
        double bw = pw - 2 * SETBACK.side();
        double bd = pd - SETBACK.front() - SETBACK.rear();

        double w = Math.min(bw, 12.0);
        double x0 = bx0 + (bw - w) / 2.0;
        double y0 = by0;
        double wing = (w - CORRIDOR_W) / 2.0;

        List<Plan.Room> rooms = new ArrayList<>();
        var f = spec.features();
        if (f.shop()) {
            addRoom(rooms, "shop", "Shop", x0, y0, 3.6, FRONT_BAND, "main");
            addRoom(rooms, "parlour", "Parlour", x0 + 3.6, y0, w - 3.6, FRONT_BAND, "main");
        } else {
            addRoom(rooms, "parlour", "Parlour", x0, y0, w, FRONT_BAND, "main");
        }

        List<String[]> queue = new ArrayList<>();
        queue.add(new String[]{"kitchen", "Kitchen"});
        int n = spec.rooms().bedrooms();
        for (int i = 0; i < n; i++) {
            if (i == 0) {
                queue.add(new String[]{"master_bedroom", "Master bedroom"});
            } else {
                queue.add(new String[]{"bedroom", "Bedroom " + (i + 1)});
            }
        }
        if (spec.rooms().dining()) {
            queue.add(new String[]{"dining", "Dining"});
        }
        queue.add(new String[]{"bathroom", "Bathroom"});
        queue.add(new String[]{"store", "Store"});

        List<WingItem> left = new ArrayList<>();
        List<WingItem> right = new ArrayList<>();
        double depthL = 0.0;
        double depthR = 0.0;
        for (String[] item : queue) {
            String t = item[0];
            String label = item[1];
            double d = DEPTH.get(t);
            boolean toRight = "kitchen".equals(t) || depthR < depthL;
            if (toRight) {
                right.add(new WingItem(t, label, d));
                depthR += d;
            } else {
                left.add(new WingItem(t, label, d));
                depthL += d;
            }
        }

        double dMax = Math.max(depthL, depthR);
        if (!left.isEmpty()) {
            left.get(left.size() - 1).depth += dMax - depthL;
        }
        if (!right.isEmpty()) {
            right.get(right.size() - 1).depth += dMax - depthR;
        }

        double yb = y0 + FRONT_BAND;
        double y = yb;
        for (WingItem wi : left) {
            addRoom(rooms, wi.type, wi.label, x0, y, wing, wi.depth, "main");
            y += wi.depth;
        }
        y = yb;
        for (WingItem wi : right) {
            addRoom(rooms, wi.type, wi.label, x0 + wing + CORRIDOR_W, y, wing, wi.depth, "main");
            y += wi.depth;
        }
        addRoom(rooms, "corridor", "Corridor", x0 + wing, yb, CORRIDOR_W, dMax, "main");

        double mainDepth = FRONT_BAND + dMax;
        if (f.bq()) {
            addRoom(rooms, "bq", "Boys' quarters", bx0, by0 + bd - 3.6, 3.6, 3.6, "annex");
            addRoom(rooms, "bathroom", "BQ toilet", bx0 + 3.6, by0 + bd - 3.6, 2.4, 3.6, "annex");
        }

        List<Violation> violations = validateRooms(rooms, bx0, by0, bw, bd, y0, mainDepth, f.bq());
        if (!violations.isEmpty()) {
            String plotLabel = fmtNum(spec.plot().width()) + " × " + fmtNum(spec.plot().depth()) + " " + spec.plot().unit().name();
            throw new ApiException(
                    ErrorCode.LAYOUT_INFEASIBLE,
                    "The requested rooms don't fit inside the setbacks of a " + plotLabel + " plot.",
                    Map.of("violations", violations));
        }

        WallsAndOpenings wo = buildWalls(rooms, x0, y0, w, mainDepth);
        Plan.Fence fence = f.fence()
                ? new Plan.Fence(0, 0, round3(pw), round3(pd), round3(pw / 2.0), 3.6)
                : null;

        return new Plan(
                "m",
                WALL_H,
                new Plan.Dimensions(round3(pw), round3(pd)),
                SETBACK,
                new Plan.Rect(round3(bx0), round3(by0), round3(bw), round3(bd)),
                new Plan.Rect(round3(x0), round3(y0), round3(w), round3(mainDepth)),
                rooms,
                wo.walls(),
                wo.openings(),
                fence,
                spec.roofing().name(),
                "y0");
    }

    private static List<Violation> validateRooms(
            List<Plan.Room> rooms, double bx0, double by0, double bw, double bd,
            double y0, double mainDepth, boolean hasBq) {
        List<Violation> v = new ArrayList<>();
        for (Plan.Room r : rooms) {
            if (r.x() < bx0 - 1e-6 || r.y() < by0 - 1e-6
                    || r.x() + r.width() > bx0 + bw + 1e-6
                    || r.y() + r.depth() > by0 + bd + 1e-6) {
                v.add(Violation.of("inside_buildable_area", r.label(),
                        r.label() + " falls outside the area allowed by setbacks"));
            }
            double[] min = MIN_SIZE.get(r.type());
            if (min != null) {
                if (Math.min(r.width(), r.depth()) < min[0] || r.area() < min[1]) {
                    v.add(new Violation("min_room_size", r.label(),
                            r.label() + " is below the minimum size", min[1], r.area(), null));
                }
            }
        }
        for (int i = 0; i < rooms.size(); i++) {
            Plan.Room a = rooms.get(i);
            for (int j = i + 1; j < rooms.size(); j++) {
                Plan.Room b = rooms.get(j);
                if (a.x() < b.x() + b.width() - 1e-6 && b.x() < a.x() + a.width() - 1e-6
                        && a.y() < b.y() + b.depth() - 1e-6 && b.y() < a.y() + a.depth() - 1e-6) {
                    v.add(Violation.of("no_overlap", a.label() + " / " + b.label(), "Rooms overlap"));
                }
            }
        }
        double annexY = by0 + bd - 3.6;
        if (hasBq && y0 + mainDepth + 1.5 > annexY) {
            v.add(new Violation("annex_gap", "Boys' quarters",
                    "Not enough space behind the house for a boys' quarters",
                    1.5, round3(annexY - y0 - mainDepth),
                    "Remove a bedroom or choose a deeper plot"));
        }
        return v;
    }

    private record WallsAndOpenings(List<Plan.Wall> walls, List<Plan.Opening> openings) {}

    private static WallsAndOpenings buildWalls(List<Plan.Room> rooms, double x0, double y0, double w, double dm) {
        TreeMap<Double, List<Interval>> hMap = new TreeMap<>();
        TreeMap<Double, List<Interval>> vMap = new TreeMap<>();
        for (Plan.Room r : rooms) {
            double x1 = r.x();
            double y1 = r.y();
            double x2 = round3(r.x() + r.width());
            double y2 = round3(r.y() + r.depth());
            hMap.computeIfAbsent(y1, k -> new ArrayList<>()).add(new Interval(x1, x2));
            hMap.computeIfAbsent(y2, k -> new ArrayList<>()).add(new Interval(x1, x2));
            vMap.computeIfAbsent(x1, k -> new ArrayList<>()).add(new Interval(y1, y2));
            vMap.computeIfAbsent(x2, k -> new ArrayList<>()).add(new Interval(y1, y2));
        }

        List<Box> boxes = new ArrayList<>();
        boxes.add(new Box(x0, y0, x0 + w, y0 + dm));
        List<Plan.Room> annex = rooms.stream().filter(r -> "annex".equals(r.zone())).toList();
        if (!annex.isEmpty()) {
            double ax1 = annex.stream().mapToDouble(Plan.Room::x).min().orElseThrow();
            double ay1 = annex.stream().mapToDouble(Plan.Room::y).min().orElseThrow();
            double ax2 = annex.stream().mapToDouble(r -> r.x() + r.width()).max().orElseThrow();
            double ay2 = annex.stream().mapToDouble(r -> r.y() + r.depth()).max().orElseThrow();
            boxes.add(new Box(ax1, ay1, ax2, ay2));
        }

        List<Plan.Wall> walls = new ArrayList<>();
        for (var entry : hMap.entrySet()) {
            double y = entry.getKey();
            for (Interval iv : merge(entry.getValue())) {
                boolean ext = isExterior(boxes, true, y, iv.a(), iv.b());
                walls.add(new Plan.Wall("w" + (walls.size() + 1),
                        round3(iv.a()), y, round3(iv.b()), y, ext ? EXT_T : INT_T, ext));
            }
        }
        for (var entry : vMap.entrySet()) {
            double x = entry.getKey();
            for (Interval iv : merge(entry.getValue())) {
                boolean ext = isExterior(boxes, false, x, iv.a(), iv.b());
                walls.add(new Plan.Wall("w" + (walls.size() + 1),
                        x, round3(iv.a()), x, round3(iv.b()), ext ? EXT_T : INT_T, ext));
            }
        }

        List<Plan.Opening> openings = new ArrayList<>();
        Plan.Room corr = rooms.stream().filter(r -> "corridor".equals(r.type())).findFirst().orElseThrow();
        double cxL = corr.x();
        double cxR = round3(corr.x() + corr.width());
        for (Plan.Room r : rooms) {
            double cx = round3(r.x() + r.width() / 2.0);
            double cy = round3(r.y() + r.depth() / 2.0);
            String t = r.type();
            if ("parlour".equals(t)) {
                place(walls, openings, "door", cx, r.y(), true);
                place(walls, openings, "window", round3(cx - 2.2), r.y(), true);
                place(walls, openings, "door", round3(corr.x() + corr.width() / 2.0), round3(r.y() + r.depth()), true);
            } else if ("shop".equals(t)) {
                place(walls, openings, "door", round3(r.x() + 0.9), r.y(), true);
                place(walls, openings, "window", round3(r.x() + 2.5), r.y(), true);
            } else if ("main".equals(r.zone()) && !"corridor".equals(t)) {
                boolean onLeft = Math.abs(round3(r.x() + r.width()) - cxL) < 1e-6;
                place(walls, openings, "door", onLeft ? cxL : cxR, cy, false);
                if (!"store".equals(t)) {
                    place(walls, openings, "window", onLeft ? r.x() : round3(r.x() + r.width()), cy, false);
                }
            } else if ("corridor".equals(t)) {
                place(walls, openings, "door", cx, round3(r.y() + r.depth()), true);
            } else if ("annex".equals(r.zone())) {
                place(walls, openings, "door", cx, r.y(), true);
                if ("bq".equals(t)) {
                    place(walls, openings, "window", r.x(), cy, false);
                }
            }
        }
        return new WallsAndOpenings(walls, openings);
    }

    private static List<Interval> merge(List<Interval> ivs) {
        List<Interval> sorted = ivs.stream()
                .sorted(Comparator.comparingDouble(Interval::a).thenComparingDouble(Interval::b))
                .toList();
        List<Interval> out = new ArrayList<>();
        double curA = sorted.get(0).a();
        double curB = sorted.get(0).b();
        for (int i = 1; i < sorted.size(); i++) {
            Interval next = sorted.get(i);
            if (next.a() <= curB + 1e-6) {
                curB = Math.max(curB, next.b());
            } else {
                out.add(new Interval(curA, curB));
                curA = next.a();
                curB = next.b();
            }
        }
        out.add(new Interval(curA, curB));
        return out;
    }

    private static boolean isExterior(List<Box> boxes, boolean horizontal, double c, double a, double b) {
        double mid = (a + b) / 2.0;
        for (Box box : boxes) {
            if (horizontal && (Math.abs(c - box.y1()) < 1e-6 || Math.abs(c - box.y2()) < 1e-6)
                    && box.x1() - 1e-6 <= mid && mid <= box.x2() + 1e-6) {
                return true;
            }
            if (!horizontal && (Math.abs(c - box.x1()) < 1e-6 || Math.abs(c - box.x2()) < 1e-6)
                    && box.y1() - 1e-6 <= mid && mid <= box.y2() + 1e-6) {
                return true;
            }
        }
        return false;
    }

    private static void place(List<Plan.Wall> walls, List<Plan.Opening> openings,
                              String kind, double px, double py, boolean horizontalHint) {
        for (Plan.Wall w : walls) {
            boolean hor = w.y1() == w.y2();
            if (hor != horizontalHint) {
                continue;
            }
            double off;
            if (hor && Math.abs(py - w.y1()) < 1e-6 && w.x1() - 1e-6 <= px && px <= w.x2() + 1e-6) {
                off = px - w.x1();
            } else if (!hor && Math.abs(px - w.x1()) < 1e-6 && w.y1() - 1e-6 <= py && py <= w.y2() + 1e-6) {
                off = py - w.y1();
            } else {
                continue;
            }
            String id = "o" + (openings.size() + 1);
            if ("door".equals(kind)) {
                openings.add(new Plan.Opening(id, kind, w.id(), round3(off), DOOR_W, DOOR_H, 0.0));
            } else {
                openings.add(new Plan.Opening(id, kind, w.id(), round3(off), WINDOW_W, WINDOW_H, WINDOW_SILL));
            }
            return;
        }
        throw new IllegalStateException("No wall for " + kind + " at " + px + "," + py);
    }

    private static void addRoom(List<Plan.Room> rooms, String type, String label,
                                double x, double y, double w, double d, String zone) {
        rooms.add(new Plan.Room(
                "r" + (rooms.size() + 1),
                type,
                label,
                round3(x),
                round3(y),
                round3(w),
                round3(d),
                round3(w * d),
                zone));
    }

    public static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String fmtNum(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}

