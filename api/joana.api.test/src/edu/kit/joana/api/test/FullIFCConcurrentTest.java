/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.api.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.junit.Test;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;

import edu.kit.joana.api.IFCAnalysis;
import edu.kit.joana.api.IFCType;
import edu.kit.joana.api.lattice.BuiltinLattices;
import edu.kit.joana.api.sdg.SDGConfig;
import edu.kit.joana.api.sdg.SDGProgram;
import edu.kit.joana.api.sdg.SDGProgramPart;
import edu.kit.joana.api.test.util.ApiTestException;
import edu.kit.joana.api.test.util.BuildSDG;
import edu.kit.joana.api.test.util.JoanaPath;
import edu.kit.joana.ifc.sdg.core.SecurityNode;
import edu.kit.joana.ifc.sdg.core.violations.IViolation;
import edu.kit.joana.ifc.sdg.mhpoptimization.MHPType;
import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;
import edu.kit.joana.util.Stubs;
import edu.kit.joana.wala.core.SDGBuilder.ExceptionAnalysis;
import edu.kit.joana.wala.core.SDGBuilder.FieldPropagation;
import edu.kit.joana.wala.core.SDGBuilder.PointsToPrecision;

/**
 * @author Juergen Graf <graf@kit.edu>
 */
public class FullIFCConcurrentTest {

	static final boolean outputPDGFiles = true;
	static final String outputDir = "out";
	private static final String SECRET = "sensitivity.Security.SECRET";
	private static final String PUBLIC = "sensitivity.Security.PUBLIC";
	
	static {
		if (outputPDGFiles) {
			File fOutDir = new File(outputDir);
			if (!fOutDir.exists()) {
				fOutDir.mkdir();
			}
		}
	}
	
	public static IFCAnalysis buildAndAnnotate(final String className, final String secSrc,
			final String pubOut) throws ApiTestException {
		return buildAndAnnotate(className, secSrc, pubOut, PointsToPrecision.INSTANCE_BASED);
	}
	
	public static IFCAnalysis buildAndAnnotate(final String className, final String secSrc,
			final String pubOut, final PointsToPrecision pts) throws ApiTestException {
		final SDGProgram prog = build(className, pts);
		final IFCAnalysis ana = annotate(prog, secSrc, pubOut);
		
		return ana;
	}
	
	public static IFCAnalysis annotate(final SDGProgram prog, final String secSrc, final String pubOut) {
		final IFCAnalysis ana = new IFCAnalysis(prog);
		SDGProgramPart secret = ana.getProgramPart(secSrc);
		assertNotNull(secret);
		ana.addSourceAnnotation(secret, BuiltinLattices.STD_SECLEVEL_HIGH);
		SDGProgramPart output = ana.getProgramPart(pubOut);
		assertNotNull(output);
		ana.addSinkAnnotation(output, BuiltinLattices.STD_SECLEVEL_LOW);
		
		return ana;
	}
	
	public static SDGProgram build(final String className) throws ApiTestException {
		return build(className, PointsToPrecision.INSTANCE_BASED);
	}
	
	public static SDGProgram build(final String className, final PointsToPrecision pts) throws ApiTestException {
		JavaMethodSignature mainMethod = JavaMethodSignature.mainMethodOfClass(className);
		SDGConfig config = new SDGConfig(JoanaPath.JOANA_MANY_SMALL_PROGRAMS_CLASSPATH, mainMethod.toBCString(), Stubs.JRE_14);
		config.setComputeInterferences(true);
		config.setExceptionAnalysis(ExceptionAnalysis.INTRAPROC);
		config.setFieldPropagation(FieldPropagation.OBJ_GRAPH);
		config.setPointsToPrecision(pts);
		SDGProgram prog = null;
		
		try {
			prog = SDGProgram.createSDGProgram(config);
			if (outputPDGFiles) {
				BuildSDG.saveSDGProgram(prog.getSDG(), outputDir + File.separator + className + ".pdg");
			}
		} catch (ClassHierarchyException e) {
			throw new ApiTestException(e);
		} catch (IOException e) {
			throw new ApiTestException(e);
		} catch (UnsoundGraphException e) {
			throw new ApiTestException(e);
		} catch (CancelException e) {
			throw new ApiTestException(e);
		}

		return prog;
	}
	
	private static void testLeaksFound(IFCAnalysis ana, int leaks) {
		Collection<? extends IViolation<SecurityNode>> illegal = ana.doIFC();
		assertFalse(illegal.isEmpty());
		assertEquals(leaks, illegal.size());
	}
	
