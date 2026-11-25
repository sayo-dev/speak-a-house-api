package ng.speakahouse.api.layout;

import java.util.List;

public record Plan(
        String units,
        double wallHeight,
        Dimensions plot,
        Setbacks setbacks,
        Rect buildable,
        Rect footprint,
        List<Room> rooms,
        List<Wall> walls,
        List<Opening> openings,
        Fence fence,
        String roofing,
        String frontEdge) {

    public record Dimensions(double width, double depth) {}

    public record Setbacks(double front, double rear, double side) {}

    public record Rect(double x, double y, double width, double depth) {}

    public record Room(
            String id,
            String type,
            String label,
            double x,
            double y,
            double width,
            double depth,
            double area,
            String zone) {}

    public record Wall(
            String id,
            double x1,
            double y1,
            double x2,
            double y2,
            double thickness,
            boolean exterior) {}

    public record Opening(
            String id,
            String type,
            String wallId,
            double offset,
            double width,
            double height,
            double sill) {}

    public record Fence(
            double x,
            double y,
            double width,
            double depth,
            double gateOffset,
            double gateWidth) {}
}

