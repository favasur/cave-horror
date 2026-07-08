package com.favasur.cavehorror;

public class TimeCounter implements ITimeCounter {
    public int counter = 0;
    public int limit;

    public TimeCounter() {
        this.rollLimit();
    }

    @Override
    public void incrementCounter() {
        this.counter++;
        if (this.counter < 0) {
            this.resetCounter();
        }
    }

    @Override
    public void resetCounter() {
        this.counter = 0;
    }

    @Override
    public void rollLimit() {
    }
}
