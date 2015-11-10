package cz.metacentrum.perun.dispatcher.perftest;

import java.util.ArrayList;
import java.util.Properties;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;

import oracle.sql.DATE;

import org.apache.tomcat.dbcp.dbcp.BasicDataSource;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.web.context.WebApplicationContext;

import cz.metacentrum.perun.controller.service.GeneralServiceManager;
import cz.metacentrum.perun.core.api.Destination;
import cz.metacentrum.perun.core.api.ExtSourcesManager;
import cz.metacentrum.perun.core.api.FacilitiesManager;
import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.Member;
import cz.metacentrum.perun.core.api.Perun;
import cz.metacentrum.perun.core.api.PerunPrincipal;
import cz.metacentrum.perun.core.api.PerunSession;
import cz.metacentrum.perun.core.api.Resource;
import cz.metacentrum.perun.core.api.ResourcesManager;
import cz.metacentrum.perun.core.api.Service;
import cz.metacentrum.perun.core.api.User;
import cz.metacentrum.perun.core.api.Vo;
import cz.metacentrum.perun.core.api.exceptions.AlreadyMemberException;
import cz.metacentrum.perun.core.api.exceptions.GroupAlreadyAssignedException;
import cz.metacentrum.perun.core.api.exceptions.GroupExistsException;
import cz.metacentrum.perun.core.api.exceptions.GroupNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.MemberNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.NotMemberOfParentGroupException;
import cz.metacentrum.perun.core.api.exceptions.PerunException;
import cz.metacentrum.perun.core.api.exceptions.PrivilegeException;
import cz.metacentrum.perun.core.api.exceptions.ResourceNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.ServiceAlreadyAssignedException;
import cz.metacentrum.perun.core.api.exceptions.ServiceExistsException;
import cz.metacentrum.perun.core.api.exceptions.ServiceNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.VoExistsException;
import cz.metacentrum.perun.core.api.exceptions.VoNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.WrongAttributeValueException;
import cz.metacentrum.perun.core.api.exceptions.WrongReferenceAttributeValueException;
import cz.metacentrum.perun.dispatcher.exceptions.PerunHornetQServerException;
import cz.metacentrum.perun.dispatcher.main.DispatcherStarter;
import cz.metacentrum.perun.dispatcher.model.Event;
import cz.metacentrum.perun.dispatcher.processing.EventQueue;
import cz.metacentrum.perun.dispatcher.scheduling.SchedulingPool;
import cz.metacentrum.perun.dispatcher.service.DispatcherManager;
import cz.metacentrum.perun.taskslib.model.ExecService;

public class DispatcherPerformanceTest extends JdbcDaoSupport {
	private final static Logger log = LoggerFactory.getLogger(DispatcherPerformanceTest.class);

	private DispatcherManager dispatcherManager;
	@Autowired
	private AbstractApplicationContext springCtx;
	@Autowired
	private Properties dispatcherPropertiesBean;
	@Autowired
	@Qualifier("perunScheduler")
	private SchedulerFactoryBean perunScheduler;
	@Autowired
	private SchedulingPool schedulingPool;
	@Autowired
	private Perun perun;
	@Autowired
	private BasicDataSource dataSource;
	@Autowired
	private TaskExecutor taskExecutor;
	
	@Autowired
	private GeneralServiceManager generalServiceManager;
	@Autowired
	private EventQueue eventQueue;
	
	protected PerunSession sess;
	protected Group group1;
	protected Vo vo1;
	protected User user1;
	protected Facility facility1;
	protected Resource resource1;
	protected Service service1;
	protected Destination destination1;
	protected Member member1;
	protected ExecService execservice1;
	protected ExecService execservice2;
	protected ArrayList<Integer> testFacilities;

