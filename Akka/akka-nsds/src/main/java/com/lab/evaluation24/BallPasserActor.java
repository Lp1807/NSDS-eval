package com.lab.evaluation24;

import akka.actor.AbstractActorWithStash;
import akka.actor.ActorRef;
import akka.actor.Props;

public class BallPasserActor extends AbstractActorWithStash {
    private int W;
    private int R;
    private int balls = 0;
    private ActorRef clockwise;
    private ActorRef counterclockwise;
    private boolean launcher = false;
    @Override
    public Receive createReceive() {
        return ActiveMode();
    }

    private void onConfigMsg(ConfigMsg msg) {
        this.W = msg.getW();
        this.R = msg.getR();
        this.clockwise = msg.getClockwise();
        this.counterclockwise = msg.getCounterclockwise();
    }

    private final Receive ActiveMode() {
        return receiveBuilder()
                .match(BallMsg.class, this::onBallActiveMsg)
                .match(ConfigMsg.class, this::onConfigMsg)
                .build();
    }


    private final Receive restMode() {
        return receiveBuilder()
                .match(BallMsg.class, this::onBallRestMsg)
                .build();
    }

    private void onBallRestMsg(BallMsg msg) {
        msg.setStashed();
        stash();
        balls++;
        System.out.println("BALLPASSER_REST: I have " + balls + " balls " + getContext().self().toString());
        // Using greater or equal in the case R is 1
        if (balls >= R) {
            System.out.println("BALLPASSER_REST: going to active mode " +  getContext().self().toString());
            getContext().become(ActiveMode());
            balls = 0;
            unstashAll();
        }
    }

    private void onBallActiveMsg(BallMsg msg) {
        if (msg.getSender() == null) {
            System.out.println("BALLPASSER: I'm the launcher " +  getContext().self().toString());
        } else if (msg.getLauncher().equals(getContext().self())) {
            System.out.println("BALLPASSER: dropping the ball " +  getContext().self().toString());
            return;
        }

        // If he's not the launcher, increment. If the message is stashed, don't increment
        if (!msg.getLauncher().equals(getContext().self()) && !msg.isStashed()) {
            balls++;
            System.out.println("BALLPASSER: I have " + balls + " balls " + getContext().self().toString());
            if (balls > W) {
                System.out.println("BALLPASSER: going to rest mode " +  getContext().self().toString());
                getContext().become(restMode());
                balls = 1;
                msg.setStashed();
                stash();
                return;
            }
        }

        msg.setSender(getSelf());

        if (msg.getDirection() == BallMsg.CLOCKWISE){
            System.out.println("BALLPASSER: passing CLOCKWISE the ball to " +  clockwise.toString());
            clockwise.tell(msg, getSelf());
        } else {
            System.out.println("BALLPASSER: passing COUNTERCLOCKWISE the ball to " +  counterclockwise.toString());
            counterclockwise.tell(msg, getSelf());
        }
    }

    public static Props props() {
        return Props.create(BallPasserActor.class);
    }
}
