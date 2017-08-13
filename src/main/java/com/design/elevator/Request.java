package com.design.elevator;

public class Request {
    public long time;
    public Integer floor;
    public Elevator.Direction direction;

    public Request(long time, Integer floor, Elevator.Direction direction) {
        this.time = time;
        this.floor = floor;
        this.direction = direction;
    }
}