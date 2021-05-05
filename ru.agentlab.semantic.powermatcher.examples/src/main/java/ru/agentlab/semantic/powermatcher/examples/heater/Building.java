package ru.agentlab.semantic.powermatcher.examples.heater;

import java.util.Objects;

public class Building {
    private final double length;
    private final double width;
    private final double height;

    public Building(double length, double width, double height) {
        this.length = length;
        this.width = width;
        this.height = height;
    }

    public double getLength() {
        return length;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    @Override
    public String toString() {
        return "Building{" +
                "length=" + length +
                ", width=" + width +
                ", height=" + height +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Building building = (Building) o;
        return Double.compare(building.length, length) == 0 && Double.compare(
                building.width,
                width
        ) == 0 && Double.compare(building.height, height) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(length, width, height);
    }
}
