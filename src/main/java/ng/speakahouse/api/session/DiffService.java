package ng.speakahouse.api.session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import ng.speakahouse.api.cost.CostEstimate;
import ng.speakahouse.api.layout.Plan;
import ng.speakahouse.api.spec.HouseSpec;
import org.springframework.stereotype.Service;

@Service
public class DiffService {

    public SessionDtos.VersionDiff diff(
            HouseSpec prevSpec, Plan prevPlan, CostEstimate prevEst,
            HouseSpec curSpec, Plan curPlan, CostEstimate curEst) {
        if (prevSpec == null) {
            return null;
        }
        List<SessionDtos.VersionDiff.Change> changes = new ArrayList<>();
        diffBudget(prevSpec.budget(), curSpec.budget(), changes);
        diffField("/features/bq", prevSpec.features().bq(), curSpec.features().bq(), changes);
        diffField("/features/fence", prevSpec.features().fence(), curSpec.features().fence(), changes);
        diffField("/features/garage", prevSpec.features().garage(), curSpec.features().garage(), changes);
        diffField("/features/shop", prevSpec.features().shop(), curSpec.features().shop(), changes);
        diffField("/finish", prevSpec.finish().name(), curSpec.finish().name(), changes);
        diffField("/houseType", prevSpec.houseType().name(), curSpec.houseType().name(), changes);
        diffField("/plot/depth", prevSpec.plot().depth(), curSpec.plot().depth(), changes);
        diffField("/plot/unit", prevSpec.plot().unit().name(), curSpec.plot().unit().name(), changes);
        diffField("/plot/unitConfirmed", prevSpec.plot().unitConfirmed(), curSpec.plot().unitConfirmed(), changes);
        diffField("/plot/width", prevSpec.plot().width(), curSpec.plot().width(), changes);
        diffField("/region", prevSpec.region().name(), curSpec.region().name(), changes);
        diffField("/roofing", prevSpec.roofing().name(), curSpec.roofing().name(), changes);
        diffField("/rooms/bedrooms", prevSpec.rooms().bedrooms(), curSpec.rooms().bedrooms(), changes);
        diffField("/rooms/dining", prevSpec.rooms().dining(), curSpec.rooms().dining(), changes);
        diffField("/rooms/ensuites", prevSpec.rooms().ensuites(), curSpec.rooms().ensuites(), changes);

        Set<String> prevRooms = prevPlan.rooms().stream().map(Plan.Room::label).collect(Collectors.toSet());
        Set<String> curRooms = curPlan.rooms().stream().map(Plan.Room::label).collect(Collectors.toSet());

        List<String> roomsAdded = curRooms.stream().filter(r -> !prevRooms.contains(r)).sorted().toList();
        List<String> roomsRemoved = prevRooms.stream().filter(r -> !curRooms.contains(r)).sorted().toList();

        Map<String, Long> pg = new LinkedHashMap<>();
        for (CostEstimate.GroupTotal g : prevEst.groups()) pg.put(g.group(), g.amount());
        Map<String, Long> cg = new LinkedHashMap<>();
        for (CostEstimate.GroupTotal g : curEst.groups()) cg.put(g.group(), g.amount());

        Set<String> allGroups = new TreeSet<>();
        allGroups.addAll(pg.keySet());
        allGroups.addAll(cg.keySet());

        List<SessionDtos.VersionDiff.GroupDelta> byGroup = new ArrayList<>();
        for (String g : allGroups) {
            long delta = cg.getOrDefault(g, 0L) - pg.getOrDefault(g, 0L);
            if (delta != 0L) {
                byGroup.add(new SessionDtos.VersionDiff.GroupDelta(g, delta));
            }
        }
        byGroup.sort(Comparator.comparingLong((SessionDtos.VersionDiff.GroupDelta d) -> Math.abs(d.delta())).reversed());

        return new SessionDtos.VersionDiff(
                changes,
                roomsAdded,
                roomsRemoved,
                new SessionDtos.VersionDiff.CostDelta(curEst.expected() - prevEst.expected(), byGroup));
    }

    private static void diffBudget(HouseSpec.BudgetSpec a, HouseSpec.BudgetSpec b,
                                   List<SessionDtos.VersionDiff.Change> out) {
        if (a == null && b == null) return;
        if (a == null || b == null) {
            out.add(new SessionDtos.VersionDiff.Change("/budget", a, b));
            return;
        }
        diffField("/budget/amount", a.amount(), b.amount(), out);
        diffField("/budget/currency", a.currency().name(), b.currency().name(), out);
    }

    private static void diffField(String path, Object a, Object b, List<SessionDtos.VersionDiff.Change> out) {
        if (!Objects.equals(a, b)) {
            out.add(new SessionDtos.VersionDiff.Change(path, a, b));
        }
    }
}

