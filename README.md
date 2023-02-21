# Gossip

This project is a Distributed System Gossip program that runs on the command line. It can be started without any other servers to connect to, or up to two
ip addresses and ports to connect with upon starting up. Using leaderless replication, this program will communicate with other servers running this program
(or another program similar to this) to monitor the status of each server. It uses a logical clock to see which serves are currently online, and which servers
have crashed or lost connection with the network (and at what point in the logical clock was the server last communicated with). This project was developed
in Java using gRPC and Picocli.
