/*****************************************************************
 JADE - Java Agent DEvelopment Framework is a framework to develop
 multi-agent systems in compliance with the FIPA specifications.
 Copyright (C) 2000 CSELT S.p.A.

 GNU Lesser General Public License

 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation,
 version 2.1 of the License.


 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General Public
 License along with this library; if not, write to the
 Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 Boston, MA  02111-1307, USA.
 *****************************************************************/

package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * This agent implements a model of a conveyor belt. It knows the following conveyor(s).
 * The agent is capable of driving a package through a network of conveyor belts.
 *
 * @author Luigi Catello, Mario Valentino
 * @version  $Date: 2010-04-08 13:08:55 +0200 (gio, 08 apr 2010) $ $Revision: 6297 $
 */

public class ConveyorAgent extends Agent{
    enum Status{
        Idle,
        Busy,
        Down
    }

    // status of the conveyor
    private Status conveyor_status;
    public Status getConveyor_status() {return conveyor_status;}
    // names of the conveyors following the agent
    private List<String> neighbours = new ArrayList<>();
    // seconds it takes the conveyor to transfer the pallet
    private int transfer_time;
    // whether the pallet is currently loaded
    private boolean pallet_loaded;

    private Logger myLogger = Logger.getMyLogger(getClass().getName());

    private class TransferControlBehaviour extends CyclicBehaviour {

        public TransferControlBehaviour(Agent a) {
            super(a);
        }

