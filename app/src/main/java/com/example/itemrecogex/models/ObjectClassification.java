package com.example.itemrecogex.models;

public class ObjectClassification implements Comparable<ObjectClassification> {
    private float percentage;
    private String name;

    public ObjectClassification(float percentage, String name) {
        this.percentage = percentage;
        this.name = name;
    }

    public float getPercentage() {
        return percentage;
    }

    public void setPercentage(float percentage) {
        this.percentage = percentage;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return String.format("%.02f", this.percentage) + "% that it's a " + this.name + "\n";
    }

    @Override
    public int compareTo(ObjectClassification objectClassification) {
        return Float.compare(objectClassification.percentage, this.percentage);
    }
}