	private static void testNoLeaksFound(IFCAnalysis ana) {
		Collection<? extends IViolation<SecurityNode>> illegal = ana.doIFC();
		assertTrue(illegal.isEmpty());
	}
	
	@Test
	public void testAlarmClock() {
		try {
			final SDGProgram prog = build("conc.ac.AlarmClock");
			{
				final IFCAnalysis ana1 = annotate(prog, "conc.ac.Clock.max", "conc.ac.Client.name");
				testLeaksFound(ana1,26);
			}
			{
				final IFCAnalysis ana2 = annotate(prog, SECRET, PUBLIC);
				testLeaksFound(ana2, 3);
			}
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testProducerConsumer() {
		try {
			IFCAnalysis ana = buildAndAnnotate("conc.bb.ProducerConsumer",
					"conc.bb.BoundedBuffer.putIn",
					"conc.bb.BoundedBuffer.takeOut");
			testLeaksFound(ana, 12);
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testDaytimeClientServer() {
		try {
			IFCAnalysis ana = buildAndAnnotate("conc.cliser.dt.Main",
					"conc.cliser.dt.DaytimeUDPClient.message",
					"conc.cliser.dt.DaytimeIterativeUDPServer.recieved");
			testLeaksFound(ana, 124);
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testKnockKnock() {
		try {
			SDGProgram p = build("conc.cliser.kk.Main", PointsToPrecision.UNLIMITED_OBJECT_SENSITIVE);
			IFCAnalysis ana = annotate(p, "conc.cliser.kk.KnockKnockThread.message", "conc.cliser.kk.KnockKnockTCPClient.received1");
			Collection<? extends IViolation<SecurityNode>> illegal = ana.doIFC();
			// communication appears in network socket layer - this can only be detected if stubs are used that model
			// network communication. We are now precise enough to not detect flow in java library code.
			assertTrue(String.format("Expected no violations, found %d", illegal.size()), illegal.isEmpty());
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testDaisy() {
		try {
			IFCAnalysis ana = buildAndAnnotate("conc.daisy.DaisyTest",
					"conc.daisy.DaisyUserThread.iterations",
					"conc.daisy.DaisyDir.dirsize");
			testLeaksFound(ana, 1);
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testDiningPhilosophers() {
		try {
			IFCAnalysis ana = buildAndAnnotate("conc.dp.DiningPhilosophers",
					"conc.dp.Philosopher.id",
					"conc.dp.DiningServer.state");
			testLeaksFound(ana, 48);
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testDiskScheduler() {
		try {
			IFCAnalysis ana = buildAndAnnotate("conc.ds.DiskSchedulerDriver",
					"conc.ds.DiskScheduler.position",
					"conc.ds.DiskReader.active");
			testLeaksFound(ana, 22);
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testKnapsack() {
		try {
			IFCAnalysis ana = buildAndAnnotate("conc.kn.Knapsack5",
					"conc.kn.Knapsack5$Item.profit",
					"conc.kn.PriorityRunQueue.numThreadsWaiting");
			testLeaksFound(ana, 57);
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testLaplaceGrid() {
		try {
			IFCAnalysis ana = buildAndAnnotate("conc.lg.LaplaceGrid",
					"conc.lg.Partition.values",
					"conc.lg.Partition.in");
			testLeaksFound(ana, 1520);
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testProbChannel() {
		try {
			IFCAnalysis ana = buildAndAnnotate("conc.pc.ProbChannel",
					"conc.pc.ProbChannel.x",
					PUBLIC);
			testLeaksFound(ana, 6);
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testSharedQueue() {
		try {
			IFCAnalysis ana = buildAndAnnotate("conc.sq.SharedQueue",
					"conc.sq.SharedQueue.next",
					"conc.sq.Semaphore.count");
			testLeaksFound(ana, 80);
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}


	@Test
	public void testProbChannelMulti() {
		try {
			IFCAnalysis ana = buildAndAnnotate("tests.probch.ProbChannel",
					SECRET,
					"tests.probch.ProbChannel$Data.a");
			Collection<? extends IViolation<SecurityNode>> illegal = ana.doIFC(IFCType.LSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(45, illegal.size());
			illegal = ana.doIFC(IFCType.LSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(42, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(12, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(10, illegal.size());
			illegal = ana.doIFC(IFCType.CLASSICAL_NI);
			assertTrue(illegal.isEmpty());
			assertEquals(0, illegal.size());
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testConcPasswordFile() {
		try {
			IFCAnalysis ana = buildAndAnnotate("tests.ConcPasswordFile",
					"tests.ConcPasswordFile.passwords",
					"tests.ConcPasswordFile.b");
			Collection<? extends IViolation<SecurityNode>> illegal = ana.doIFC(IFCType.LSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(62, illegal.size());
			illegal = ana.doIFC(IFCType.LSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(52, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(33, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(33, illegal.size());
			illegal = ana.doIFC(IFCType.CLASSICAL_NI);
			assertFalse(illegal.isEmpty());
			assertEquals(22, illegal.size());
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testIndirectRecursive() {
		try {
			IFCAnalysis ana = buildAndAnnotate("tests.IndirectRecursiveThreads",
					SECRET,
					PUBLIC);
			Collection<? extends IViolation<SecurityNode>> illegal = ana.doIFC(IFCType.LSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(53, illegal.size());
			illegal = ana.doIFC(IFCType.LSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(45, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(23, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(17, illegal.size());
			illegal = ana.doIFC(IFCType.CLASSICAL_NI);
			assertFalse(illegal.isEmpty());
			assertEquals(6, illegal.size());
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testProbPasswordFile() {
		try {
			IFCAnalysis ana = buildAndAnnotate("tests.ProbPasswordFile",
					SECRET,
					PUBLIC);
			Collection<? extends IViolation<SecurityNode>> illegal = ana.doIFC(IFCType.LSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(9, illegal.size());
			illegal = ana.doIFC(IFCType.LSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(6, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(4, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(2, illegal.size());
			illegal = ana.doIFC(IFCType.CLASSICAL_NI);
			assertTrue(illegal.isEmpty());
			assertEquals(0, illegal.size());
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testRecursiveThread() {
		try {
			IFCAnalysis ana = buildAndAnnotate("tests.RecursiveThread",
					SECRET,
					PUBLIC);
			Collection<? extends IViolation<SecurityNode>> illegal = ana.doIFC(IFCType.LSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(199, illegal.size());
			illegal = ana.doIFC(IFCType.LSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(189, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(40, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(33, illegal.size());
			illegal = ana.doIFC(IFCType.CLASSICAL_NI);
			assertFalse(illegal.isEmpty());
			assertEquals(9, illegal.size());
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testSynchronization() {
		try {
			IFCAnalysis ana = buildAndAnnotate("tests.Synchronization",
					SECRET,
					PUBLIC);
			Collection<? extends IViolation<SecurityNode>> illegal = ana.doIFC(IFCType.LSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(23, illegal.size());
			illegal = ana.doIFC(IFCType.LSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(19, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(11, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(3, illegal.size());
			illegal = ana.doIFC(IFCType.CLASSICAL_NI);
			assertTrue(illegal.isEmpty());
			assertEquals(0, illegal.size());
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testThreadJoining() {
		try {
			IFCAnalysis ana = buildAndAnnotate("tests.ThreadJoining",
					SECRET,
					PUBLIC);
			Collection<? extends IViolation<SecurityNode>> illegal = ana.doIFC(IFCType.LSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(12, illegal.size());
			illegal = ana.doIFC(IFCType.LSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(12, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(5, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(4, illegal.size());
			illegal = ana.doIFC(IFCType.CLASSICAL_NI);
			assertFalse(illegal.isEmpty());
			assertEquals(4, illegal.size());
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testThreadSpawning() {
		try {
			IFCAnalysis ana = buildAndAnnotate("tests.ThreadSpawning",
					SECRET,
					PUBLIC);
			Collection<? extends IViolation<SecurityNode>> illegal = ana.doIFC(IFCType.LSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(189, illegal.size());
			illegal = ana.doIFC(IFCType.LSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(177, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(19, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(18, illegal.size());
			illegal = ana.doIFC(IFCType.CLASSICAL_NI);
			assertTrue(illegal.isEmpty());
			assertEquals(0, illegal.size());
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testVolpanoSmith98Page3() {
		try {
			IFCAnalysis ana = buildAndAnnotate("tests.VolpanoSmith98Page3",
					"tests.VolpanoSmith98Page3.PIN",
					PUBLIC);
			Collection<? extends IViolation<SecurityNode>> illegal = ana.doIFC(IFCType.LSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(83, illegal.size());
			illegal = ana.doIFC(IFCType.LSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(77, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.SIMPLE);
			assertFalse(illegal.isEmpty());
			assertEquals(21, illegal.size());
			illegal = ana.doIFC(IFCType.RLSOD, MHPType.PRECISE);
			assertFalse(illegal.isEmpty());
			assertEquals(7, illegal.size());
			illegal = ana.doIFC(IFCType.CLASSICAL_NI);
			assertTrue(illegal.isEmpty());
			assertEquals(0, illegal.size());
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

}
