package cz.metacentrum.perun.engine.unit;

import cz.metacentrum.perun.engine.AbstractEngineTest;
import cz.metacentrum.perun.engine.exceptions.TaskExecutionException;
import cz.metacentrum.perun.engine.runners.SendCollector;
import cz.metacentrum.perun.engine.scheduling.impl.BlockingSendExecutorCompletionService;
import cz.metacentrum.perun.taskslib.model.SendTask;
import org.junit.Before;
import org.junit.Test;

import static cz.metacentrum.perun.taskslib.model.SendTask.SendTaskStatus.SENT;
import static cz.metacentrum.perun.taskslib.model.Task.TaskStatus.SENDERROR;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class SendCollectorTest extends AbstractEngineTest {
	private BlockingSendExecutorCompletionService sendCompletionServiceMock;
	private SendCollector spy;

	@Before
	public void setUp() throws Exception {
		super.mockSetUp();
		sendCompletionServiceMock = mock(BlockingSendExecutorCompletionService.class);
		SendCollector sendCollector = new SendCollector(sendCompletionServiceMock,
				schedulingPoolMock, jmsQueueManagerMock);
		spy = spy(sendCollector);
	}

	@Test
	public void sendCollectorTest() throws Exception {
		when(sendCompletionServiceMock.blockingTake()).thenReturn(sendTask1, sendTask2, sendTask4, sendTask3);
		doReturn(false, false, false, false, true).when(spy).shouldStop();

		spy.run();

		assertEquals(SENT, sendTask1.getStatus());
		assertEquals(SENT, sendTask2.getStatus());
		assertEquals(SENT, sendTask3.getStatus());
		assertEquals(SENT, sendTask4.getStatus());
		verify(schedulingPoolMock, times(4)).decreaseSendTaskCount(task1.getId(), 1);
		verify(jmsQueueManagerMock, times(4)).reportSendTask(any(SendTask.class));
	}

	@Test
	public void sendCollectorTaskExceptionTest() throws Exception {
		doReturn(sendTask1)
				.doThrow(new TaskExecutionException(sendTask2.getId(), "Test error"))
				.when(sendCompletionServiceMock).blockingTake();
		doReturn(false, false, true).when(spy).shouldStop();

		spy.run();

		assertEquals(SENT, sendTask1.getStatus());
		verify(schedulingPoolMock, times(1)).decreaseSendTaskCount(task1.getId(), 1);
		verify(jmsQueueManagerMock, times(1)).reportSendTask(sendTask1);
		verify(jmsQueueManagerMock, times(1)).reportSendTask(eq(task1.getId()),
				eq(SENDERROR.toString()), contains("Destination"));
	}
}