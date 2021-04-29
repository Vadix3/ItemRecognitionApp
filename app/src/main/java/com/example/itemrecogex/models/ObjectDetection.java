package com.example.itemrecogex.models;

import java.util.Arrays;

public class ObjectDetection {

    private String name;
    private float score;
    private ObjectLocation location;

    public ObjectDetection(String name, float score, ObjectLocation location) {
        this.name = name;
        this.score = score;
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }


    public ObjectLocation getLocation() {
        return location;
    }

    public void setLocation(ObjectLocation location) {
        this.location = location;
    }

    @Override
    public String toString() {
        return "ObjectDetection{" +
                "name='" + name + '\'' +
                ", score=" + score +
                ", location=" + location +
                "}\n";
    }
}
