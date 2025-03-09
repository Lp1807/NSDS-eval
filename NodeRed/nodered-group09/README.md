# Evaluation lab - Node-RED

## Group number: 09

## Group members

- Luca Pagano
- Ernesto Natuzzi
- Flavia Nicotri

## Description of message flows
We implemented three flows:
- One is in charge of taking K from the server and updating it. This flow stores K in a global variable.
    K is initialized to 0 to avoid concurrency errors.
- One is in charge of reading from the mqtt NesLab server the values of temperature in kelvin of sensor.community message. This flow
    keeps in memory with "context" an array of the last ten elements. Each time a new element arrives it appends it and checks if the size
    is less or equal than ten. If it is over 10 it drops the first element. Then each time, computes the average and sets it globally
    with the "global" keyword.
- One is triggered every minute, polls a message from openweather, takes the temperature in kelvin, computes the absolute
    difference between average and this temperature. Then if the difference is greater than K produces a message published 
    on the output topic specified in the slides.
## Extensions 
    None