	final String cleanupQuery = 
			"rollback;  " +
			"delete from perun.tasks_results where task_id in  " +
			"(select t.id from perun.tasks t left join perun.facilities f on t.facility_id = f.id  " +
			"where f.name like 'testFacility%');  " +
			" delete from perun.tasks where facility_id in  " + 
			"(select id from perun.facilities where name like 'testFacility%');  " +
			"delete from perun.service_dependencies where exec_service_id in  " +  
			"(select es.id from perun.exec_services es left join perun.services s on es.service_id = s.id  " + 
			"where s.name = 'testService');  " +
			"delete from perun.resource_services where resource_id in  " + 
			"(select id from perun.resources where name = 'testResource');  " +
			"delete from perun.groups_resources where resource_id in  " + 
			"(select id from perun.resources where name = 'testResource'); " + 
			"delete from perun.facility_service_destinations where service_id in " +
			"(select id from perun.services where name = 'testService'); " +
			"delete from perun.resources where name = 'testResource'; " +
			"delete from perun.facilities where name like 'testFacility%'; " +
			"delete from perun.exec_services where id in " + 
			"(select es.id from perun.exec_services es left join perun.services s on es.service_id = s.id " + 
			"where s.name = 'testService'); " +
			"delete from perun.services where name = 'testService'; " +
			"delete from perun.groups_members where member_id in " + 
			"(select m.id from perun.members m left join perun.users u on m.user_id = u.id " + 
			"where u.first_name = 'firstName'); " +
			"delete from perun.groups_members where group_id in " + 
			"(select id from perun.groups where name = 'falcon'); " +
			"delete from perun.members where user_id in " +
			"(select id from perun.users where first_name = 'firstName'); " +
			"delete from perun.user_ext_sources where user_id in " + 
			"(select id from perun.users where first_name = 'firstName'); " +
			"delete from perun.users where first_name = 'firstName'; " +
			"delete from perun.groups where vo_id in (select id from perun.vos where name = 'testVo'); " +
			"delete from perun.groups where vo_id in (select id from perun.vos where name = 'testVo'); " +
			"delete from perun.groups where name = 'falcon'; " +
			"delete from perun.vos where name = 'testVo'; " +
			"commit;";
	

	/**
	 * Initialize integrated dispatcher.
	 */
	public final void init() {

		try {

			dispatcherManager = springCtx.getBean("dispatcherManager", DispatcherManager.class);
			if(springCtx instanceof WebApplicationContext) {
				// do nothing here
			} else {
				springCtx.registerShutdownHook();
			}
			
			removeTestTasks();
			
			// Register into the database
			// DO NOT: dispatcherStarter.dispatcherManager.registerDispatcher();
			// Start HornetQ server
			dispatcherManager.startPerunHornetQServer();
			// Start System Queue Processor
			dispatcherManager.startProcessingSystemMessages();
			// Prefetch rules for all the Engines in the Perun DB and create
			// Dispatcher queues
			dispatcherManager.prefetchRulesAndDispatcherQueues();
			// reload tasks from database
			// not for perftest: dispatcherManager.loadSchedulingPool();
			// Start parsers (mining data both from Grouper and PerunDB)
			// not for perftest: dispatcherManager.startParsingData();
			// Start Event Processor
			dispatcherManager.startProcessingEvents();

			log.debug("JDBC url:  " + dataSource.getUrl());
			
			
			taskExecutor.execute(new Runnable() {

				@Override
				public void run() {
					try {
					// populate task database
					createTestTasks();
					
					// get current time -> start time
					log.debug("PERFTEST starting propagations:  " + System.currentTimeMillis() );

					// start propagation
					String message = member1.serializeToString() + " added to  " + group1.serializeToString() + ".";

					Event event = new Event();
					event.setTimeStamp(System.currentTimeMillis());
					event.setHeader("portishead");
					event.setData(message);
					eventQueue.add(event);
					
					// wait for all propagations to complete
					Boolean finished = false;
					while(!finished) {
						if(schedulingPool.getSize() > 0 &&
								schedulingPool.getWaitingTasks().isEmpty() &&
								schedulingPool.getPlannedTasks().isEmpty() &&
								schedulingPool.getProcessingTasks().isEmpty()) {
							finished = true;
						} else {
							log.debug("There are  " + schedulingPool.getProcessingTasks().size() + " processing tasks");
							log.debug("There are  " + schedulingPool.getWaitingTasks().size() + " waiting tasks");
							log.debug("There are  " + schedulingPool.getPlannedTasks().size() + " planned tasks");
							log.debug("There are  " + schedulingPool.getDoneTasks().size() + " done tasks");
							log.debug("There are  " + schedulingPool.getErrorTasks().size() + " error tasks");
							Thread.sleep(5000);
						}
					}
					
					// get current time -> end time
					log.debug("PERFTEST end propagations:  " + System.currentTimeMillis());
					log.debug("    " + schedulingPool.getDoneTasks() + " done");
					log.debug("    " + schedulingPool.getErrorTasks() + " failed");
					
					// print results and (wait for) exit
					removeTestTasks();
					} catch (Exception e) {
						log.error(e.toString(), e);
					}
				}
				
			});

			
		} catch (PerunHornetQServerException e) {
			log.error(e.toString(), e);
		} catch (Exception e) {
			log.error(e.toString(), e);
		}

	}


	@PreDestroy
	public void destroy() {
		try {
			// stop current scheduler
			perunScheduler.stop();
			// stop job triggers
			perunScheduler.getScheduler().pauseAll();
		} catch (SchedulerException ex) {
			log.error("Unable to stop dispatcher scheduler: {}", ex);
		}
		// stop currently running jobs
		//dispatcherManager.stopProcessingEvents();
		//dispatcherManager.stopParsingData();
		dispatcherManager.stopProcessingSystemMessages();
		dispatcherManager.stopPerunHornetQServer();
		try {
			removeTestTasks();
		} catch(PerunException e) {
			log.error(e.toString(), e);
		}
	}


