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

    private Logger myLogger = Logger.getMyLogger(getClass().getName());

    private class TransferControlBehaviour extends CyclicBehaviour {

        public TransferControlBehaviour(Agent a) {
            super(a);
        }

        public void action() {
            ACLMessage msg = myAgent.receive();
            JSONParser parser = new JSONParser();

            if (msg != null){
                ACLMessage reply = msg.createReply();

                if (msg.getPerformative() == ACLMessage.REQUEST){
                    String content = msg.getContent();
                    //Replies with its neighbours
                    if ((content != null) && (content.contains("get_neighbours"))) {
                        JSONObject replyObject = new JSONObject();
                        replyObject.put("Neighbours", neighbours);
                        myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Neighbours: " + replyObject.toString());

                        reply.setPerformative(ACLMessage.INFORM);
                        reply.setContent(replyObject.toString());
                    }
                    //Load the pallet on the conveyor
                    else if((content!= null) && content.contains("load")){
                        if(conveyor_status == Status.Busy){
                            reply.setPerformative(ACLMessage.FAILURE);
                            reply.setContent("Conveyor busy, cannot load!");

                            myLogger.log(Logger.WARNING, "Agent " + getLocalName() + " - Busy, cannot load");
                        }
                        else if(conveyor_status == Status.Down){
                            reply.setPerformative(ACLMessage.FAILURE);
                            reply.setContent("Conveyor down, cannot load!");

                            myLogger.log(Logger.WARNING, "Agent " + getLocalName() + " - Down, cannot load");
                        }
                        else{
                            //Load the pallet, update the status
                            conveyor_status = Status.Busy;
                            reply.setPerformative(ACLMessage.AGREE);
                            reply.setContent("Pallet loaded");

                            myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Pallet loaded");
                        }
                    }
                    //Transfer the pallet to the next conveyor ( Task 1 only)
                    else if((content!= null) && content.contains("transfer_next")){

                    }
                }
                else if(msg.getPerformative() == ACLMessage.CFP){
                    try {
                        String content = msg.getContent();
                        JSONParser jsonParser = new JSONParser();
                        JSONObject jsonObject = (JSONObject) jsonParser.parse(content);

                        addBehaviour(new BestPath(jsonObject));
                        myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Received call for proposal: " + jsonObject.toString());
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                }
                else {
                    myLogger.log(Logger.INFO, "Agent " + getLocalName() + " - Unexpected message [" + ACLMessage.getPerformative(msg.getPerformative()) + "] received from "+msg.getSender().getLocalName());
                    reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                    reply.setContent("( (Unexpected-act " + ACLMessage.getPerformative(msg.getPerformative()) + ") )");
                }
                send(reply);
            }   // if message != null
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

        // getting the neighbours from the args
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            for (Object n : args) {
                neighbours.add((String) n);
            }
        }
    }


    // behaviour to ask look for the best path
    private class BestPath extends Behaviour {
        JSONObject msg; //
        private boolean isDone = false;

        public BestPath(JSONObject s) {
            msg = s;
        };

        public void action() {
            // I am source
            if (myAgent.getLocalName() == msg.get("source")) {
                // if the viaPoints array is empty, we start
                // otherwise we are done
                // start timer
                long timeout = 3000;
                addBehaviour(new WakerBehaviour(myAgent, timeout) {
                    @Override
                    protected void onWake() {

                    }
                });
                // propagate message to neighbours
            }
            // I am destination
            else if (myAgent.getLocalName() == msg.get("destination")) {
                // send full list to source
                // create message
                ACLMessage fullList = new ACLMessage(ACLMessage.PROPAGATE);
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
            // else
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