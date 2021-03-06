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
import edu.kit.joana.ifc.sdg.graph.SDG;
import edu.kit.joana.ifc.sdg.graph.SDGNode;
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
			final String pubOut, MHPType mhpType) throws ApiTestException {
		return buildAndAnnotate(className, secSrc, pubOut, PointsToPrecision.INSTANCE_BASED, mhpType);
	}
	
	public static IFCAnalysis buildAndAnnotate(final String className, final String secSrc,
			final String pubOut, final PointsToPrecision pts, MHPType mhpType) throws ApiTestException {
		final SDGProgram prog = build(className, pts, mhpType);
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
	
	public static SDGProgram build(final String className, MHPType mhpType) throws ApiTestException {
		return build(className, PointsToPrecision.INSTANCE_BASED, mhpType);
	}
	
	public static SDGProgram build(final String className, final PointsToPrecision pts, MHPType mhpType) throws ApiTestException {
		JavaMethodSignature mainMethod = JavaMethodSignature.mainMethodOfClass(className);
		SDGConfig config = new SDGConfig(JoanaPath.JOANA_MANY_SMALL_PROGRAMS_CLASSPATH, mainMethod.toBCString(), Stubs.JRE_15);
		config.setComputeInterferences(true);
		config.setMhpType(mhpType);
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
	
	@SuppressWarnings("unused")
	private static void testNoLeaksFound(IFCAnalysis ana) {
		Collection<? extends IViolation<SecurityNode>> illegal = ana.doIFC();
		assertTrue(illegal.isEmpty());
	}
	
	@Test
	public void testAlarmClock() {
		try {
			final SDGProgram prog = build("conc.ac.AlarmClock", MHPType.PRECISE);
			{
				final IFCAnalysis ana1 = annotate(prog, "conc.ac.Clock.max", "conc.ac.Client.name");
				testLeaksFound(ana1, 144);
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
					"conc.bb.BoundedBuffer.takeOut",
					MHPType.PRECISE
			);
			testLeaksFound(ana, 16);
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
					"conc.cliser.dt.DaytimeIterativeUDPServer.recieved",
					MHPType.PRECISE
			);
			testLeaksFound(ana, 2);
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testKnockKnock() {
		try {
			SDGProgram p = build("conc.cliser.kk.Main", PointsToPrecision.UNLIMITED_OBJECT_SENSITIVE, MHPType.PRECISE);
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
					"conc.daisy.DaisyDir.dirsize",
					MHPType.PRECISE
			);
			testLeaksFound(ana, 2);
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
					"conc.dp.DiningServer.state",
					MHPType.PRECISE
			);
			testLeaksFound(ana, 96);
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
					"conc.ds.DiskReader.active",
					MHPType.PRECISE
		);
			testLeaksFound(ana, 24);
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
					"conc.kn.PriorityRunQueue.numThreadsWaiting",
					MHPType.PRECISE
			);
			testLeaksFound(ana, 60);
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
					"conc.lg.Partition.in",
					MHPType.PRECISE
			);
			testLeaksFound(ana, 1113);
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
					PUBLIC,
					MHPType.PRECISE
			);
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
					"conc.sq.Semaphore.count",
					MHPType.PRECISE
			);
			testLeaksFound(ana, 80);
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}


	@Test
	public void testProbChannelMulti() {
		try {
			/* MHPType.SIMPLE */ {
				final IFCAnalysis ana = buildAndAnnotate("tests.probch.ProbChannel",
						SECRET,
						"tests.probch.ProbChannel$Data.a",
						MHPType.SIMPLE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;

				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(85, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(68, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(73, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertTrue(illegal.isEmpty());
				assertEquals(0, illegal.size());

			}
			/* MHPType.PRECISE */ {
				final IFCAnalysis ana = buildAndAnnotate("tests.probch.ProbChannel",
						SECRET,
						"tests.probch.ProbChannel$Data.a",
						MHPType.PRECISE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
				
				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(35, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(16, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(25, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertTrue(illegal.isEmpty());
				assertEquals(0, illegal.size());
			}
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testConcPasswordFile() {
		try {
			/* MHPType.SIMPLE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.ConcPasswordFile",
						"tests.ConcPasswordFile.passwords",
						"tests.ConcPasswordFile.b",
						MHPType.SIMPLE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
				
				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(14, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(12, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertFalse(illegal.isEmpty());
				assertEquals(8, illegal.size());
			}
			
			/* MHPType.PRECISE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.ConcPasswordFile",
						"tests.ConcPasswordFile.passwords",
						"tests.ConcPasswordFile.b",
						MHPType.PRECISE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
				
				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(14, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(12, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(10, illegal.size());

				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertFalse(illegal.isEmpty());
				assertEquals(8, illegal.size());
			}
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testIndirectRecursive() {
		try {
			/* MHPType.SIMPLE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.IndirectRecursiveThreads",
						SECRET,
						PUBLIC,
						MHPType.SIMPLE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
				
				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(79, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(46, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(7, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertFalse(illegal.isEmpty());
				assertEquals(5, illegal.size());
			}
			/* MHPType.PRECISE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.IndirectRecursiveThreads",
						SECRET,
						PUBLIC,
						MHPType.PRECISE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
	
				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(73, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(40, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(2, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertFalse(illegal.isEmpty());
				assertEquals(5, illegal.size());
			}
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testProbPasswordFile() {
		try {
			/* MHPType.SIMPLE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.ProbPasswordFile",
						SECRET,
						PUBLIC,
						MHPType.SIMPLE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;

				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(6, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(4, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(7, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertTrue(illegal.isEmpty());
				assertEquals(0, illegal.size());
			}

			/* MHPType.PRECISE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.ProbPasswordFile",
						SECRET,
						PUBLIC,
						MHPType.PRECISE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
				
				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(3, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(2, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(4, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertTrue(illegal.isEmpty());
				assertEquals(0, illegal.size());
			}
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testRecursiveThread() {
		try {
			/* MHPType.SIMPLE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.RecursiveThread",
						SECRET,
						PUBLIC,
						MHPType.SIMPLE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
				
				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(109, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(43, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(19, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertFalse(illegal.isEmpty());
				assertEquals(6, illegal.size());
			}
			
			/* MHPType.PRECISE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.RecursiveThread",
						SECRET,
						PUBLIC,
						MHPType.PRECISE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;

				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(99, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(37, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(12, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertFalse(illegal.isEmpty());
				assertEquals(6, illegal.size());
			}
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testSynchronization() {
		try {
			/* MHPType.SIMPLE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.Synchronization",
						SECRET,
						PUBLIC,
						MHPType.SIMPLE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
				
				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(15, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(12, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(4, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertFalse(illegal.isEmpty());
				assertEquals(2, illegal.size());
			}
			
			/* MHPType.PRECISE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.Synchronization",
						SECRET,
						PUBLIC,
						MHPType.PRECISE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
				
				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(8, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(4, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(1, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertFalse(illegal.isEmpty());
				assertEquals(2, illegal.size());
			}
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testThreadJoining() {
		try {
			/* MHPType.SIMPLE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.ThreadJoining",
						SECRET,
						PUBLIC,
						MHPType.SIMPLE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
				
				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(6, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(5, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(2, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertFalse(illegal.isEmpty());
				assertEquals(4, illegal.size());
			}
			
			/* MHPType.PRECISE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.ThreadJoining",
						SECRET,
						PUBLIC,
						MHPType.PRECISE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
			
				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(6, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(4, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(2, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertFalse(illegal.isEmpty());
				assertEquals(4, illegal.size());
			}
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testThreadSpawning() {
		try {
			/* MHPType.SIMPLE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.ThreadSpawning",
						SECRET,
						PUBLIC,
						MHPType.SIMPLE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
				
				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(35, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(25, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(1, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertTrue(illegal.isEmpty());
				assertEquals(0, illegal.size());
			}
			
			/* MHPType.PRECISE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.ThreadSpawning",
						SECRET,
						PUBLIC,
						MHPType.PRECISE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
			
				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(33, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(24, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(1, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertTrue(illegal.isEmpty());
				assertEquals(0, illegal.size());
			}
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Test
	public void testVolpanoSmith98Page3() {
		try {
			/* MHPType.SIMPLE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.VolpanoSmith98Page3",
						"tests.VolpanoSmith98Page3.PIN",
						PUBLIC,
						MHPType.SIMPLE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
				
				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(88, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(31, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(3, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertTrue(illegal.isEmpty());
				assertEquals(0, illegal.size());
			}
			
			/* MHPType.PRECISE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.VolpanoSmith98Page3",
						"tests.VolpanoSmith98Page3.PIN",
						PUBLIC,
						MHPType.PRECISE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
				
				illegal = ana.doIFC(IFCType.LSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(63, illegal.size());
				
				illegal = ana.doIFC(IFCType.RLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(7, illegal.size());
				
				illegal = ana.doIFC(IFCType.timingiRLSOD);
				assertFalse(illegal.isEmpty());
				assertEquals(2, illegal.size());
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertTrue(illegal.isEmpty());
				assertEquals(0, illegal.size());
			}
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test
	public void testLoopedFork() {
		// Demonstrates that even in this example, the thread is forked from java.lang.Thread.start()V
		try {
			/* MHPType.PRECISE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.LoopedFork",
						SECRET,
						PUBLIC,
						MHPType.PRECISE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertTrue(illegal.isEmpty());
				
				final SDG sdg = ana.getProgram().getSDG();
				final SDGNode fork = sdg.getThreadsInfo().getThread(1).getFork();
				assertEquals("java.lang.Thread.start()V", fork.getBytecodeName());
			}
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	@Test()
	public void testLoopedFork2() {
		// Demonstrates that at least in this example, fork-nodes can no longer be FOLD nodes
		try {
			/* MHPType.PRECISE */ {
				IFCAnalysis ana = buildAndAnnotate("tests.LoopedFork2",
						SECRET,
						PUBLIC,
						MHPType.PRECISE
				);
				Collection<? extends IViolation<SecurityNode>> illegal;
				
				illegal = ana.doIFC(IFCType.CLASSICAL_NI);
				assertTrue(illegal.isEmpty());
				
				final SDG sdg = ana.getProgram().getSDG();
				final SDGNode fork = sdg.getThreadsInfo().getThread(2).getFork();
				assertEquals(SDGNode.Kind.CALL, fork.getKind());
			}
		} catch (ApiTestException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
}
