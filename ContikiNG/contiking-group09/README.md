# Evaluation lab - Contiki-NG

## Group number: 09

## Group members

- Flavia Nicotri
- Ernesto Natuzzi
- Luca Pagano

## Solution description
We implemented two agents:

- Client: the client has a periodic timer of 1 minute in which it loads the parent ip address and sends it
   to the root.
  
- Server: the server has a data structure composed in the following way:
   - nodes: which is an array of MAX_RECEIVERS which is the list of the monitored clients
   - parents: an array of length MAX_RECEIVERS which contains the parent of client i
   - num_missing: how many consecutive minutes the client i hasn't sent a message to server
   - message_arrived: a boolean array which contains a boolean that it is true if the client i has sent a message in the the last minute
   - is_alive: a reduntant array that is used only for clarity of the code. If client i is alive (has sent a message in the last three minutes) then the ith element is true
  
    The server updates a structure each time a client sends a message. Every minute, it checks for clients that haven't sent updates, incrementing num_missing. If num_missing[i] reaches 3, it sets is_alive[i] to false. The server can handle a maximum number of clients; if a client "dies," its index is replaced by a new arriving client not yet monitored.



