/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   May 23, 2020 (hornm): created
 */
package org.knime.core.node.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.context.ModifiableNodeCreationConfiguration;
import org.knime.core.node.workflow.action.ReplaceNodeResult;
import org.knime.filehandling.core.port.FileSystemPortObject;
import org.mockito.ArgumentMatcher;

/**
 * Tests the {@link WorkflowManager#canReplaceNode(NodeID)} and
 * {@link WorkflowManager#replaceNode(NodeID, org.knime.core.node.context.ModifiableNodeCreationConfiguration)} methods.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
public class EnhSRV2696_ReplaceNodePorts extends WorkflowTestCase {

	private NodeID m_datagenerator_1;

	private NodeID m_concatenate_2;

	private NodeID m_metanode_4;

	private final WorkflowListener m_workflowListener = mock(WorkflowListener.class);

	private final ArgumentMatcher<WorkflowEvent> m_workflowEventMatcher = event -> event.getType()
			.equals(WorkflowEvent.Type.PORT_ADDED);

    /**
     * Load workflow.
     *
     * @throws Exception
     */
    @Before
    public void setup() throws Exception {
        NodeID baseID = loadAndSetWorkflow();
        m_datagenerator_1 = new NodeID(baseID, 1);
        m_concatenate_2 = new NodeID(baseID, 2);
        m_metanode_4 = new NodeID(baseID, 4);
        var wfm = getManager();
        wfm.addListener(m_workflowListener);
        setManager(wfm);
    }

    /**
     * Tests {@link WorkflowManager#canReplaceNode(NodeID)}.
     */
    @Test
    public void testCanReplaceNode() {
        WorkflowManager wfm = getManager();
        assertThat("metanode can't be replaced", wfm.canReplaceNode(m_metanode_4), is(false));
        wfm.executeAll();
        assertThat("executing node can't be replaced", wfm.canReplaceNode(m_concatenate_2), is(false));
        wfm.cancelExecution();
    }

    /**
     * Tests {@link WorkflowManager#replaceNode(NodeID, ModifiableNodeCreationConfiguration)}.
     */
    @Test
    public void testReplaceNode() {
        WorkflowManager wfm = getManager();
        NativeNodeContainer oldNC = (NativeNodeContainer)wfm.getNodeContainer(m_concatenate_2);
        ModifiableNodeCreationConfiguration creationConfig = oldNC.getNode().getCopyOfCreationConfig().get();
        creationConfig.getPortConfig().get().getExtendablePorts().get("input").addPort(BufferedDataTable.TYPE);
        creationConfig.getPortConfig().get().getExtendablePorts().get("input").addPort(BufferedDataTable.TYPE);
        wfm.replaceNode(m_concatenate_2, creationConfig);

        NativeNodeContainer newNC = (NativeNodeContainer)wfm.getNodeContainer(m_concatenate_2);
        assertThat("node hasn't been replaced", newNC, is(not(oldNC)));
        assertThat("node annotations not the same", newNC.getNodeAnnotation().getData().getText(),
            is(oldNC.getNodeAnnotation().getData().getText()));
        assertThat("node's x postion changed", newNC.getUIInformation().getBounds()[0],
            is(oldNC.getUIInformation().getBounds()[0]));
        assertThat("node's y postion changed", newNC.getUIInformation().getBounds()[1],
            is(oldNC.getUIInformation().getBounds()[1]));
        assertThat("unexpected number of input ports", newNC.getNrInPorts(), is(5));
        assertThat("missing in connections", wfm.getIncomingConnectionsFor(m_concatenate_2).size(), is(2));
        assertThat("missing out connections", wfm.getOutgoingConnectionsFor(m_concatenate_2).size(), is(1));

        // test if node cannot be replaced -> exception
        IllegalStateException e =
            assertThrows(IllegalStateException.class, () -> wfm.replaceNode(m_metanode_4, creationConfig));
        assertThat("unexpected exception message", e.getMessage(), is("Node cannot be replaced"));
        
        // check that listener noticed exactly one PORT_ADDED event
        verify(m_workflowListener, times(1)).workflowChanged(argThat(m_workflowEventMatcher));
    }
    
	/**
	 * Tests that connections remain if a port is inserted before another already
	 * connected one.
	 */
    @Test
	public void testInsertPortBeforeAnotherPort() {
		WorkflowManager wfm = getManager();
		NodeID modelwriter_11 = new NodeID(wfm.getID(), 11);
		NativeNodeContainer oldNC = (NativeNodeContainer) wfm.getNodeContainer(modelwriter_11);
		assertTrue("node annotation expected to be the default one", oldNC.getNodeAnnotation().getData().isDefault());

		// add new port
		ModifiableNodeCreationConfiguration creationConfig = oldNC.getNode().getCopyOfCreationConfig().get();
		creationConfig.getPortConfig().get().getExtendablePorts().get("File System Connection")
				.addPort(FileSystemPortObject.TYPE);
		ReplaceNodeResult replaceRes = wfm.replaceNode(modelwriter_11, creationConfig);

		// check that connection remains
		NativeNodeContainer newNC = (NativeNodeContainer) wfm.getNodeContainer(modelwriter_11);
		assertThat("connection is gone unexpectedly", wfm.getIncomingConnectionFor(modelwriter_11, 2), notNullValue());
		assertTrue("node annotation expected to be the default one", newNC.getNodeAnnotation().getData().isDefault());
		
		// try undo
		replaceRes.undo();
		newNC = (NativeNodeContainer) wfm.getNodeContainer(modelwriter_11);
		assertThat("connection is gone unexpectedly", wfm.getIncomingConnectionFor(modelwriter_11, 1), notNullValue());
		assertTrue("node annotation expected to be the default one", newNC.getNodeAnnotation().getData().isDefault());

        // check that listener noticed exactly one PORT_ADDED event
        verify(m_workflowListener, times(1)).workflowChanged(argThat(m_workflowEventMatcher));
	}

    /**
     * Tests {@link ReplaceNodeResult#undo()} and makes sure that connections removed during the node replacement are
     * added back again on undo.
     */
    @Test
    public void testReplaceNodeUndo() {
        WorkflowManager wfm = getManager();
        NativeNodeContainer oldNC = (NativeNodeContainer)wfm.getNodeContainer(m_concatenate_2);

        // add port and connect
        ModifiableNodeCreationConfiguration creationConfig = oldNC.getNode().getCopyOfCreationConfig().get();
        creationConfig.getPortConfig().get().getExtendablePorts().get("input").addPort(BufferedDataTable.TYPE);
        wfm.replaceNode(m_concatenate_2, creationConfig);
        ConnectionContainer newCC = wfm.addConnection(m_datagenerator_1, 1, m_concatenate_2, 3);
        NativeNodeContainer newNC = (NativeNodeContainer)wfm.getNodeContainer(m_concatenate_2);
        assertThat("unexpected number of input ports", newNC.getNrInPorts(), is(4));
        
        // remove port again
        ReplaceNodeResult replaceRes =
            wfm.replaceNode(m_concatenate_2, oldNC.getNode().getCopyOfCreationConfig().get());
        newNC = (NativeNodeContainer)wfm.getNodeContainer(m_concatenate_2);
        assertThat("unexpected number of input ports", newNC.getNrInPorts(), is(3));
        assertThat("unexpected connection", wfm.getConnection(newCC.getID()), nullValue());
        assertThat("canUndo expected to be possible", replaceRes.canUndo(), is(true));
        
        // undo remove operation
        replaceRes.undo();
        NativeNodeContainer undoNC = (NativeNodeContainer)wfm.getNodeContainer(m_concatenate_2);
        assertNotSame(newNC, undoNC);
        assertThat("unexpected number of input ports", undoNC.getNrInPorts(), is(4));
        assertThat("connection missing", wfm.getConnection(newCC.getID()), notNullValue());
        
        // check that listener noticed exactly two PORT_ADDED events
        verify(m_workflowListener, times(2)).workflowChanged(argThat(m_workflowEventMatcher));
    }

}
