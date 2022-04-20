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
import jade.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    private Status conveyor_status;
    private List<String> neighbours = new ArrayList<>();
    private int transfer_time;
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

                if (msg.getPerformative() == ACLMessage.NOT_UNDERSTOOD || msg.getPerformative() == ACLMessage.AGREE ||
                        (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().contains("Transfer finished"))
                    || msg.getPerformative() == ACLMessage.FAILURE) {
                    // do nothing
                } else {

                    if (msg.getPerformative() == ACLMessage.REQUEST) {
                        String content = msg.getContent();
                        try {
                            JSONParser jsonParser = new JSONParser();
                            JSONObject parsedRequest = (JSONObject) jsonParser.parse(content);
                            //myLogger.log(Logger.INFO,"Ciao" + parsedRequest.get("request_type"));
                            //Replies with its neighbours
                            if ((content != null) && ((parsedRequest.get("request_type"))).equals("get_info")) {
                                JSONObject replyObject = new JSONObject();
                                replyObject.put("Neighbours", neighbours);
                                replyObject.put("TransferTime", transfer_time);
                                replyObject.put("PalletLoaded", pallet_loaded);

                                reply.setPerformative(ACLMessage.INFORM);
                                reply.setContent(replyObject.toString());
                                send(reply);
                            }
                            //Load the pallet on the conveyor
                            else if ((content != null) && ((parsedRequest.get("request_type"))).equals("load")) {
                                if (conveyor_status == Status.Busy || pallet_loaded) {
                                    reply.setPerformative(ACLMessage.FAILURE);
                                    reply.setContent("Conveyor busy, cannot load!");
                                    send(reply);
                                    myLogger.log(Logger.WARNING, "Agent " + getLocalName() + " - Busy, cannot load");
                                } else if (conveyor_status == Status.Down) {
                                    reply.setPerformative(ACLMessage.FAILURE);
                                    reply.setContent("Conveyor down, cannot load!");
                                    send(reply);
                                    myLogger.log(Logger.WARNING, "Agent " + getLocalName() + " - Down, cannot load");
                                } else {
                                    //Load the pallet, update the status
                                    conveyor_status = Status.Busy;
                                    pallet_loaded = true;
                                    reply.setPerformative(ACLMessage.AGREE);

                                    reply.setContent("Pallet loaded");
                                    send(reply);
                                    myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Pallet loaded");
                                }
                            }
                            //Transfer the pallet to the next conveyor ( Task 1 only)
                            //The request must contain the ordered array of the via points and the transfer times
                            else if ((content != null) && ((parsedRequest.get("request_type"))).equals("transfer")) {
                                if (pallet_loaded) {
                                    JSONArray route = (JSONArray) parsedRequest.get("viaPoints");
                                    myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Received route: " + route);
                                    //Finds himself in the route and send the pallet to the next one
                                    for (int i = 0; i < route.size(); i++) {
                                        if (((String) route.get(i)).equals(myAgent.getLocalName())) {
                                            //If I am the last, I don't have to transfer
                                            if (i == (route.size() - 1)) {
                                                myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Transfer finished");
                                                //Inform the first conveyor that the transfer has finished.
                                                ACLMessage transferFinishedMessage = new ACLMessage(ACLMessage.INFORM);
                                                transferFinishedMessage.addReceiver(new AID((String) route.get(0), AID.ISLOCALNAME));
                                                transferFinishedMessage.setContent("Transfer Finished");
//                                                send(transferFinishedMessage);
                                                //break;
                                            } else {
                                                myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Transfer continuing");
                                                //Transfer to the next conveyor (wait)
                                                // get next cnv
                                                String nextCnv = (String) route.get(i + 1);
                                                // transfer after transfer_time
                                                conveyor_status = Status.Busy;
                                                int finalI = i; //Java :)
                                                addBehaviour(new WakerBehaviour(myAgent, transfer_time*1000L) {
                                                    protected void handleElapsedTimeout() {
                                                        //Simulation: pallet unloaded and loaded on the following conveyor
                                                        conveyor_status = Status.Idle;
                                                        pallet_loaded = false;
                                                        ACLMessage loadNextConveyor = new ACLMessage(ACLMessage.REQUEST);
                                                        JSONObject loadMessage = new JSONObject();
                                                        loadMessage.put("request_type", "load");
                                                        loadNextConveyor.setContent(loadMessage.toString());
//                                                    loadNextConveyor.addReceiver(new AID((String) route.get(finalI+1)));
                                                        loadNextConveyor.addReceiver(new AID((String) route.get(finalI + 1), AID.ISLOCALNAME));
                                                        myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Transferring the pallet to " + route.get(finalI + 1));
                                                        myAgent.send(loadNextConveyor);

                                                        //After sending the pallet, send the route to the next conveyor
                                                        ACLMessage truthSpreader = new ACLMessage(ACLMessage.REQUEST);
                                                        truthSpreader.addReceiver(new AID((String) route.get(finalI + 1), AID.ISLOCALNAME));
                                                        truthSpreader.setContent(msg.getContent());
                                                        myAgent.send(truthSpreader);

//                                                        reply.addReceiver(new AID((String) route.get(finalI + 1),AID.ISLOCALNAME));
//                                                        reply.setPerformative(ACLMessage.REQUEST);
//                                                        reply.setContent(msg.getContent());
                                                        myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Spreading the truth...");
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
                            } else {
                                reply.setPerformative(ACLMessage.FAILURE);
                                reply.setContent("Could not understand the request");
                                send(reply);
                            }
                        } catch (ParseException e) {
                            myLogger.log(Logger.INFO, "Agent " + getLocalName() + " Request not understood -  received from " + msg.getSender().getLocalName());
                            reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                            reply.setContent("Request not understood");
                            e.printStackTrace();
                            send(reply);
                        }

                    } else if (msg.getPerformative() == ACLMessage.CFP) {
                        try {
                            String content = msg.getContent();
                            JSONParser jsonParser = new JSONParser();
                            JSONObject jsonObject = (JSONObject) jsonParser.parse(content);

                            addBehaviour(new BestPath(myAgent, jsonObject, true));
                            myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Received call for proposal: " + jsonObject.toString());
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }

                    } else {
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


    // behaviour to ask look for the best path
    private class BestPath extends Behaviour {
        JSONObject msg; //
        private boolean isDone = false,
                             SOC = false;

        public BestPath(Agent a, JSONObject s) {
            super(a);
            msg = s;
        }

        public BestPath(Agent a, JSONObject s, boolean SOC) {
            this(a,s);
            this.SOC = SOC;
        }

        boolean timeoutElapsed = false;
//        private void setTimeoutElapsed(boolean value) {
//            timeoutElapsed = value;
//        }

        public void action() {
            //Start of Communication, I am the source

            // I am source //basta SOC per sapere che sono la source
            if (SOC) {
                // if the viaPoints array is empty, we are done
                // start timer
                long timeout = 3000;
//                setTimeoutElapsed(false);
                timeoutElapsed = false;
                addBehaviour(new WakerBehaviour(myAgent, timeout) {
//                    @Override
//                    protected void onWake() {
//                        setTimeoutElapsed(true);
//                    }
                    @Override
                    protected void handleElapsedTimeout() {
                        timeoutElapsed = true;
                    }
                });

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
                while (!timeoutElapsed) {
                    ACLMessage rec = myAgent.receive();
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
                    ACLMessage noPathMsg = new ACLMessage(ACLMessage.FAILURE);
                    // TODO capire a chi mandare il messaggio di errore
                    noPathMsg.setContent("Failed to find a path for the request\n" + msg);
                    myAgent.send(noPathMsg);
                } else {
                    // compare all the possible path
                    JSONObject bestPath = new JSONObject();
                    int minLength = ((JSONArray) messages.get(0).get("midPoints")).size();
                    for (JSONObject path : messages) {
                        int pathLength = ((JSONArray) path.get("midPoints")).size();
                        if (pathLength < minLength) {
                            bestPath = path;
                            minLength = pathLength;
                        }
                    }
                    // best path found
                    myLogger.log(Logger.INFO, myAgent.getLocalName() + " - found best path: " + bestPath.toString());
                }
                conveyor_status = Status.Idle;
            }
            // I am destination
            else if (myAgent.getLocalName() == msg.get("destination")) {
                // send full list to source
                // create message
                ACLMessage fullList = new ACLMessage(ACLMessage.INFORM);    //TODO: ascoltare inform
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

            else {
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


        public boolean done() {
            return isDone;
        }
    }
}