package com.lab.evaluation24;

import akka.actor.ActorRef;

public class BallMsg {
	
	public static final int COUNTERCLOCKWISE = 0;
	public static final int CLOCKWISE = 1;
	private int direction;
	private ActorRef launcher;
	private ActorRef sender;
	private boolean stashed = false;

	public BallMsg(String direction, ActorRef launcher, ActorRef sender) {
		if (direction.equals("clockwise")){
			this.direction = CLOCKWISE;
		} else {
			this.direction = COUNTERCLOCKWISE;
		}
		this.launcher = launcher;
		this.sender = sender;
	}

	public boolean isStashed() {
		return stashed;
	}

	public void setStashed() {
		this.stashed = true;
	}

	public ActorRef getSender() {
		return sender;
	}

	public void setSender(ActorRef sender) {
		this.sender = sender;
	}

	public ActorRef getLauncher() {
		return launcher;
	}

	public int getDirection() {
		return direction;
	}

}
