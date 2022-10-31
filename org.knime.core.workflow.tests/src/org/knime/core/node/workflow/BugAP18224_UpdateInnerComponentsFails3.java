package org.knime.core.node.workflow;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Before;
import org.junit.Test;
import org.knime.core.node.ExecutionMonitor;

/**
 * Tests the new update components functionality of update errors being masked
 * when a parent update exists. In the workflow "mainWorkflow" one component
 * instance of B contains an inner component A. Both have an update available.
 * However, the update of B fixes the update of A, so the inner update has to be ignored.
 *
 * @author Leon Wenzler, KNIME GmbH, Konstanz, Germany
 */
public class BugAP18224_UpdateInnerComponentsFails3 extends WorkflowTestCase {

	private NodeID m_outerComponent;

	/**
	 * Initialize all node IDs
	 * 
	 * @throws Exception
	 */
	@Before
	public void setUp() throws Exception {
		var baseId = loadAndSetWorkflow(new File(getDefaultWorkflowDirectory(), "mainWorkflow"));
		m_outerComponent = new NodeID(baseId, 1);
	}

	@Test
	public void testUpdatingInnerAndOuter() throws Exception {
		var loadHelper = new WorkflowLoadHelper(true, getManager().getContextV2());
		
		assertTrue("Expected meta node update available",
				getManager().checkUpdateMetaNodeLink(m_outerComponent, loadHelper));
		// updating this without the AP-18224 fix would throw an exception
		getManager().updateMetaNodeLink(m_outerComponent, new ExecutionMonitor(), loadHelper);
		
		executeAndWait(m_outerComponent);
		checkState(m_outerComponent, InternalNodeContainerState.EXECUTED); // runs successful
	}
}
