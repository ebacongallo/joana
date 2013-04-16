/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.api.test;

import java.io.IOException;

import org.junit.Test;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;

import edu.kit.joana.api.sdg.SDGConfig;
import edu.kit.joana.api.sdg.SDGProgram;
import edu.kit.joana.api.test.util.JoanaPath;
import edu.kit.joana.ifc.sdg.mhpoptimization.MHPType;
import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;
import edu.kit.joana.util.Stubs;
import edu.kit.joana.wala.core.SDGBuilder.ExceptionAnalysis;
import edu.kit.joana.wala.core.SDGBuilder.FieldPropagation;
import edu.kit.joana.wala.core.SDGBuilder.PointsToPrecision;

/**
 * Test SDG construction in corner cases.
 * @author Martin Mohr
 */
public class SDGConstructionCCTest {
	
	@Test
	public void testBuildStaticNativeExample() throws ClassHierarchyException, IOException, UnsoundGraphException, CancelException {
		JavaMethodSignature mainMethod = JavaMethodSignature.mainMethodOfClass("joana.api.testdata.seq.LocalKillDefRegression");
		final SDGConfig config = new SDGConfig(JoanaPath.JOANA_API_TEST_DATA_CLASSPATH, mainMethod.toBCString(), Stubs.NO_STUBS,
				ExceptionAnalysis.ALL_NO_ANALYSIS, FieldPropagation.OBJ_GRAPH, PointsToPrecision.TYPE, false, false, MHPType.NONE);
		SDGProgram.createSDGProgram(config);
	}
}
