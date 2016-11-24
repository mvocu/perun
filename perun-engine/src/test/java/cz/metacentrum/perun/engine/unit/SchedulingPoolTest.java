package cz.metacentrum.perun.engine.unit;

import cz.metacentrum.perun.engine.AbstractEngineTest;
import cz.metacentrum.perun.engine.scheduling.SchedulingPool;
import cz.metacentrum.perun.taskslib.model.Task;
import cz.metacentrum.perun.taskslib.model.Task.TaskStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.concurrent.Future;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Tests of SchedulingPool which represent local storage of Tasks which are processed by Engine.
 *
 * @author Michal Karm Babacek
 * @author Michal Voců
 * @author Pavel Zlámal <zlamal@cesnet.cz>
 */
public class SchedulingPoolTest extends AbstractEngineTest {
	@Autowired
	private SchedulingPool schedulingPool;

	@Before
	public void setup() throws Exception {
		super.setup();
		schedulingPool.addToPool(task1);
	}

	@After
	public void cleanup() {
		schedulingPool.removeTask(task1);
		schedulingPool.removeTask(task2);
	}

	@Test
	public void addToPoolTest() {
		assertEquals("Original size should be 1", 1, schedulingPool.getSize());

		schedulingPool.addToPool(task1); // pool already contains this task
		assertEquals("New size should be 1 because the added Task was already in.", 1, schedulingPool.getSize());

		schedulingPool.addToPool(task2);
		assertEquals("New size should be 2.", 2, schedulingPool.getSize());
	}

	@Test(expected = IllegalArgumentException.class)
	public void doNotAddNotPlannedTasks() {
		task2.setStatus(TaskStatus.GENERATING);
		schedulingPool.addToPool(task2);
	}

	@Test
	public void getPlannedFromPoolTest() {
		Collection<Task> tasks = schedulingPool.getPlannedTasks();
		assertTrue("Task task1 should be in the collection.", tasks.contains(task1));

		schedulingPool.addToPool(task2);
		tasks = schedulingPool.getPlannedTasks();
		assertTrue("Both Tasks should be in the collection.", tasks.contains(task1) && tasks.contains(task2));
	}

	@Test
	public void getGeneratingFromPoolTest() throws Exception {
		Collection<Task> tasks = schedulingPool.getGeneratingTasks();
		assertTrue("There should be no generating Tasks", tasks.isEmpty());

		schedulingPool.addToPool(task2);
		task2.setStatus(TaskStatus.GENERATING);
		schedulingPool.getGeneratingTasksBlockingMap().blockingPut(task2.getId(), task2);
		tasks = schedulingPool.getGeneratingTasks();
		assertTrue("Task task1 should be in the collection.", tasks.contains(task2));
	}

	@Test
	public void getGeneratedFromPool() {
		Collection<Task> tasks = schedulingPool.getGeneratedTasks();
		assertTrue("There should be no generated Tasks", tasks.isEmpty());

		schedulingPool.addToPool(task2);
		task2.setStatus(TaskStatus.GENERATED);
		schedulingPool.getGeneratedTasksQueue().add(task2);
		tasks = schedulingPool.getGeneratedTasks();
		assertTrue("Task task1 should be in the collection.", tasks.contains(task2));
	}

	@Test
	public void getGenTaskFutureById() {
		Future<Task> future = schedulingPool.getGenTaskFutureById(task1.getId());
		assertNull("There should be no Future under this id.", future);

		Future<Task> futureMock = mock(Future.class);
		schedulingPool.getGenTaskFuturesMap().put(task1.getId(), futureMock);
		future = schedulingPool.getGenTaskFutureById(task1.getId());
		assertEquals(futureMock, future);
	}

	@Test
	public void getTaskByIdTest() {
		Task task = schedulingPool.getTaskById(task1.getId());
		assertEquals(task1, task);
	}

	@Test
	public void removeSentTaskTest() {
		schedulingPool.addSendTaskCount(task1.getId(), 2);
		assertEquals(1, schedulingPool.getSize());

		schedulingPool.decreaseSendTaskCount(task1.getId(), 1);
		schedulingPool.decreaseSendTaskCount(task1.getId(), 1);

		assertEquals("Task should be removed from pool when associated sendTask count reaches zero",
				0, schedulingPool.getSize());
	}

}
