package consumer;

import connectionManager.Connection;
import connectionManager.Message;
import connectionManager.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

/**
 *
 * @author H
 */
public class ConsumerProtocol extends Protocol {
  
  String DELIM = " ";
  String DELIM2 = "~";
  int masterId = -1;
  int accumulatorId = -2;
  
  Integer myServerPort;
  String masterIp;
  Integer masterPort;
  
  String accumulatorIp;
  Integer accumulatorPort;
  
  String backupMasterString;
  
  public ConsumerProtocol(int myServerPort, String masterIp, int masterPort) {
    this.myServerPort = myServerPort;
    this.masterIp = masterIp;
    this.masterPort = masterPort;
  }
  
  void sendMessage(int id, String message) {
    try {
      outgoingMessages.put(new Message(id, message));
    } catch (InterruptedException e) {
      System.out.println("Interrupted sending message to"+id+DELIM+message);
    }
  }
  
  @Override
  public boolean processAcceptorMessages(int numConnections, 
                                  BufferedReader incomingStream, 
                                  Socket cSocket) {
    return true;
  }

  //TO DO stuff here
  @Override
  public void processManagerMessages(Message message) {
    String[] msgPieces = message.message.split(DELIM);
    switch (msgPieces[0].charAt(0)) {
      case 'b':
        backupMasterString = message.message;
        break;
      case 'k':
        break;
      case 'a':
        msgPieces = msgPieces[1].split(DELIM2);
        accumulatorIp = msgPieces[0];
        accumulatorPort = Integer.parseInt(msgPieces[1]);
        connectToAccumulator();
        break;
      case 'l':
        msgPieces = msgPieces[1].split(DELIM2);
        masterIp = msgPieces[0];
        masterPort = Integer.parseInt(msgPieces[1]);
        connectToMaster();
        break;
      case 'm':
        //train vector
        break;
      case 'n':
        try { Thread.sleep(10000); } catch (InterruptedException e) {}
        if (!sockets.containsKey(masterId))
          connectToMaster();
        break;
      case 'p':
        break;
      case 'y':
        System.out.println("Connected to accumulator at " 
                + accumulatorIp+DELIM+accumulatorPort);
        break;
      default:
        System.err.println("Received invalid message from clientID: "
                + message.connectedID+DELIM+message.message);
    }
  }

  @Override
  public void handleDisconnection(int connectedID) {
    sockets.remove(connectedID);
    if (connectedID == masterId) {
      handleMasterDisconnection();
    } else if (connectedID == accumulatorId) {
      handleAccumulatorDisconnection();
    } else {
      System.out.println("A producer disconnected");
    }
  }
  
  void handleMasterDisconnection() {
    Boolean resolved = false;
    while (!resolved) {
      String [] disMessagePieces= backupMasterString.split(DELIM, 3);
      if (disMessagePieces.length == 1) {
        System.out.println("No more masters!");
        System.exit(1);
      }
      String nextMasterInfo = disMessagePieces[1];
      String[] nextMaster = nextMasterInfo.split(DELIM2);
      masterIp = nextMaster[0];
      masterPort = Integer.parseInt(nextMaster[1]);
      if (disMessagePieces.length == 3)
        backupMasterString = "m " + disMessagePieces[2];
      else if (disMessagePieces.length == 2)
        backupMasterString = "m";
      connectToMaster();
      if (sockets.containsKey(masterId))
        resolved = true;
    }
  }
  
  void handleAccumulatorDisconnection() {
    System.out.println("Accumulator Disconnected");
    //logic here
  }
  
  @Override
  public void connect() {
    connectToMaster();
  }
  
  public void connectToMaster() {
    System.out.println("Attempting to connect to master");
    Socket masterSocket;
    try {
      masterSocket = new Socket(masterIp, masterPort);
      BufferedReader masterStream = new BufferedReader(
              new InputStreamReader(masterSocket.getInputStream()));
      sockets.put(masterId, masterSocket);
      Connection connection= new Connection(masterId,
                                            isrunning,
                                            incomingMessages,
                                            masterStream,
                                            this);
      connection.start();
      sendMessage(masterId, "c " + myServerPort);
    } catch (IOException ex) {
      System.err.println("Couldn't connect to master!");
    }
  }
  
  public void connectToAccumulator() {
    System.out.println("Attempting to connect to accumulator");
    Socket accSocket;
    try {
      accSocket = new Socket(accumulatorIp, masterPort);
      BufferedReader masterStream = new BufferedReader(
              new InputStreamReader(accSocket.getInputStream()));
      sockets.put(accumulatorId, accSocket);
      Connection connection= new Connection(accumulatorId,
                                            isrunning,
                                            incomingMessages,
                                            masterStream,
                                            this);
      connection.start();
      sendMessage(masterId, "c");
    } catch (IOException ex) {
      System.err.println("Couldn't connect to master!");
    }
  }
  
}