	private void createTestTasks() throws PerunException {
		PerunPrincipal pp = new PerunPrincipal("perunTests", ExtSourcesManager.EXTSOURCE_NAME_INTERNAL, ExtSourcesManager.EXTSOURCE_INTERNAL);
		PerunSession sess = perun.getPerunSession(pp);

		testFacilities = new ArrayList<Integer>();
		
		// create VO for tests
		vo1 = new Vo(0, "testVo", "testVo");
		vo1 = perun.getVosManager().createVo(sess, vo1);
		// create some group in there
		group1 = new Group("falcon", "desc");
		group1 = perun.getGroupsManager().createGroup(sess, vo1, group1);
		// create user in the VO
		// skip the xEntry (authorization check),
		// could skip the xBl a go directly to xImpl to avoid writing audit
		// log
		user1 = new User(0, "firstName", "lastName", "", "", "");
		user1 = perun.getUsersManager().createUser(sess, user1);
		// make the user the member of the group
		member1 = perun.getMembersManager().createMember(sess, vo1, user1);
		member1.setStatus("VALID");
		perun.getGroupsManager().addMember(sess, group1, member1);
		// create service
		service1 = new Service(0, "testService");
		service1 = perun.getServicesManager().createService(sess, service1);
		// create destination
		destination1 = new Destination();
		destination1.setDestination("testDestination");
		destination1.setType(Destination.DESTINATIONHOSTTYPE);
		destination1.setPropagationType(Destination.PROPAGATIONTYPE_PARALLEL);
		// create execService
		execservice1 = new ExecService();
		execservice1.setDefaultDelay(1);
		execservice1.setScript("/bin/true");
		execservice1.setEnabled(true);
		execservice1.setExecServiceType(ExecService.ExecServiceType.GENERATE);
		execservice1.setService(service1);
		int id = generalServiceManager.insertExecService(sess, execservice1);
		// stash back the created id (this should be really done somewhere else)
		execservice1.setId(id);
		// create execService
		execservice2 = new ExecService();
		execservice2.setDefaultDelay(1);
		execservice2.setScript("/bin/true");
		execservice2.setEnabled(true);
		execservice2.setExecServiceType(ExecService.ExecServiceType.SEND);
		execservice2.setService(service1);
		id = generalServiceManager.insertExecService(sess, execservice2);
		// stash back the created id (this should be really done somewhere else)
		execservice2.setId(id);
		generalServiceManager.createDependency(execservice2, execservice1);
		for(int i = 0; i < 100; i++) {
			// now create some facility
			facility1 = new Facility(0, "testFacility" + i, "desc");
			facility1 = perun.getFacilitiesManager().createFacility(sess, facility1);
			// create a resource
			resource1 = new Resource(0, "testResource", "test resource", facility1.getId(), vo1.getId());
			resource1 = perun.getResourcesManager().createResource(sess, resource1, vo1, facility1);
			// assign the group to this resource
			perun.getResourcesManager().assignGroupToResource(sess, group1, resource1);
			// assign service to the resource
			perun.getResourcesManager().assignService(sess, resource1, service1);
			testFacilities.add(facility1.getId());
			// add destination
			perun.getServicesManager().addDestination(sess, service1, facility1, destination1);
		}
	}

	private void removeTestTasks() throws PerunException {
		this.getJdbcTemplate().update(cleanupQuery);
/*
		PerunPrincipal pp = new PerunPrincipal("perunTests", ExtSourcesManager.EXTSOURCE_NAME_INTERNAL, ExtSourcesManager.EXTSOURCE_INTERNAL);
		PerunSession sess = perun.getPerunSession(pp);

		ResourcesManager rm = perun.getResourcesManager();
		FacilitiesManager fm = perun.getFacilitiesManager();
		for(Integer id : testFacilities) {
			facility1 = fm.getFacilityById(sess, id);
			for(Resource resource1 : fm.getAssignedResources(sess, facility1)) {
				rm.removeService(sess, resource1, service1);
				rm.removeGroupFromResource(sess, group1, resource1);
				rm.deleteResource(sess, resource1);
			}
			perun.getServicesManager().removeAllDestinations(sess, service1, facility1);
			fm.deleteFacility(sess, facility1);
		}
		generalServiceManager.removeDependency(execservice2, execservice1);
		generalServiceManager.deleteExecService(execservice2);
		generalServiceManager.deleteExecService(execservice1);
		perun.getServicesManager().deleteService(sess, service1);
		perun.getMembersManager().deleteMember(sess, member1);
		perun.getGroupsManager().deleteGroup(sess, group1);
		perun.getUsersManager().deleteUser(sess, user1);
		perun.getVosManager().deleteVo(sess, vo1);
*/		
	}
}
