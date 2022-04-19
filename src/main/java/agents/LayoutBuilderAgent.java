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
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

/**
 * This agent creates all the Conveyor agents, assigning to each CNV its neighbours.
 *
 * @author Luigi Catello, Mario Valentino
 * @version  $Date: 2010-04-08 13:08:55 +0200 (gio, 08 apr 2010) $ $Revision: 6297 $
 */
public class LayoutBuilderAgent extends Agent{

    private Logger myLogger = Logger.getMyLogger(getClass().getName());

    protected void setup() {
        // Registration with the DF
        DFAgentDescription dfd = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("LayoutBuilderAgent");
        sd.setName(getName());
        sd.setOwnership("Group 15");
        dfd.setName(getAID());
        dfd.addServices(sd);

        try {
            DFService.register(this,dfd);

            //Agent creation
            ContainerController containerController = getContainerController();

            AgentController CNV1 = containerController.createNewAgent("CNV1","agents.ConveyorAgent", new Object[]{"CNV2"});
            CNV1.start();

            AgentController CNV2 = containerController.createNewAgent("CNV2","agents.ConveyorAgent", new Object[]{"CNV3"});
            CNV2.start();

            AgentController CNV3 = containerController.createNewAgent("CNV3","agents.ConveyorAgent", new Object[]{"CNV4", "CNV13"});
            CNV3.start();

            AgentController CNV4 = containerController.createNewAgent("CNV4","agents.ConveyorAgent", new Object[]{"CNV5"});
            CNV4.start();

            AgentController CNV5 = containerController.createNewAgent("CNV5","agents.ConveyorAgent", new Object[]{"CNV6"});
            CNV5.start();

            AgentController CNV6 = containerController.createNewAgent("CNV6","agents.ConveyorAgent", new Object[]{"CNV7"});
            CNV6.start();

            AgentController CNV7 = containerController.createNewAgent("CNV7","agents.ConveyorAgent", new Object[]{"CNV8"});
            CNV7.start();

            AgentController CNV8 = containerController.createNewAgent("CNV8","agents.ConveyorAgent", new Object[]{"CNV9", "CNV14"});
            CNV8.start();

            AgentController CNV9 = containerController.createNewAgent("CNV9","agents.ConveyorAgent", new Object[]{"CNV10"});
            CNV9.start();

            AgentController CNV10 = containerController.createNewAgent("CNV10","agents.ConveyorAgent", new Object[]{"CNV11"});
            CNV10.start();

            AgentController CNV11 = containerController.createNewAgent("CNV11","agents.ConveyorAgent", new Object[]{"CNV12"});
            CNV11.start();

            AgentController CNV12 = containerController.createNewAgent("CNV12","agents.ConveyorAgent", new Object[]{"CNV1"});
            CNV12.start();

            AgentController CNV13 = containerController.createNewAgent("CNV13","agents.ConveyorAgent", new Object[]{"CNV9", "CNV14"});
            CNV13.start();

            AgentController CNV14 = containerController.createNewAgent("CNV14","agents.ConveyorAgent", new Object[]{"CNV12"});
            CNV14.start();

        } catch (FIPAException e) {
            myLogger.log(Logger.SEVERE, "Agent "+getLocalName()+" - Cannot register with DF", e);
            doDelete();
        } catch (StaleProxyException e) {
            myLogger.log(Logger.SEVERE, "Agent "+getLocalName()+" - Cannot create the agents", e);
            doDelete();
        }
    }
}