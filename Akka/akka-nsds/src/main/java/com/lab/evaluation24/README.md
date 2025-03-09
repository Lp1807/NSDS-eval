# Evaluation lab - Akka

## Group number: 09

## Group members

- Student 1: Ernesto Natuzzi 
- Student 2: Flavia Nicotri
- Student 3: Luca Pagano

## Description of message flows
The main function in KeepAway generates four BallPasserActor and
configures them through the ConfigMsg class, which contains 
W, R and the next player in the clockwise and 
counterclockwise direction. The BallPasserActor is a state machine,
achived through the Akka Become method: Active and Rest mode.
The default mode is Active.
- **Active mode:** the player passes the ball to the next player
depending on the direction of the ball (attribute direction)
and counts the number balls passed, when he's not the launcher.
Each time he propagates the ball, changes the sender.
If he's the launcher the second time he receives the ball, he
drops it. 
When a player passes a number of balls greater than W,
stashes the message, sets the BallMsg to _stashed_ he goes into Rest mode with the become method, changing
the way the BallMsgs are received. 

- **Rest Mode:** when the player is in Rest mode, he doesn't
propagate the ball messages, stashes them, sets the _stashed_
flag and counts them. When counter reaches R, the player goes back
to Active mode and unstashes all the messages. They will not be counted, and they
will continue the previously stopped propagation.