        public void action() {
            ACLMessage msg = myAgent.receive();

            if (msg != null){
                ACLMessage reply = msg.createReply();

                if (msg.getPerformative() == ACLMessage.NOT_UNDERSTOOD ||
                         msg.getPerformative() == ACLMessage.AGREE ||
                        (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().contains("Transfer finished")) ||
                         msg.getPerformative() == ACLMessage.FAILURE) {
                    // do nothing
                    // this check is done to prevent an infinite NOT_UNDERSTOOD loop conversation between two agents
                }
                else {
                    if (msg.getPerformative() == ACLMessage.REQUEST) {
                        String content = msg.getContent();
                        try {
                            JSONParser jsonParser = new JSONParser();
                            JSONObject parsedRequest = (JSONObject) jsonParser.parse(content);
                            // Replies with info on the agent
                            if ((content != null) && ((parsedRequest.get("request_type"))).equals("get_info")) {
                                JSONObject replyObject = new JSONObject();
                                replyObject.put("Neighbours", neighbours);
                                replyObject.put("TransferTime", transfer_time);
                                replyObject.put("PalletLoaded", pallet_loaded);
                                replyObject.put("Status", conveyor_status);

                                reply.setPerformative(ACLMessage.INFORM);
                                reply.setContent(replyObject.toString());
                                send(reply);
                            }
                            // Load the pallet on the conveyor
                            else if ((content != null) && ((parsedRequest.get("request_type"))).equals("load")) {
                                if (conveyor_status == Status.Busy || pallet_loaded) {
                                    reply.setPerformative(ACLMessage.FAILURE);
                                    reply.setContent("Conveyor busy, cannot load!");
                                    send(reply);
                                    myLogger.log(Logger.WARNING, "Agent " + getLocalName() + " - Busy, cannot load");
                                }
                                else if (conveyor_status == Status.Down) {
                                    reply.setPerformative(ACLMessage.FAILURE);
                                    reply.setContent("Conveyor down, cannot load!");
                                    send(reply);
                                    myLogger.log(Logger.WARNING, "Agent " + getLocalName() + " - Down, cannot load");
                                }
                                else {
                                    // Load the pallet, update the status
                                    conveyor_status = Status.Busy;
                                    pallet_loaded = true;
                                    reply.setPerformative(ACLMessage.AGREE);
                                    reply.setContent("Pallet loaded");
                                    send(reply);
                                    myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Pallet loaded");
                                }
                            }
                            // Unload the pallet from the conveyor
                            else if ((content != null) && ((parsedRequest.get("request_type"))).equals("unload")) {
                                if (pallet_loaded && conveyor_status != Status.Down) {
                                    reply.setPerformative(ACLMessage.AGREE);
                                    reply.setContent("Pallet unloaded");
                                    send(reply);
                                    conveyor_status = Status.Idle;
                                    pallet_loaded = false;
                                    myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Pallet unloaded");
                                }
                                else {
                                    reply.setPerformative(ACLMessage.FAILURE);
                                    reply.setContent("Pallet not unloaded");
                                    send(reply);
                                    myLogger.log(Logger.WARNING, "Agent " + getLocalName() + " - Pallet not unloaded");
                                }
                            }
                            // Set the status of the conveyor
                            else if ((content != null) && ((parsedRequest.get("request_type"))).equals("set_status")) {
                                if (parsedRequest.get("status").equals("Idle")) {
                                    reply.setPerformative(ACLMessage.AGREE);
                                    reply.setContent("Setting status to Idle");
                                    send(reply);
                                    conveyor_status = Status.Idle;
                                }
                                else if (parsedRequest.get("status").equals("Busy")) {
                                    reply.setPerformative(ACLMessage.AGREE);
                                    reply.setContent("Setting status to Busy");
                                    send(reply);
                                    conveyor_status = Status.Busy;
                                }
                                else if (parsedRequest.get("status").equals("Down")) {
                                    reply.setPerformative(ACLMessage.AGREE);
                                    reply.setContent("Setting status to Down\nBravo six, going dark");
                                    send(reply);
                                    conveyor_status = Status.Down;
                                }
                                else {
                                    reply.setPerformative(ACLMessage.FAILURE);
                                    reply.setContent("Selected status is not valid");
                                    send(reply);
                                }
                            }
                            // Transfer a pallet knowing the route in advance
                            // The request must contain the ordered array of the via points
                            else if ((content != null) && ((parsedRequest.get("request_type"))).equals("routed_transfer")) {
                                // the transfer can only occur if the pallet is loaded on the cnv
                                if (pallet_loaded) {
                                    JSONArray route = (JSONArray) parsedRequest.get("viaPoints");
                                    myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Received route: " + route);
                                    // Finds itself in the route and sends the pallet to the next one
                                    for (int i = 0; i < route.size(); i++) {
                                        if (((String) route.get(i)).equals(myAgent.getLocalName())) {
                                            // If I am the last -> I don't have to transfer
                                            if (i == (route.size() - 1)) {
                                                myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Transfer finished");
                                                // Inform the first conveyor that the transfer has finished.
                                                ACLMessage transferFinishedMessage = new ACLMessage(ACLMessage.INFORM);
                                                transferFinishedMessage.addReceiver(new AID((String) route.get(0), AID.ISLOCALNAME));
                                                transferFinishedMessage.setContent("Transfer finished");
                                                send(transferFinishedMessage);
                                            }
                                            else {
                                                myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Transfer continuing");
                                                // Transfer to the next conveyor (after waiting for transfer time)
                                                // get next cnv
                                                String nextCnv = (String) route.get(i + 1);
                                                // while waiting for the transfer_time to pass, the cnv is busy
                                                conveyor_status = Status.Busy;
                                                int finalI = i; // Java :)

                                                addBehaviour(new WakerBehaviour(myAgent, transfer_time*1000L) {
                                                    protected void handleElapsedTimeout() {
                                                        // Simulation: pallet unloaded and loaded on the following conveyor
                                                        ACLMessage loadNextConveyor = new ACLMessage(ACLMessage.REQUEST);
                                                        JSONObject loadMessage = new JSONObject();
                                                        loadMessage.put("request_type", "load");
                                                        loadNextConveyor.setContent(loadMessage.toString());
                                                        // check that next conveyor in the route is actually a neighbour
                                                        if (!neighbours.contains((String) route.get(finalI +1))){
                                                            myLogger.log(Logger.WARNING, "Agent " + getLocalName() + " - Next conveyor is not a neighbour");
                                                            reply.setPerformative(ACLMessage.FAILURE);
                                                            reply.setContent("Next conveyor is not a neighbour");
                                                            send(reply);
                                                        }
                                                        else {
                                                            loadNextConveyor.addReceiver(new AID((String) route.get(finalI + 1), AID.ISLOCALNAME));
                                                            send(loadNextConveyor);
                                                            myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Transferring the pallet to " + route.get(finalI + 1));

                                                            // if the next cnv loads the pallet, send the transfer instruction
                                                            long replyTimeoutMs = 1000L;

                                                            // checking if the transfer was successful
                                                            // message template for the reply
                                                            MessageTemplate.MatchExpression agreeMessage = aclMessage ->
                                                                    (aclMessage.getPerformative() == ACLMessage.AGREE) || (aclMessage.getPerformative() == ACLMessage.REFUSE);  // lambda expression D:
                                                            // wait for an AGREE or REFUSE for replyTimeoutMs
                                                            ACLMessage replyToLoad = myAgent.blockingReceive(new MessageTemplate(agreeMessage), replyTimeoutMs);
                                                            // if the load was successful
                                                            if ((replyToLoad != null) && (replyToLoad.getPerformative() == ACLMessage.AGREE)) {
                                                                conveyor_status = Status.Idle;
                                                                pallet_loaded = false;
                                                                //After sending the pallet, send the route to the next conveyor
                                                                ACLMessage truthSpreader = new ACLMessage(ACLMessage.REQUEST);
                                                                truthSpreader.addReceiver(new AID((String) route.get(finalI + 1), AID.ISLOCALNAME));
                                                                truthSpreader.setContent(msg.getContent());
                                                                myAgent.send(truthSpreader);
                                                                myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Spreading the truth...");
                                                            }
                                                            else {
                                                                // load refused, try again three times
                                                                boolean pallet_sent = false;
                                                                for(int i=0; i<2;i++){
                                                                    doWait(5000L);
                                                                    send(loadNextConveyor);
                                                                    MessageTemplate.MatchExpression agreeMessage2 = aclMessage ->
                                                                            (aclMessage.getPerformative() == ACLMessage.AGREE) || (aclMessage.getPerformative() == ACLMessage.REFUSE);  // lambda expression D:
                                                                    ACLMessage replyToLoad2 = myAgent.blockingReceive(new MessageTemplate(agreeMessage2), 2000L);
                                                                   if ((replyToLoad2 != null) && (replyToLoad2.getPerformative() == ACLMessage.AGREE)) {
                                                                       conveyor_status = Status.Idle;
                                                                       pallet_loaded = false;
                                                                       //After sending the pallet, send the route to the next conveyor
                                                                       ACLMessage truthSpreader = new ACLMessage(ACLMessage.REQUEST);
                                                                       truthSpreader.addReceiver(new AID((String) route.get(finalI + 1), AID.ISLOCALNAME));
                                                                       truthSpreader.setContent(msg.getContent());
                                                                       myAgent.send(truthSpreader);
                                                                       myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Spreading the truth...");
                                                                       pallet_sent = true;
                                                                       break;
                                                                   }
                                                                }
                                                                if(!pallet_sent) {
                                                                    // if we have rerouting permission, try to find a new route
                                                                    if ((parsedRequest.get("reroute") != null) && (parsedRequest.get("reroute").equals("true"))) {
                                                                        JSONObject newTransferRequestObj = new JSONObject();
                                                                        newTransferRequestObj.put("request_type", "transfer");
                                                                        newTransferRequestObj.put("source", myAgent.getLocalName());
                                                                        newTransferRequestObj.put("destination", parsedRequest.get("destination"));
                                                                        ACLMessage newTransferRequestMsg = new ACLMessage(ACLMessage.REQUEST);
                                                                        newTransferRequestMsg.setContent(newTransferRequestObj.toString());
                                                                        newTransferRequestMsg.addReceiver(myAgent.getAID());
                                                                        send(newTransferRequestMsg);
                                                                        myLogger.log(Logger.WARNING, "Agent " + getLocalName() + " - Trying to reroute...");

                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                });
                                                break;
                                            }
                                        }
                                    }
                                } else {
                                    reply.setPerformative(ACLMessage.FAILURE);
                                    reply.setContent("Cannot proceed, pallet not loaded");
                                    myLogger.log(Logger.WARNING, "Agent " + getLocalName() + " - Cannot proceed, pallet not loaded");
                                    send(reply);
                                }
                            }
                            //Transfer the pallet via the best path
                            else if ((content != null) && ((parsedRequest.get("request_type"))).equals("transfer")) {
                                if (pallet_loaded) {
                                    // add viaPoints array to parsedRequest
                                    JSONArray viaPoints = new JSONArray();
                                    parsedRequest.put("viaPoints", viaPoints);
                                    // call BestPath and transfer automatically after finding the route
                                    addBehaviour(new BestPath(myAgent, parsedRequest, myAgent.getAID(), true, true));
                                } else {
                                    reply.setPerformative(ACLMessage.FAILURE);
                                    reply.setContent("Cannot proceed, pallet not loaded");
                                    myLogger.log(Logger.WARNING, "Agent " + getLocalName() + " - Cannot proceed, pallet not loaded");
                                    send(reply);
                                }
                            }
                            else {
                                reply.setPerformative(ACLMessage.FAILURE);
                                reply.setContent("Could not understand the request");
                                send(reply);
                            }
                        } catch (ParseException e) {
                            myLogger.log(Logger.INFO, "Agent " + getLocalName() + " Request not understood - received from " + msg.getSender().getLocalName());
                            reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                            reply.setContent("Request not understood");
                            //e.printStackTrace();
                            send(reply);
                        }

                    }
                    else if ((msg.getPerformative() == ACLMessage.CFP) || (msg.getPerformative() == ACLMessage.PROPAGATE)) {
                        try {
                            String content = msg.getContent();
                            JSONParser jsonParser = new JSONParser();
                            JSONObject jsonObject = (JSONObject) jsonParser.parse(content);

                            addBehaviour(new BestPath(myAgent, jsonObject, msg.getSender(),(msg.getPerformative() == ACLMessage.CFP)));
                            myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Received path finding request: " + jsonObject.toString());
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }

                    }
                    else {
//                        ACLMessage reply = new ACLMessage();
                        //myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Unexpected message [" + ACLMessage.getPerformative(msg.getPerformative()) + "] received from "+msg.getSender().getLocalName());
                        reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                        reply.setContent("( (Unexpected-act " + ACLMessage.getPerformative(msg.getPerformative()) + ") )");
                        send(reply);
                    }
                }
            }
            else {
                block();
            }
        }
    }


    protected void setup() {
        // Registration with the DF
        DFAgentDescription dfd = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("ConveyorAgent");
        sd.setName(getName());
        sd.setOwnership("Group 15");
        dfd.setName(getAID());
        dfd.addServices(sd);
        try {
            DFService.register(this,dfd);

            TransferControlBehaviour ConveyorBehaviour = new  TransferControlBehaviour(this);
            addBehaviour(ConveyorBehaviour);

        } catch (FIPAException e) {
            myLogger.log(Logger.SEVERE, "Agent " + getLocalName()+" - Cannot register with DF", e);
            doDelete();
        }

        // agent starts as idling
        conveyor_status = Status.Idle;
        pallet_loaded = false;
        // getting the neighbours and the transfer time from the args
        Object[] args = getArguments();
        if (args != null && args.length > 0) {

            for(int i = 0; i< ((Object[])args[0]).length; i++){
                neighbours.add( ((Object[])args[0])[i].toString());
            }
            transfer_time = (int) ((Object[])args[1])[0];
        }
    }



    // -------------------------------------------------------------------------------------------------- //


    // behaviour to ask look for the best path
    private class BestPath extends Behaviour {
        JSONObject msg; //
        AID sender = null;
        private boolean isDone = false,
                             SOC = false;
        private boolean transferAfterFindingRoute = false;
        public BestPath(Agent a, JSONObject s) {
            super(a);
            msg = s;
        }

        public BestPath(Agent a, JSONObject s, boolean SOC) {
            this(a,s);
            this.SOC = SOC;
        }

        public BestPath(Agent a, JSONObject s, AID sender, boolean SOC) {
            this(a,s,SOC);
            this.sender = sender;
        }

        public BestPath(Agent a, JSONObject s, AID sender, boolean SOC, boolean transferAfterFindingRoute) {
            this(a,s,sender,SOC);
            this.transferAfterFindingRoute = transferAfterFindingRoute;
        }

        boolean timeoutElapsed = false;

        public void action() {
            //Check the format of the message
            if((msg.get("source") == null) || (msg.get("destination") == null) || (msg.get("viaPoints") == null)) {
                myLogger.log(Logger.WARNING,myAgent.getLocalName()+" - Wrong format");
                isDone = true;
                return;
            }


            // I am source
            if (SOC) {
                if (msg.get("source").equals(myAgent.getLocalName())) {
                    // if the viaPoints array is empty, we are done
                    // start timer
                    long timeoutMs = 1000L;
                    timeoutElapsed = false;

//                WakerBehaviour wakerBehaviour = new WakerBehaviour(myAgent, timeoutMs) {
//                    @Override
//                    protected void handleElapsedTimeout() {
//                        timeoutElapsed = true;
//                        myLogger.log(Logger.INFO, myAgent.getLocalName() + " - Timeout elapsed, it's time to take a look at the answers!");
//                    }
//                };
//                addBehaviour(wakerBehaviour);
//                wakerBehaviour.action();

                    // propagate message to neighbours
                    // add myself to viaPoints array
                    ((JSONArray) msg.get("viaPoints")).add(myAgent.getLocalName());
                    ACLMessage propagateMsg = new ACLMessage(ACLMessage.PROPAGATE);
                    // add receivers to message
                    for (String n : neighbours) {
                        propagateMsg.addReceiver(new AID(n, AID.ISLOCALNAME));
                    }
                    // set the content
                    propagateMsg.setContent(msg.toString());
                    // send the message
                    myAgent.send(propagateMsg);

                    // wait for the messages and put them in a list
                    List<JSONObject> messages = new ArrayList<>();

                    conveyor_status = Status.Busy;
                    long wakeUpTime = System.currentTimeMillis() + timeoutMs;


                    myLogger.log(Logger.INFO, myAgent.getLocalName() + " - Polling paths...");
                    // receiving answers about paths
//                while (!timeoutElapsed) {
                    while (System.currentTimeMillis() < wakeUpTime) {
                        MessageTemplate.MatchExpression recvMessage = aclMessage -> (aclMessage.getPerformative() == ACLMessage.INFORM);  // lambda expression D:

                        ACLMessage rec = myAgent.receive(new MessageTemplate(recvMessage));
                        if (rec != null && rec.getPerformative() == ACLMessage.INFORM) {
                            try {
                                JSONParser jsonParser = new JSONParser();
                                messages.add((JSONObject) jsonParser.parse(rec.getContent()));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }
                        }

                    }
                    // if no replies
                    if (messages.isEmpty()) {
                        if(sender != null) {
                            ACLMessage noPathMsg = new ACLMessage(ACLMessage.FAILURE);
                            noPathMsg.setContent("Failed to find a path for the request\n" + msg);
                            noPathMsg.addReceiver(sender);
                            myAgent.send(noPathMsg);
                        }
                        myLogger.log(Logger.WARNING, myAgent.getLocalName() + " - Received no replies while looking for path from " + msg.get("source") + " to " + msg.get("destination"));
                    }
                    else {
                        // compare all the possible path
                        JSONObject bestPath = (JSONObject) messages.get(0);
                        int minLength = ((JSONArray) messages.get(0).get("viaPoints")).size();
                        for (JSONObject path : messages) {
                            int pathLength = ((JSONArray) path.get("viaPoints")).size();
                            if (pathLength < minLength) {
                                bestPath = path;
                                minLength = pathLength;
                            }
                        }
                        // best path found
                        if (sender != null) {
                            ACLMessage pathFound = new ACLMessage();
                            pathFound.addReceiver(sender);
                            if (transferAfterFindingRoute) {
                                pathFound.setPerformative(ACLMessage.REQUEST);
                                bestPath.replace("request_type", "routed_transfer");
                                bestPath.put("reroute","true");
                            }
                            else {
                                pathFound.setPerformative(ACLMessage.INFORM);
                                bestPath.remove("request_type");
                            }
                            pathFound.setContent(bestPath.toString());
                            send(pathFound);
                        }
                        myLogger.log(Logger.INFO, myAgent.getLocalName() + " - found best path (with lenght " + minLength + "): " + bestPath.toString());
                    }
                    conveyor_status = Status.Idle;
                    isDone = true;
                }
                // SOC was sent to a CNV that is not the source
                else {
                    ACLMessage forward = new ACLMessage(ACLMessage.CFP);
                    forward.setContent(msg.toString());
                    forward.addReceiver(new AID(msg.get("source").toString(), AID.ISLOCALNAME));
                    send(forward);
                    myLogger.log(Logger.WARNING, myAgent.getLocalName() + " - The PathFinding request was sent to the wrong agent - Rerouting request ...");
                    isDone = true;
                }
            }
//            // I am the source but !SOC -> pathfinder is stuck in a loop
//            else if (myAgent.getLocalName().equals(msg.get("source"))) {
//                // we can end
//                isDone = true;
//            }
            // I am destination
            else if ((myAgent.getLocalName()).equals(msg.get("destination"))) {
                //Propagate only if the conveyor is idle, if not the path is not valid
                if (((ConveyorAgent) myAgent).getConveyor_status() == Status.Idle) {
                    // destination cnv is added to viaPoints
                    ((JSONArray) msg.get("viaPoints")).add(myAgent.getLocalName());
                    // send full list to source
                    // create message
                    ACLMessage fullList = new ACLMessage(ACLMessage.INFORM);
                    // find target
                    AID targetAID = new AID((String) msg.get("source"), AID.ISLOCALNAME);
                    // add receiver to message
                    fullList.addReceiver(targetAID);
                    // set the content
                    fullList.setContent(msg.toString());
                    // send the message
                    myAgent.send(fullList);
                    // log
                    myLogger.log(Logger.INFO, myAgent.getLocalName() + " - I am the destination. Sending the full list to source.");
                    // done
                    isDone = true;
                }
            }
            // I am part of the path
            else {
                //Propagate only if the conveyor is idle, if not the path is not valid
                if (((ConveyorAgent) myAgent).getConveyor_status() == Status.Idle) {
                    // let's check if the CNV is already in the viaPoints array
                    if (((JSONArray) msg.get("viaPoints")).contains(myAgent.getLocalName())) {
                        // already in the array -> pathfinder is stuck in a loop
                        isDone = true;
                        return;
                    }
                    // add myself to viaPoints array
                    ((JSONArray) msg.get("viaPoints")).add(myAgent.getLocalName());
                    // propagate the message
                    ACLMessage propagateMsg = new ACLMessage(ACLMessage.PROPAGATE);
                    // add receivers to message
                    for (String n : neighbours) {
                        propagateMsg.addReceiver(new AID(n, AID.ISLOCALNAME));
                    }
                    // set the content
                    propagateMsg.setContent(msg.toString());
                    // send the message
                    myAgent.send(propagateMsg);
                    // log
                    myLogger.log(Logger.INFO, myAgent.getLocalName() + " - Adding myself to the list and propagating the message.");
                    // done
                    isDone = true;
                }
            }
        }




        public boolean done() {
            return isDone;
        }
    }
}