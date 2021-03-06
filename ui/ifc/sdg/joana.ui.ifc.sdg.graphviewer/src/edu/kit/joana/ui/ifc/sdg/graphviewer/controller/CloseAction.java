/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
/*
 * @(c)CloseAction.java
 *
 * Project: GraphViewer
 *
 * Chair for Softwaresystems
 * Faculty of Informatics and Mathematics
 * University of Passau
 *
 * Created on 13.12.2004 at 17:44:21
 */
package edu.kit.joana.ui.ifc.sdg.graphviewer.controller;

import java.awt.event.ActionEvent;

import edu.kit.joana.ui.ifc.sdg.graphviewer.model.Graph;
import edu.kit.joana.ui.ifc.sdg.graphviewer.view.GraphPane;

/**
 * @author <a href="mailto:wellner@fmi.uni-passau.de">Tobias Wellner </a>
 * @version 1.0
 */
public class CloseAction extends AbstractGVAction {
	private static final long serialVersionUID = -4784279785259276504L;

	private final GraphPane graphPane;

    /**
     * Constructs a new <code>CloseAction</code> object.
     */
    public CloseAction(GraphPane graphPane) {
        super("close.name", "close.description", "close");
        this.graphPane = graphPane;
    }

    /**
     * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
     */
    public void actionPerformed(ActionEvent event) {
        Graph g = graphPane.getSelectedGraph();
        g.close();
//        return new CommandStatusEvent(this, CommandStatusEvent.SUCCESS,
//                new Resource(COMMANDS_BUNDLE, "close.success.status"));
    }
}
