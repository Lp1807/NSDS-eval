package com.lab.evaluation24;

import akka.actor.ActorRef;

public class ConfigMsg {

        private int W;
        private int R = 3;
        private ActorRef clockwise;
        private ActorRef counterclockwise;

        public ConfigMsg(ActorRef clockwise, ActorRef counterclockwise, int w, int r) {
                this.clockwise = clockwise;
                this.counterclockwise = counterclockwise;
                this.W = w;
                this.R = r;
        }

        public int getW() {
                return W;
        }

        public int getR() {
                return R;
        }

        public ActorRef getClockwise() {
                return clockwise;
        }

        public ActorRef getCounterclockwise() {
                return counterclockwise;
        }
}
