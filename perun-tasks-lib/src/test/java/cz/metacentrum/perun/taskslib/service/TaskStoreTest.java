package cz.metacentrum.perun.taskslib.service;

import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.taskslib.exceptions.TaskStoreException;
import cz.metacentrum.perun.taskslib.model.ExecService;
import cz.metacentrum.perun.taskslib.model.Task;
import cz.metacentrum.perun.taskslib.service.impl.TaskStoreImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;

import static org.junit.Assert.*;

public class TaskStoreTest {
	private TaskStore taskStore;
	private Task taskW;
	private Task taskG;
	private Task taskD;

	@Rule
	public final ExpectedException exception = ExpectedException.none();

	@Before
	public void setUp() throws Exception {
		taskStore = new TaskStoreImpl();
		Facility facility = new Facility();
		facility.setName("Test");
		facility.setDescription("Test");
		facility.setId(1);

		ExecService execService1 = new ExecService();
		execService1.setId(1);

		ExecService execService2 = new ExecService();
		execService2.setId(2);

		ExecService execService3 = new ExecService();
		execService3.setId(3);

		taskW = new Task();
		taskW.setFacility(facility);
		taskW.setExecService(execService1);
		taskW.setId(1);
		taskW.setStatus(Task.TaskStatus.WAITING);

		taskG = new Task();
		taskG.setFacility(facility);
		taskG.setExecService(execService2);
		taskG.setId(2);
		taskG.setStatus(Task.TaskStatus.GENERATED);

		taskD = new Task();
		taskD.setFacility(facility);
		taskD.setExecService(execService3);
		taskD.setId(3);
		taskD.setStatus(Task.TaskStatus.DONE);
	}

	@Test
	public void testAddEqualTasks() throws Exception {
		Task taskWW = new Task();
		taskWW.setFacility(taskW.getFacility());
		taskWW.setExecService(taskW.getExecService());
		taskWW.setId(10);
		taskStore.addToPool(taskW);
		exception.expect(TaskStoreException.class);
		taskStore.addToPool(taskWW);
	}

	@Test
	public void testGetTaskWithStatus() throws Exception {
		taskStore.addToPool(taskW);
		taskStore.addToPool(taskG);
		taskStore.addToPool(taskD);

		List<Task> tasks = taskStore.getTasksWithStatus(Task.TaskStatus.WAITING);
		assertEquals(1, tasks.size());
		assertTrue(tasks.contains(taskW));

		tasks = taskStore.getTasksWithStatus(Task.TaskStatus.GENERATED);
		assertEquals(1, tasks.size());
		assertTrue(tasks.contains(taskG));

		tasks = taskStore.getTasksWithStatus(Task.TaskStatus.WAITING, Task.TaskStatus.GENERATED);
		assertEquals(2, tasks.size());
		assertTrue(tasks.contains(taskW));
		assertTrue(tasks.contains(taskG));

		tasks = taskStore.getTasksWithStatus(Task.TaskStatus.WAITING, Task.TaskStatus.GENERATED, Task.TaskStatus.DONE);
		assertEquals(3  , tasks.size());
		assertTrue(tasks.contains(taskW));
		assertTrue(tasks.contains(taskG));
		assertTrue(tasks.contains(taskD));
	}
}