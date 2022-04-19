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

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * This agent implements a simple Ping Agent that registers itself with the DF and
 * then waits for ACLMessages.
 * If  a REQUEST message is received containing the string "ping" within the content
 * then it replies with an INFORM message whose content will be the string "pong".
 *
 * @author Tiziana Trucco - CSELT S.p.A.
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
            ACLMessage  msg = myAgent.receive();
            //TODO: come lo mettiamo lo stato?

            if(msg != null){
                ACLMessage reply = msg.createReply();

                if(msg.getPerformative()== ACLMessage.INFORM){  //Inform?
                    String content = msg.getContent();
                    


                }
                else {
                    myLogger.log(Logger.INFO, "Agent "+getLocalName()+" - Unexpected message ["+ACLMessage.getPerformative(msg.getPerformative())+"] received from "+msg.getSender().getLocalName());
                    reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                    reply.setContent("( (Unexpected-act "+ACLMessage.getPerformative(msg.getPerformative())+") )");
                }
                send(reply);
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
            myLogger.log(Logger.SEVERE, "Agent "+getLocalName()+" - Cannot register with DF", e);
            doDelete();
        }
    }
}