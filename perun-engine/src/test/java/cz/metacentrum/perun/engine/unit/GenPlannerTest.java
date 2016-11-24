package cz.metacentrum.perun.engine.unit;

import cz.metacentrum.perun.engine.AbstractEngineTest;
import cz.metacentrum.perun.engine.scheduling.GenWorker;
import cz.metacentrum.perun.engine.scheduling.impl.BlockingGenExecutorCompletionService;
import cz.metacentrum.perun.engine.runners.impl.GenPlanner;
import cz.metacentrum.perun.taskslib.model.Task;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;

import static cz.metacentrum.perun.taskslib.model.Task.TaskStatus.GENERATING;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GenPlannerTest extends AbstractEngineTest{
	private BlockingGenExecutorCompletionService genCompletionServiceMock;
	private BlockingDeque<Task> newTasksQueue;
	private GenPlanner spy;

	@Before
	public void setUp() throws Exception {
		super.mockSetUp();
		genCompletionServiceMock = mock(BlockingGenExecutorCompletionService.class);
		newTasksQueue = new LinkedBlockingDeque<>();
		GenPlanner genPlanner = new GenPlanner(schedulingPoolMock, genCompletionServiceMock, jmsQueueManagerMock);
		spy = spy(genPlanner);
	}

	@Test
	public void testGenPlanner() throws Exception {
		Future<Task> futureMock = mock(Future.class);
		newTasksQueue.add(task1);

		when(schedulingPoolMock.getNewTasksQueue()).thenReturn(newTasksQueue);
		when(genCompletionServiceMock.blockingSubmit(any(GenWorker.class))).thenReturn(futureMock);
		doReturn(false, true).when(spy).shouldStop();

		spy.run();

		verify(genCompletionServiceMock, times(1)).blockingSubmit(any(GenWorker.class));
		verify(schedulingPoolMock, times(1)).addGenTaskFutureToPool(task1.getId(), futureMock);
		verify(jmsQueueManagerMock, times(1)).reportGenTask(task1);

		assertEquals(GENERATING, task1.getStatus());
	}

}